import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class SensorServiceBridge extends ChangeNotifier {
  static const MethodChannel _methodChannel =
      MethodChannel('com.lifesaver.cardiac/sensor_service');
  static const EventChannel _eventChannel =
      EventChannel('com.lifesaver.cardiac/sensor_stream');

  StreamSubscription<dynamic>? _sensorSubscription;

  bool _isMonitoring = false;
  bool _sensorAvailable = true;
  double _currentBpm = 0.0;
  int _sensorAccuracy = 0;
  String _escalationState = 'NORMAL';
  int _escalationSequence = 0;
  int _lastPromptedSequence = -1;
  int _alertSequence = 0;
  String _escalationReason = 'INITIALIZED';
  String? _activeCandidateId;
  String? _collapseReason;
  String? _lastError;

  bool get isMonitoring => _isMonitoring;
  bool get sensorAvailable => _sensorAvailable;
  double get currentBpm => _currentBpm;
  int get sensorAccuracy => _sensorAccuracy;
  String get escalationState => _escalationState;
  int get escalationSequence => _escalationSequence;
  String get escalationReason => _escalationReason;
  String? get activeCandidateId => _activeCandidateId;
  int get alertSequence => _alertSequence;
  String? get collapseReason => _collapseReason;
  String? get lastError => _lastError;

  int get currentStage {
    switch (_escalationState) {
      case 'RECHECK':
        return 2;
      case 'SAFETY_CHECK':
        return 3;
      case 'DIALER_READY':
        return 4;
      case 'NORMAL':
      case 'RESOLVED':
      default:
        return 1;
    }
  }

  SensorServiceBridge() {
    _initSensorStreamListener();
  }

  Future<void> startMonitoring() async {
    _lastError = null;
    try {
      await _methodChannel.invokeMethod<void>('startEmergencyService');
    } on PlatformException catch (error) {
      _isMonitoring = false;
      _lastError = error.message ?? error.code;
      debugPrint('Failed to start native sensor service: $error');
    } on MissingPluginException catch (error) {
      _isMonitoring = false;
      _lastError = 'Native sensor bridge is not available.';
      debugPrint('Native sensor bridge is not available: $error');
    }
    notifyListeners();
  }

  Future<void> stopMonitoring() async {
    _lastError = null;
    try {
      await _methodChannel.invokeMethod<void>('stopEmergencyService');
      _isMonitoring = false;
    } on PlatformException catch (error) {
      _lastError = error.message ?? error.code;
      debugPrint('Failed to stop native sensor service: $error');
    }
    notifyListeners();
  }

  Future<void> acknowledgeAlert() async {
    _lastError = null;
    try {
      await _methodChannel.invokeMethod<void>('confirmSafe');
    } on PlatformException catch (error) {
      _lastError = error.message ?? error.code;
      debugPrint('Failed to confirm the safety prompt: $error');
    }
    notifyListeners();
  }

  Future<void> openEmergencyDialer({required bool timedOut}) async {
    _lastError = null;
    try {
      await _methodChannel.invokeMethod<void>(
        'openEmergencyDialer',
        <String, String>{
          'source': timedOut ? 'PROMPT_TIMEOUT' : 'USER',
        },
      );
    } on PlatformException catch (error) {
      _lastError = error.message ?? error.code;
      debugPrint('Failed to request the emergency dialer: $error');
    }
    notifyListeners();
  }

  void clearError() {
    _lastError = null;
    notifyListeners();
  }

  void _initSensorStreamListener() {
    _sensorSubscription = _eventChannel.receiveBroadcastStream().listen(
      (dynamic event) {
        if (event is! Map) return;

        final Object? monitoring = event['monitoring'];
        if (monitoring is bool) {
          _isMonitoring = monitoring;
        }

        final Object? sensorAvailable = event['sensorAvailable'];
        if (sensorAvailable is bool) {
          _sensorAvailable = sensorAvailable;
        }

        final Object? heartRate = event['heartRate'];
        if (heartRate is num) {
          _currentBpm = heartRate.toDouble();
        }

        final Object? accuracy = event['accuracy'];
        if (accuracy is num) {
          _sensorAccuracy = accuracy.toInt();
        }

        final Object? state = event['escalationState'];
        if (state is String) {
          _escalationState = state;
        }

        final Object? sequence = event['escalationSequence'];
        if (sequence is num) {
          _escalationSequence = sequence.toInt();
        }

        final Object? reason = event['escalationReason'];
        if (reason is String) {
          _escalationReason = reason;
        }

        if (event.containsKey('activeCandidateId')) {
          _activeCandidateId = event['activeCandidateId']?.toString();
        } else if (_escalationState == 'NORMAL' ||
            _escalationState == 'RESOLVED') {
          _activeCandidateId = null;
        }

        if (event['safetyCheckRequired'] == true &&
            _escalationSequence > _lastPromptedSequence) {
          _lastPromptedSequence = _escalationSequence;
          _collapseReason =
              event['candidateReason']?.toString() ?? _escalationReason;
          _alertSequence += 1;
        } else if (_escalationState != 'SAFETY_CHECK') {
          _collapseReason = null;
        }

        if (event['transitionAccepted'] == false) {
          final String eventType = event['eventType']?.toString() ?? '';
          if (eventType.startsWith('DIALER_') ||
              eventType == 'USER_CONFIRMED_SAFE') {
            _lastError = event['transitionRejectionReason']?.toString() ??
                'The requested safety transition was rejected.';
          }
        }

        notifyListeners();
      },
      onError: (Object error) {
        _lastError = 'Sensor stream error: $error';
        debugPrint(_lastError);
        notifyListeners();
      },
    );
  }

  @override
  void dispose() {
    _sensorSubscription?.cancel();
    super.dispose();
  }
}
