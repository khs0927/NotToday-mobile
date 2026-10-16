import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:vibration/vibration.dart';

import '../services/sensor_service.dart';

class EmergencyPromptDialog extends StatefulWidget {
  const EmergencyPromptDialog({
    super.key,
    this.triggerReason,
  });

  final String? triggerReason;

  @override
  State<EmergencyPromptDialog> createState() =>
      _EmergencyPromptDialogState();
}

class _EmergencyPromptDialogState extends State<EmergencyPromptDialog> {
  int _countdown = 15;
  Timer? _timer;
  bool _isCompleting = false;

  @override
  void initState() {
    super.initState();
    unawaited(_startHapticPattern());
    _startCountdown();
  }

  Future<void> _startHapticPattern() async {
    try {
      if (await Vibration.hasVibrator()) {
        await Vibration.vibrate(
          pattern: <int>[500, 1000, 500, 1000],
          repeat: 0,
        );
      }
    } on Object {
      // Haptics are best-effort. The safety prompt must remain usable even if
      // the device or plugin cannot start a vibration pattern.
    }
  }

  void _startCountdown() {
    _timer = Timer.periodic(const Duration(seconds: 1), (Timer timer) {
      if (_countdown <= 1) {
        timer.cancel();
        unawaited(_openDialerAndClose());
        return;
      }

      if (mounted) {
        setState(() {
          _countdown -= 1;
        });
      }
    });
  }

  Future<void> _markFalseAlarmAndClose() async {
    if (_isCompleting) return;
    _isCompleting = true;
    _timer?.cancel();
    await Vibration.cancel();

    if (!mounted) return;
    context.read<SensorServiceBridge>().acknowledgeAlert();
    Navigator.of(context).pop();
  }

  Future<void> _openDialerAndClose() async {
    if (_isCompleting) return;
    _isCompleting = true;
    _timer?.cancel();
    await Vibration.cancel();

    if (!mounted) return;
    await context.read<SensorServiceBridge>().openEmergencyDialer();
    if (mounted) {
      Navigator.of(context).pop();
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    unawaited(Vibration.cancel());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: const Color(0xFF2A0000),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
      ),
      title: const Row(
        children: <Widget>[
          Icon(
            Icons.warning_amber_rounded,
            color: Colors.redAccent,
            size: 24,
          ),
          SizedBox(width: 8),
          Expanded(
            child: Text(
              '안전 상태를 확인하세요',
              style: TextStyle(
                color: Colors.redAccent,
                fontSize: 14,
              ),
            ),
          ),
        ],
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const Text(
            '강한 충격과 이후의 낮은 움직임이 감지되었습니다. 괜찮으신가요?',
            style: TextStyle(color: Colors.white, fontSize: 12),
            textAlign: TextAlign.center,
          ),
          if (widget.triggerReason != null) ...<Widget>[
            const SizedBox(height: 6),
            Text(
              widget.triggerReason!,
              style: const TextStyle(color: Colors.white54, fontSize: 9),
              textAlign: TextAlign.center,
            ),
          ],
          const SizedBox(height: 12),
          Text(
            '$_countdown초',
            style: const TextStyle(
              color: Colors.redAccent,
              fontSize: 24,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            '응답이 없으면 119 전화 화면을 엽니다. 통화는 자동 연결되지 않습니다.',
            style: TextStyle(color: Colors.grey, fontSize: 9),
            textAlign: TextAlign.center,
          ),
        ],
      ),
      actions: <Widget>[
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.redAccent,
            ),
            onPressed: _openDialerAndClose,
            child: const Text('119 전화 화면 열기'),
          ),
        ),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton(
            onPressed: _markFalseAlarmAndClose,
            child: const Text(
              '괜찮습니다 · 오탐 취소',
              style: TextStyle(color: Colors.white),
            ),
          ),
        ),
      ],
    );
  }
}
