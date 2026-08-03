import 'package:flutter/material.dart';

class DisclaimerBanner extends StatelessWidget {
  const DisclaimerBanner({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(6),
      decoration: BoxDecoration(
        color: Colors.grey[900],
        borderRadius: BorderRadius.circular(8),
      ),
      child: const Text(
        '안전 보조 프로토타입입니다. 질환을 진단하거나 응급 구조를 보장하지 않습니다. 실제 응급 상황에서는 직접 119에 연락하세요.',
        style: TextStyle(color: Colors.grey, fontSize: 8),
        textAlign: TextAlign.center,
      ),
    );
  }
}
