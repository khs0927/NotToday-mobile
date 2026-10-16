import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class SensorServiceBridge extends ChangeNotifier {
  static const _methodChannel =
      MethodChannel('com.lifesaver.cardiac/sensor_service');
  static const _eventChannel =
      EventChannel('com.lifesaver.cardiac/sensor_stream');

  StreamSubscription<dynamic>? _sensorSubscription;

  bool _isMonitoring = false;
  bool _sensorAvailable = true;
  double _currentBpm = 0.0;
  int _sensorAccuracy = 0;
  int _currentStage = 1;
  int _alertSequence = 0;
  String? _collapseReason;
  String? _lastError;

  bool get isMonitoring => _isMonitoring;
  bool get sensorAvailable => _sensorAvailable;
  double get currentBpm => _currentBpm;
  int get sensorAccuracy => _sensorAccuracy;
  int get currentStage => _currentStage;
  int get alertSequence => _alertSequence;
  String? get collapseReason => _collapseReason;
  String? get lastError => _lastError;

  SensorServiceBridge() {
    _initSensorStreamListener();
  }

  Future<void> startMonitoring() async {
    _lastError = null;
    try {
      await _methodChannel.invokeMethod<void>('startEmergencyService');
      _isMonitoring = true;
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
      _currentStage = 1;
    } on PlatformException catch (error) {
      _lastError = error.message ?? error.code;
      debugPrint('Failed to stop native sensor service: $error');
    }
    notifyListeners();
  }

  Future<void> openEmergencyDialer() async {
    _lastError = null;
    try {
      await _methodChannel.invokeMethod<void>('openEmergencyDialer');
      _currentStage = 4;
    } on PlatformException catch (error) {
      _lastError = error.message ?? error.code;
      debugPrint('Failed to open emergency dialer: $error');
    }
    notifyListeners();
  }

  void acknowledgeAlert() {
    _collapseReason = null;
    _currentStage = 1;
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

        final monitoring = event['monitoring'];
        if (monitoring is bool) {
          _isMonitoring = monitoring;
        }

        final sensorAvailable = event['sensorAvailable'];
        if (sensorAvailable is bool) {
          _sensorAvailable = sensorAvailable;
        }

        final heartRate = event['heartRate'];
        if (heartRate is num) {
          _currentBpm = heartRate.toDouble();
        }

        final accuracy = event['accuracy'];
        if (accuracy is num) {
          _sensorAccuracy = accuracy.toInt();
        }

        final stage = event['escalationStage'];
        if (stage is num) {
          _currentStage = stage.toInt();
        }

        if (event['collapseCandidate'] == true) {
          _collapseReason = event['collapseType']?.toString();
          _alertSequence += 1;
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
