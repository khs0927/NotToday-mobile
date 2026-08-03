import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:wear_plus/wear_plus.dart';

import '../services/sensor_service.dart';
import '../widgets/disclaimer_banner.dart';
import 'emergency_prompt_dialog.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({
    super.key,
    required this.shape,
  });

  final WearShape shape;

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  SensorServiceBridge? _sensorBridge;
  int _lastHandledAlertSequence = 0;
  bool _dialogOpen = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final SensorServiceBridge nextBridge = Provider.of<SensorServiceBridge>(
      context,
      listen: false,
    );

    if (identical(nextBridge, _sensorBridge)) return;

    _sensorBridge?.removeListener(_handleSensorUpdate);
    _sensorBridge = nextBridge;
    _sensorBridge?.addListener(_handleSensorUpdate);
  }

  void _handleSensorUpdate() {
    final SensorServiceBridge? bridge = _sensorBridge;
    if (!mounted || bridge == null || _dialogOpen) return;
    if (bridge.alertSequence <= _lastHandledAlertSequence) return;

    _lastHandledAlertSequence = bridge.alertSequence;
    _dialogOpen = true;

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;

      await showDialog<void>(
        context: context,
        barrierDismissible: false,
        builder: (BuildContext context) => EmergencyPromptDialog(
          triggerReason: bridge.collapseReason,
        ),
      );

      _dialogOpen = false;
      _handleSensorUpdate();
    });
  }

  @override
  void dispose() {
    _sensorBridge?.removeListener(_handleSensorUpdate);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final SensorServiceBridge sensorProvider =
        context.watch<SensorServiceBridge>();

    return AmbientMode(
      builder: (BuildContext context, WearMode mode, Widget? child) {
        final bool isAmbient = mode == WearMode.ambient;

        return Scaffold(
          body: Center(
            child: SingleChildScrollView(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: <Widget>[
                    if (!isAmbient) ...<Widget>[
                      const Icon(
                        Icons.favorite_rounded,
                        color: Colors.redAccent,
                        size: 28,
                      ),
                      const SizedBox(height: 4),
                    ],
                    Text(
                      sensorProvider.currentBpm > 0
                          ? '${sensorProvider.currentBpm.toStringAsFixed(0)} BPM'
                          : '-- BPM',
                      style: TextStyle(
                        fontSize: isAmbient ? 22 : 28,
                        fontWeight: FontWeight.bold,
                        color: isAmbient ? Colors.white70 : Colors.white,
                      ),
                    ),
                    Text(
                      _sensorStatusText(sensorProvider),
                      style: const TextStyle(
                        fontSize: 10,
                        color: Colors.grey,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                    _buildStatusBadge(sensorProvider.currentStage),
                    const SizedBox(height: 12),
                    if (!isAmbient) ...<Widget>[
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: sensorProvider.isMonitoring
                              ? Colors.grey[800]
                              : Colors.redAccent,
                          shape: const StadiumBorder(),
                        ),
                        onPressed: () {
                          if (sensorProvider.isMonitoring) {
                            sensorProvider.stopMonitoring();
                          } else {
                            sensorProvider.startMonitoring();
                          }
                        },
                        child: Text(
                          sensorProvider.isMonitoring
                              ? '모니터링 중지'
                              : '모니터링 시작',
                        ),
                      ),
                      const SizedBox(height: 6),
                      TextButton(
                        onPressed: () {
                          showDialog<void>(
                            context: context,
                            barrierDismissible: false,
                            builder: (BuildContext context) =>
                                const EmergencyPromptDialog(
                              triggerReason: '테스트 안전 확인',
                            ),
                          );
                        },
                        child: const Text(
                          '안전 확인 화면 테스트',
                          style: TextStyle(
                            color: Colors.orangeAccent,
                            fontSize: 10,
                          ),
                        ),
                      ),
                      if (sensorProvider.lastError != null) ...<Widget>[
                        const SizedBox(height: 4),
                        Text(
                          sensorProvider.lastError!,
                          style: const TextStyle(
                            color: Colors.redAccent,
                            fontSize: 9,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ],
                      const SizedBox(height: 8),
                      const DisclaimerBanner(),
                    ],
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  String _sensorStatusText(SensorServiceBridge sensorProvider) {
    if (!sensorProvider.sensorAvailable) {
      return '이 기기에서 심박 센서를 사용할 수 없습니다.';
    }
    if (!sensorProvider.isMonitoring) {
      return '모니터링이 중지되어 있습니다.';
    }
    if (sensorProvider.currentBpm <= 0) {
      return '유효한 심박 신호를 기다리는 중입니다.';
    }
    return '심박 센서 정확도: ${sensorProvider.sensorAccuracy}';
  }

  Widget _buildStatusBadge(int stage) {
    Color badgeColor = Colors.green;
    String statusText = 'Normal';

    if (stage == 2) {
      badgeColor = Colors.amber;
      statusText = 'Recheck';
    } else if (stage == 3) {
      badgeColor = Colors.orange;
      statusText = 'Safety Check';
    } else if (stage == 4) {
      badgeColor = Colors.red;
      statusText = 'Dialer Opened';
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: badgeColor.withValues(alpha: 0.2),
        border: Border.all(color: badgeColor),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        statusText,
        style: TextStyle(
          color: badgeColor,
          fontSize: 11,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}
