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
import com.lifesaver.cardiac.escalation.DialerRequestSource
import com.lifesaver.cardiac.escalation.EscalationCoordinator
import com.lifesaver.cardiac.escalation.MonotonicEpochClock
import com.lifesaver.cardiac.escalation.EscalationSnapshot
import com.lifesaver.cardiac.escalation.EscalationStage
import com.lifesaver.cardiac.escalation.TransitionResult
import io.flutter.plugin.common.EventChannel

class NativeSensorForegroundService : Service() {

    private lateinit var healthSensorManager: WearOSHealthSensorManager
    private lateinit var fallDetector: IMUFallCollapseDetector
    private lateinit var emergencyController: EmergencyDispatchController
    private lateinit var escalationCoordinator: EscalationCoordinator
    private lateinit var epochClock: MonotonicEpochClock
    private val mainHandler = Handler(Looper.getMainLooper())
    private var monitoringActive = false

    companion object {
        private const val TAG = "NativeSensorService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "cardiac_health_service_channel"

        const val ACTION_START_MONITORING =
            "com.lifesaver.cardiac.action.START_MONITORING"
        const val ACTION_CONFIRM_SAFE =
            "com.lifesaver.cardiac.action.CONFIRM_SAFE"
        const val ACTION_REQUEST_DIALER =
            "com.lifesaver.cardiac.action.REQUEST_DIALER"
        const val EXTRA_DIALER_SOURCE = "dialer_source"
        const val EXTRA_CANDIDATE_ID = "candidate_id"
        const val DIALER_SOURCE_USER = "USER"
        const val DIALER_SOURCE_PROMPT_TIMEOUT = "PROMPT_TIMEOUT"

        @Volatile
        var eventSink: EventChannel.EventSink? = null
    }

