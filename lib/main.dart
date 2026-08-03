import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:wear_plus/wear_plus.dart';

import 'screens/dashboard_screen.dart';
import 'services/sensor_service.dart';

void main() {
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider<SensorServiceBridge>(
          create: (_) => SensorServiceBridge(),
        ),
      ],
      child: const NotTodayApp(),
    ),
  );
}

class NotTodayApp extends StatelessWidget {
  const NotTodayApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'NotToday-Mobile',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark().copyWith(
        primaryColor: Colors.redAccent,
        scaffoldBackgroundColor: Colors.black,
        cardColor: const Color(0xFF1E1E1E),
      ),
      home: WatchShape(
        builder: (
          BuildContext context,
          WearShape shape,
          Widget? child,
        ) {
          return DashboardScreen(shape: shape);
        },
      ),
    );
  }
}
