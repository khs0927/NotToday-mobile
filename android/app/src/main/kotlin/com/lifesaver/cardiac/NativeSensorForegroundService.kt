package com.lifesaver.cardiac

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import io.flutter.plugin.common.EventChannel

class NativeSensorForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var healthSensorManager: WearOSHealthSensorManager
    private lateinit var fallDetector: IMUFallCollapseDetector
    private lateinit var emergencyController: EmergencyDispatchController

    companion object {
        private const val TAG = "NativeSensorService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "cardiac_health_service_channel"
        var eventSink: EventChannel.EventSink? = null
    }

    override fun onCreate() {
        super.onCreate()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NotToday::CardiacSensorWakeLock"
        ).apply {
            acquire(24 * 60 * 60 * 1000L)
        }

        emergencyController = EmergencyDispatchController(this)
        
        fallDetector = IMUFallCollapseDetector(this) { collapseType, gForce ->
            emergencyController.executeStage4EmergencyDispatch(
                latitude = "35.1796",
                longitude = "129.0756",
                triggerReason = "Collapse Detected: $collapseType ($gForce g)"
            )
        }

        healthSensorManager = WearOSHealthSensorManager(this) { telemetryMap ->
            eventSink?.success(telemetryMap)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        healthSensorManager.startMonitoring()
        fallDetector.start()

        return START_STICKY
    }

    override fun onDestroy() {
        healthSensorManager.stopMonitoring()
        fallDetector.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NotToday Cardiac Health Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotToday Cardiac Guardian Active")
            .setContentText("Continuous ECG, PPG & Collapse Monitoring in Progress")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}