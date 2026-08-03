import 'dart:async';
import 'package:flutter/material.dart';
import 'package:vibration/vibration.dart';

class EmergencyPromptDialog extends StatefulWidget {
  const EmergencyPromptDialog({Key? key}) : super(key: key);

  @override
  State<EmergencyPromptDialog> createState() => _EmergencyPromptDialogState();
}

class _EmergencyPromptDialogState extends State<EmergencyPromptDialog> {
  int _countdown = 15;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _startHapticPattern();
    _startCountdown();
  }

  void _startHapticPattern() async {
    if (await Vibration.hasVibrator() ?? false) {
      Vibration.vibrate(pattern: [500, 1000, 500, 1000], repeat: 0);
    }
  }

  void _startCountdown() {
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_countdown == 1) {
        _timer?.cancel();
        Vibration.cancel();
        Navigator.of(context).pop();
      } else {
        setState(() {
          _countdown--;
        });
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    Vibration.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: const Color(0xFF2A0000),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      title: Row(
        children: const [
          Icon(Icons.warning_amber_rounded, color: Colors.redAccent, size: 24),
          SizedBox(width: 8),
          Text('High Risk Alert', style: TextStyle(color: Colors.redAccent, fontSize: 14)),
        ],
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text(
            'Are you feeling chest pressure, severe pain, or shortness of breath?',
            style: TextStyle(color: Colors.white, fontSize: 12),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 12),
          Text(
            '$_countdown s',
            style: const TextStyle(color: Colors.redAccent, fontSize: 24, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 4),
          const Text('119 Call in progress...', style: TextStyle(color: Colors.grey, fontSize: 10)),
        ],
      ),
      actions: [
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.grey[800]),
            onPressed: () {
              _timer?.cancel();
              Vibration.cancel();
              Navigator.of(context).pop();
            },
            child: const Text('CANCEL (False Alarm)', style: TextStyle(color: Colors.white)),
          ),
        ),
      ],
    );
  }
}