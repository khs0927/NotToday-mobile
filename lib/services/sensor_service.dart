import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class SensorServiceBridge extends ChangeNotifier {
  static const _methodChannel = MethodChannel('com.lifesaver.cardiac/sensor_service');
  static const _eventChannel = EventChannel('com.lifesaver.cardiac/sensor_stream');

  bool _isMonitoring = false;
  double _currentBpm = 0.0;
  double _currentHrv = 0.0;
  int _currentStage = 1;

  bool get isMonitoring => _isMonitoring;
  double get currentBpm => _currentBpm;
  double get currentHrv => _currentHrv;
  int get currentStage => _currentStage;

  SensorServiceBridge() {
    _initSensorStreamListener();
  }

  Future<void> startMonitoring() async {
    try {
      await _methodChannel.invokeMethod('startEmergencyService');
      _isMonitoring = true;
      notifyListeners();
    } catch (e) {
      debugPrint('Failed to start native sensor service: $e');
    }
  }

  Future<void> stopMonitoring() async {
    try {
      await _methodChannel.invokeMethod('stopEmergencyService');
      _isMonitoring = false;
      notifyListeners();
    } catch (e) {
      debugPrint('Failed to stop native sensor service: $e');
    }
  }

  void _initSensorStreamListener() {
    _eventChannel.receiveBroadcastStream().listen((dynamic event) {
      if (event is Map) {
        if (event.containsKey('heartRate')) {
          _currentBpm = (event['heartRate'] as num).toDouble();
        }
        if (event.containsKey('hrvSdnn')) {
          _currentHrv = (event['hrvSdnn'] as num).toDouble();
        }
        if (event.containsKey('escalationStage')) {
          _currentStage = (event['escalationStage'] as num).toInt();
        }
        notifyListeners();
      }
    }, onError: (dynamic error) {
      debugPrint('Sensor Stream Error: $error');
    });
  }
}