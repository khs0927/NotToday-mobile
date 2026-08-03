package com.lifesaver.cardiac

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import io.flutter.plugin.common.EventChannel

class NativeSensorForegroundService : Service() {

    private lateinit var healthSensorManager: WearOSHealthSensorManager
    private lateinit var fallDetector: IMUFallCollapseDetector
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "NativeSensorService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "cardiac_health_service_channel"

        @Volatile
        var eventSink: EventChannel.EventSink? = null
    }

    override fun onCreate() {
        super.onCreate()

        healthSensorManager = WearOSHealthSensorManager(this) { telemetry ->
            publishEvent(telemetry)
        }

        fallDetector = IMUFallCollapseDetector(
            context = this,
            isWearerSignalAvailable = {
                healthSensorManager.hasRecentValidHeartRate()
            },
        ) { collapseType, gForce ->
            Log.w(TAG, "Reviewed collapse candidate emitted to Flutter UI.")
            publishEvent(
                mapOf(
                    "collapseCandidate" to true,
                    "collapseType" to collapseType,
                    "impactGForce" to gForce,
                    "escalationStage" to 3,
                    "timestamp" to System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()

        // The health foreground-service type was introduced in Android 14.
        // Older Wear OS versions must use the two-argument overload.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        healthSensorManager.startMonitoring()
        fallDetector.start()
        publishEvent(
            mapOf(
                "monitoring" to true,
                "escalationStage" to 1,
                "timestamp" to System.currentTimeMillis(),
            ),
        )

        return START_STICKY
    }

    override fun onDestroy() {
        healthSensorManager.stopMonitoring()
        fallDetector.stop()
        publishEvent(
            mapOf(
                "monitoring" to false,
                "timestamp" to System.currentTimeMillis(),
            ),
        )
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publishEvent(event: Map<String, Any>) {
        mainHandler.post {
            try {
                eventSink?.success(event)
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Unable to publish telemetry to Flutter.", exception)
            }
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NotToday safety monitoring",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotToday monitoring active")
            .setContentText("Heart-rate and fall-candidate monitoring is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .build()
    }
}
