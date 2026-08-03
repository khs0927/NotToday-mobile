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

    private enum class PermissionStage {
        PRIMARY,
        BACKGROUND,
    }

    companion object {
        private const val METHOD_CHANNEL = "com.lifesaver.cardiac/sensor_service"
        private const val EVENT_CHANNEL = "com.lifesaver.cardiac/sensor_stream"
        private const val SENSOR_PERMISSION_REQUEST = 4102
        private const val READ_HEART_RATE_PERMISSION =
            "android.permission.health.READ_HEART_RATE"
        private const val READ_HEALTH_DATA_IN_BACKGROUND_PERMISSION =
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }

    private var pendingStartResult: MethodChannel.Result? = null
    private var pendingPermissionStage: PermissionStage? = null

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
        if (pendingStartResult != null) {
            result.error(
                "PERMISSION_REQUEST_ACTIVE",
                "A health permission request is already active.",
                null,
            )
            return
        }

        pendingStartResult = result
        requestPrimaryPermissionsOrContinue()
    }

    private fun requestPrimaryPermissionsOrContinue() {
        val missing = requiredPrimaryPermissions().filterNot(::isPermissionGranted)
        if (missing.isEmpty()) {
            requestBackgroundPermissionOrStart()
            return
        }

        pendingPermissionStage = PermissionStage.PRIMARY
        ActivityCompat.requestPermissions(
            this,
            missing.toTypedArray(),
            SENSOR_PERMISSION_REQUEST,
        )
    }

    private fun requestBackgroundPermissionOrStart() {
        val permission = requiredBackgroundPermission()
        if (permission == null || isPermissionGranted(permission)) {
            completePermissionFlowAndStart()
            return
        }

        pendingPermissionStage = PermissionStage.BACKGROUND
        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            SENSOR_PERMISSION_REQUEST,
        )
    }

    private fun requiredPrimaryPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 36) {
            add(READ_HEART_RATE_PERMISSION)
        } else {
            add(Manifest.permission.BODY_SENSORS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requiredBackgroundPermission(): String? = when {
        Build.VERSION.SDK_INT >= 36 -> READ_HEALTH_DATA_IN_BACKGROUND_PERMISSION
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            Manifest.permission.BODY_SENSORS_BACKGROUND
        else -> null
    }

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun completePermissionFlowAndStart() {
        val result = pendingStartResult ?: return
        pendingStartResult = null
        pendingPermissionStage = null
        startMonitoringService()
        result.success(null)
    }

    private fun failPermissionFlow(stage: PermissionStage) {
        val result = pendingStartResult ?: return
        pendingStartResult = null
        pendingPermissionStage = null
        val code = when (stage) {
            PermissionStage.PRIMARY -> "HEART_RATE_PERMISSION_DENIED"
            PermissionStage.BACKGROUND -> "BACKGROUND_HEALTH_PERMISSION_DENIED"
        }
        val message = when (stage) {
            PermissionStage.PRIMARY ->
                "Heart-rate and notification permissions are required for monitoring."
            PermissionStage.BACKGROUND ->
                "All-day monitoring requires background health-data permission."
        }
        result.error(code, message, null)
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

        val stage = pendingPermissionStage ?: return
        val allGranted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (!allGranted) {
            failPermissionFlow(stage)
            return
        }

        when (stage) {
            PermissionStage.PRIMARY -> requestBackgroundPermissionOrStart()
            PermissionStage.BACKGROUND -> completePermissionFlowAndStart()
        }
    }

    override fun onDestroy() {
        NativeSensorForegroundService.eventSink = null
        pendingStartResult = null
        pendingPermissionStage = null
        super.onDestroy()
    }
}