    override fun onCreate() {
        super.onCreate()

        epochClock = MonotonicEpochClock()
        emergencyController = EmergencyDispatchController(this)
        escalationCoordinator = EscalationCoordinator()

        healthSensorManager = WearOSHealthSensorManager(this) { telemetry ->
            publishEvent(withSnapshot(telemetry))
        }

        fallDetector = IMUFallCollapseDetector(
            context = this,
            isWearerSignalAvailable = {
                healthSensorManager.hasRecentValidHeartRate()
            },
            onCandidateEvent = ::handleFallCandidateEvent,
            nowEpochMs = epochClock::now,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()

        when (intent?.action ?: ACTION_START_MONITORING) {
            ACTION_CONFIRM_SAFE -> {
                val candidateId = intent?.getStringExtra(EXTRA_CANDIDATE_ID).orEmpty()
                publishTransition(
                    escalationCoordinator.confirmSafe(
                        candidateId = candidateId,
                        occurredAtEpochMs = epochClock.now(),
                    ),
                    eventType = "USER_CONFIRMED_SAFE",
                )
                stopIfActionStartedWithoutMonitoring(startId)
            }

            ACTION_REQUEST_DIALER -> {
                val source = when (intent?.getStringExtra(EXTRA_DIALER_SOURCE)) {
                    DIALER_SOURCE_PROMPT_TIMEOUT -> DialerRequestSource.PROMPT_TIMEOUT
                    else -> DialerRequestSource.USER
                }
                handleDialerRequest(
                    source = source,
                    candidateId = intent?.getStringExtra(EXTRA_CANDIDATE_ID).orEmpty(),
                )
                stopIfActionStartedWithoutMonitoring(startId)
            }

            else -> startMonitoring()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        if (monitoringActive) {
            healthSensorManager.stopMonitoring()
            fallDetector.stop()
        }
        monitoringActive = false
        publishEvent(
            withSnapshot(
                mapOf(
                    "monitoring" to false,
                    "eventType" to "MONITORING_STOPPED",
                    "timestamp" to epochClock.now(),
                ),
            ),
        )
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        if (!monitoringActive) {
            healthSensorManager.startMonitoring()
            fallDetector.start()
            monitoringActive = true
        }

        publishEvent(
            withSnapshot(
                mapOf(
                    "monitoring" to true,
                    "eventType" to "MONITORING_STARTED",
                    "timestamp" to epochClock.now(),
                ),
            ),
        )
    }

    private fun handleFallCandidateEvent(event: FallCandidateEvent): Boolean {
        val transition = escalationCoordinator.handleFallCandidate(event)
        val extras = mutableMapOf<String, Any>(
            "candidateLifecycle" to when (event) {
                is FallCandidateEvent.Started -> "STARTED"
                is FallCandidateEvent.Verified -> "VERIFIED"
                is FallCandidateEvent.Rejected -> "REJECTED"
            },
            "candidateId" to event.candidateId,
            "impactGForce" to event.impactGForce,
            "timestamp" to event.occurredAtEpochMs,
        )

        when (event) {
            is FallCandidateEvent.Verified -> {
                extras["candidateReason"] = event.reason
                extras["sampleCount"] = event.sampleCount
                extras["postImpactStdDevG"] = event.postImpactStdDevG
            }

            is FallCandidateEvent.Rejected -> {
                extras["candidateReason"] = event.reason
                extras["sampleCount"] = event.sampleCount
                event.postImpactStdDevG?.let {
                    extras["postImpactStdDevG"] = it
                }
            }

            is FallCandidateEvent.Started -> Unit
        }

        if (
            transition is TransitionResult.Accepted &&
            transition.current.stage == EscalationStage.SAFETY_CHECK
        ) {
            extras["safetyCheckRequired"] = true
        }

        publishTransition(
            transition = transition,
            eventType = "FALL_CANDIDATE_${extras.getValue("candidateLifecycle")}",
            extras = extras,
        )

        return transition is TransitionResult.Accepted
    }

    private fun handleDialerRequest(
        source: DialerRequestSource,
        candidateId: String,
    ) {
        val requestTransition = escalationCoordinator.requestDialer(
            candidateId = candidateId,
            source = source,
            occurredAtEpochMs = epochClock.now(),
        )
        publishTransition(
            transition = requestTransition,
            eventType = "DIALER_REQUESTED_${source.name}",
        )

        if (
            requestTransition !is TransitionResult.Accepted ||
            requestTransition.current.stage != EscalationStage.DIALER_READY
        ) {
            return
        }

        val openedCandidateId = requestTransition.current.activeCandidateId ?: return
        val opened = emergencyController.openSystemEmergencyDialer()
        val resultTransition = if (opened) {
            escalationCoordinator.recordDialerOpened(
                candidateId = openedCandidateId,
                occurredAtEpochMs = epochClock.now(),
            )
        } else {
            escalationCoordinator.recordDialerFailed(
                candidateId = openedCandidateId,
                reason = "DIALER_UNAVAILABLE",
                occurredAtEpochMs = epochClock.now(),
            )
        }

        publishTransition(
            transition = resultTransition,
            eventType = if (opened) "DIALER_OPENED" else "DIALER_FAILED",
            extras = mapOf("dialerOpened" to opened),
        )
    }

    private fun publishTransition(
        transition: TransitionResult,
        eventType: String,
        extras: Map<String, Any> = emptyMap(),
    ) {
        val payload = extras.toMutableMap()
        payload["eventType"] = eventType
        payload["monitoring"] = monitoringActive
        payload["timestamp"] = epochClock.now()

        when (transition) {
            is TransitionResult.Accepted -> {
                payload["transitionAccepted"] = true
                payload.putAll(snapshotPayload(transition.current))
            }

            is TransitionResult.Rejected -> {
                payload["transitionAccepted"] = false
                payload["transitionRejectionReason"] = transition.reason
                payload.putAll(snapshotPayload(transition.current))
            }
        }

        publishEvent(payload)
    }

    private fun withSnapshot(event: Map<String, Any>): Map<String, Any> =
        event + snapshotPayload(escalationCoordinator.currentSnapshot())

    private fun snapshotPayload(snapshot: EscalationSnapshot): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "escalationState" to snapshot.stage.name,
            "escalationSequence" to snapshot.sequence,
            "escalationReason" to snapshot.reason,
            "stageEnteredAtEpochMs" to snapshot.stageEnteredAtEpochMs,
            "escalationUpdatedAtEpochMs" to snapshot.updatedAtEpochMs,
        )
        snapshot.activeCandidateId?.let {
            payload["activeCandidateId"] = it
        }
        snapshot.cooldownUntilEpochMs?.let {
            payload["cooldownUntilEpochMs"] = it
        }
        return payload
    }

    private fun publishEvent(event: Map<String, Any>) {
        val publish: () -> Unit = {
            try {
                eventSink?.success(event)
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Unable to publish telemetry to Flutter.", exception)
            }
            Unit
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            publish()
        } else {
            mainHandler.post { publish() }
        }
    }

    private fun stopIfActionStartedWithoutMonitoring(startId: Int) {
        if (!monitoringActive) {
            stopSelf(startId)
        }
    }

    private fun ensureForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
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
