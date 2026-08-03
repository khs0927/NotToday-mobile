package com.lifesaver.cardiac

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log

class EmergencyDispatchController(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyDispatchCtrl"
        private const val EMERGENCY_NUMBER = "119"
    }

    fun executeStage4EmergencyDispatch(latitude: String, longitude: String, triggerReason: String) {
        Log.e(TAG, "EXECUTING STAGE 4 EMERGENCY DISPATCH PROTOCOL: $triggerReason")
        sendEmergencySms(latitude, longitude, triggerReason)
        openSystemEmergencyDialer()
    }

    private fun sendEmergencySms(lat: String, lon: String, reason: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val message = "[NotToday 초응급 알림] 심정지/쓰러짐 감지!\n사유: $reason\n위치: https://maps.google.com/?q=$lat,$lon"
            smsManager.sendTextMessage(EMERGENCY_NUMBER, null, message, null, null)
            Log.i(TAG, "Emergency SMS dispatched successfully to $EMERGENCY_NUMBER")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch Emergency SMS: ${e.message}", e)
        }
    }

    private fun openSystemEmergencyDialer() {
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$EMERGENCY_NUMBER")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            Log.i(TAG, "System Emergency Dialer opened with $EMERGENCY_NUMBER pre-filled.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open System Emergency Dialer: ${e.message}", e)
        }
    }
}