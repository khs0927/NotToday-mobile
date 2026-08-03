package com.lifesaver.cardiac.health

import android.os.SystemClock
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import java.time.Instant

/** Receives batched all-day heart-rate updates directly from Health Services. */
class HealthServicesHeartRateListenerService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val receivedAtEpochMs = System.currentTimeMillis()
        val bootInstant = Instant.ofEpochMilli(
            receivedAtEpochMs - SystemClock.elapsedRealtime(),
        )

        dataPoints.getData(DataType.HEART_RATE_BPM).forEach { point ->
            HeartRateTelemetryHub.publishReading(
                bpm = point.value,
                measuredAtEpochMs = point.getTimeInstant(bootInstant).toEpochMilli(),
                receivedAtEpochMs = receivedAtEpochMs,
                accuracyLabel = point.accuracy?.toString(),
            )
        }
    }

    override fun onPermissionLost() {
        HeartRateTelemetryHub.clear("HEALTH_SERVICES_PERMISSION_LOST")
    }
}
