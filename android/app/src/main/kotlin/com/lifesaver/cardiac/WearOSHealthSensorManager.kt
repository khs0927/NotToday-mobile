package com.lifesaver.cardiac

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import com.lifesaver.cardiac.health.HealthServicesHeartRateListenerService
import com.lifesaver.cardiac.health.HeartRateTelemetryHub
import java.util.concurrent.Executor

/**
 * Registers an all-day, power-aware heart-rate request with Wear OS Health
 * Services. Raw SensorManager heart-rate sampling is intentionally not used.
 */
class WearOSHealthSensorManager(
    context: Context,
    private val onTelemetryListener: (Map<String, Any>) -> Unit,
) {
    private val applicationContext = context.applicationContext
    private val passiveClient: PassiveMonitoringClient =
        HealthServices.getClient(applicationContext).passiveMonitoringClient
    private val callbackExecutor: Executor =
        ContextCompat.getMainExecutor(applicationContext)
    private val hubListener: (Map<String, Any>) -> Unit = onTelemetryListener

    @Volatile
    private var monitoringRequested = false

    @Volatile
    private var registrationGeneration = 0L

    fun startMonitoring() {
        if (monitoringRequested) return
        monitoringRequested = true
        val generation = ++registrationGeneration
        HeartRateTelemetryHub.addListener(hubListener)
        HeartRateTelemetryHub.publishServiceState(
            state = "CHECKING_CAPABILITIES",
            sensorAvailable = null,
        )

        val capabilitiesFuture = passiveClient.getCapabilitiesAsync()
        capabilitiesFuture.addListener(
            {
                if (!isCurrentRequest(generation)) return@addListener
                runCatching { capabilitiesFuture.get() }
                    .onSuccess { capabilities ->
                        if (
                            DataType.HEART_RATE_BPM !in
                            capabilities.supportedDataTypesPassiveMonitoring
                        ) {
                            monitoringRequested = false
                            HeartRateTelemetryHub.publishServiceState(
                                state = "UNSUPPORTED",
                                sensorAvailable = false,
                                reason = "PASSIVE_HEART_RATE_UNSUPPORTED",
                            )
                            return@onSuccess
                        }
                        registerPassiveHeartRate(generation)
                    }
                    .onFailure { error ->
                        monitoringRequested = false
                        publishFailure("CAPABILITY_CHECK_FAILED", error)
                    }
            },
            callbackExecutor,
        )
    }

    fun stopMonitoring() {
        if (!monitoringRequested) {
            HeartRateTelemetryHub.removeListener(hubListener)
            return
        }
        monitoringRequested = false
        ++registrationGeneration
        HeartRateTelemetryHub.removeListener(hubListener)

        val clearFuture = passiveClient.clearPassiveListenerServiceAsync()
        clearFuture.addListener(
            {
                runCatching { clearFuture.get() }
                    .onSuccess {
                        HeartRateTelemetryHub.publishServiceState(
                            state = "STOPPED",
                            sensorAvailable = true,
                        )
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Unable to clear passive heart-rate registration.", error)
                    }
            },
            callbackExecutor,
        )
    }

    fun hasRecentValidHeartRate(
        maxAgeMs: Long = 30_000L,
    ): Boolean = HeartRateTelemetryHub.hasRecentValidHeartRate(maxAgeMs = maxAgeMs)

    private fun registerPassiveHeartRate(generation: Long) {
        val config = PassiveListenerConfig.builder()
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            .build()
        val registrationFuture = passiveClient.setPassiveListenerServiceAsync(
            HealthServicesHeartRateListenerService::class.java,
            config,
        )
        registrationFuture.addListener(
            {
                if (!isCurrentRequest(generation)) return@addListener
                runCatching { registrationFuture.get() }
                    .onSuccess {
                        HeartRateTelemetryHub.publishServiceState(
                            state = "REGISTERED",
                            sensorAvailable = true,
                        )
                    }
                    .onFailure { error ->
                        monitoringRequested = false
                        publishFailure("REGISTRATION_FAILED", error)
                    }
            },
            callbackExecutor,
        )
    }

    private fun isCurrentRequest(generation: Long): Boolean =
        monitoringRequested && registrationGeneration == generation

    private fun publishFailure(reason: String, error: Throwable) {
        Log.e(TAG, reason, error)
        HeartRateTelemetryHub.publishServiceState(
            state = "ERROR",
            sensorAvailable = false,
            reason = "$reason:${error.javaClass.simpleName}",
        )
    }

    companion object {
        private const val TAG = "WearOSHealthServices"
    }
}
