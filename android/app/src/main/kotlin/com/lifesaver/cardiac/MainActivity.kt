package com.lifesaver.cardiac

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        private const val METHOD_CHANNEL = "com.lifesaver.cardiac/sensor_service"
        private const val EVENT_CHANNEL = "com.lifesaver.cardiac/sensor_stream"
        private const val SENSOR_PERMISSION_REQUEST = 4102
        private const val READ_HEART_RATE_PERMISSION = "android.permission.health.READ_HEART_RATE"
    }

    private var pendingStartResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startEmergencyService" -> startMonitoringWithPermissions(result)
                    "stopEmergencyService" -> {
                        stopService(Intent(this, NativeSensorForegroundService::class.java))
                        result.success(null)
                    }
                    "confirmSafe" -> {
                        dispatchServiceAction(
                            action = NativeSensorForegroundService.ACTION_CONFIRM_SAFE,
                            extras = mapOf(
                                NativeSensorForegroundService.EXTRA_CANDIDATE_ID to
                                    call.argument<String>("candidateId").orEmpty(),
                            ),
                        )
                        result.success(null)
                    }
                    "openEmergencyDialer" -> {
                        val source = when (call.argument<String>("source")) {
                            NativeSensorForegroundService.DIALER_SOURCE_PROMPT_TIMEOUT ->
                                NativeSensorForegroundService.DIALER_SOURCE_PROMPT_TIMEOUT
                            else -> NativeSensorForegroundService.DIALER_SOURCE_USER
                        }
                        dispatchServiceAction(
                            action = NativeSensorForegroundService.ACTION_REQUEST_DIALER,
                            extras = mapOf(
                                NativeSensorForegroundService.EXTRA_DIALER_SOURCE to source,
                                NativeSensorForegroundService.EXTRA_CANDIDATE_ID to
                                    call.argument<String>("candidateId").orEmpty(),
                            ),
                        )
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    NativeSensorForegroundService.eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    NativeSensorForegroundService.eventSink = null
                }
            })
    }

    private fun startMonitoringWithPermissions(result: MethodChannel.Result) {
        val missingPermissions = requiredRuntimePermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startMonitoringService()
            result.success(null)
            return
        }

        if (pendingStartResult != null) {
            result.error("PERMISSION_REQUEST_ACTIVE", "A sensor permission request is already active.", null)
            return
        }

        pendingStartResult = result
        ActivityCompat.requestPermissions(
            this,
            missingPermissions.toTypedArray(),
            SENSOR_PERMISSION_REQUEST,
        )
    }

    private fun requiredRuntimePermissions(): List<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 36) {
            permissions += READ_HEART_RATE_PERMISSION
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            permissions += Manifest.permission.BODY_SENSORS
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        return permissions
    }

    private fun startMonitoringService() {
        dispatchServiceAction(NativeSensorForegroundService.ACTION_START_MONITORING)
    }

    private fun dispatchServiceAction(
        action: String,
        extras: Map<String, String> = emptyMap(),
    ) {
        val serviceIntent = Intent(this, NativeSensorForegroundService::class.java).apply {
            this.action = action
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != SENSOR_PERMISSION_REQUEST) return

        val result = pendingStartResult ?: return
        pendingStartResult = null

        val allGranted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            startMonitoringService()
            result.success(null)
        } else {
            result.error(
                "SENSOR_PERMISSION_DENIED",
                "Heart-rate and notification permissions are required for monitoring.",
                null,
            )
        }
    }

    override fun onDestroy() {
        NativeSensorForegroundService.eventSink = null
        pendingStartResult = null
        super.onDestroy()
    }
}
