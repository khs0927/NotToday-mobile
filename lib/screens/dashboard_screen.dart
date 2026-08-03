import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:wear/wear.dart';
import '../services/sensor_service.dart';
import '../widgets/disclaimer_banner.dart';
import 'emergency_prompt_dialog.dart';

class DashboardScreen extends StatelessWidget {
  final WearShape shape;

  const DashboardScreen({Key? key, required this.shape}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final sensorProvider = Provider.of<SensorServiceBridge>(context);

    return AmbientMode(
      builder: (BuildContext context, WearMode mode, Widget? child) {
        final isAmbient = mode == WearMode.ambient;

        return Scaffold(
          body: Center(
            child: SingleChildScrollView(
              child: Padding(
                padding: const EdgeInsets.all(12.0),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    if (!isAmbient) ...[
                      const Icon(Icons.favorite_rounded, color: Colors.redAccent, size: 28),
                      const SizedBox(height: 4),
                    ],
                    Text(
                      '${sensorProvider.currentBpm.toStringAsFixed(0)} BPM',
                      style: TextStyle(
                        fontSize: isAmbient ? 22 : 28,
                        fontWeight: FontWeight.bold,
                        color: isAmbient ? Colors.white70 : Colors.white,
                      ),
                    ),
                    Text(
                      'SDNN: ${sensorProvider.currentHrv.toStringAsFixed(1)} ms',
                      style: const TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                    const SizedBox(height: 8),
                    _buildStatusBadge(sensorProvider.currentStage),
                    const SizedBox(height: 12),
                    if (!isAmbient) ...[
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: sensorProvider.isMonitoring ? Colors.grey[800] : Colors.redAccent,
                          shape: const StadiumBorder(),
                        ),
                        onPressed: () {
                          if (sensorProvider.isMonitoring) {
                            sensorProvider.stopMonitoring();
                          } else {
                            sensorProvider.startMonitoring();
                          }
                        },
                        child: Text(sensorProvider.isMonitoring ? 'Stop Watch' : 'Start Monitor'),
                      ),
                      const SizedBox(height: 6),
                      TextButton(
                        onPressed: () {
                          showDialog(
                            context: context,
                            builder: (context) => const EmergencyPromptDialog(),
                          );
                        },
                        child: const Text('Test Emergency Dialog', style: TextStyle(color: Colors.orangeAccent, fontSize: 10)),
                      ),
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

  Widget _buildStatusBadge(int stage) {
    Color badgeColor = Colors.green;
    String statusText = 'Normal';

    if (stage == 2) {
      badgeColor = Colors.amber;
      statusText = 'Caution';
    } else if (stage == 3) {
      badgeColor = Colors.orange;
      statusText = 'High Risk';
    } else if (stage == 4) {
      badgeColor = Colors.red;
      statusText = 'Emergency';
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: badgeColor.withOpacity(0.2),
        border: Border.all(color: badgeColor),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        statusText,
        style: TextStyle(color: badgeColor, fontSize: 11, fontWeight: FontWeight.bold),
      ),
    );
  }
}