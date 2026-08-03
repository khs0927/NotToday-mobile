import 'package:flutter/material.dart';

class DisclaimerBanner extends StatelessWidget {
  const DisclaimerBanner({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(6),
      decoration: BoxDecoration(
        color: Colors.grey[900],
        borderRadius: BorderRadius.circular(8),
      ),
      child: const Text(
        'Notice: Personal Safety & Risk Awareness Tool. Not a medical diagnostic device.',
        style: TextStyle(color: Colors.grey, fontSize: 8),
        textAlign: TextAlign.center,
      ),
    );
  }
}