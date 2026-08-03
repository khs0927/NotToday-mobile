package com.lifesaver.cardiac

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class EmergencyDispatchController(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyDispatchCtrl"
        private const val EMERGENCY_NUMBER = "119"
    }

    /**
     * Opens the system dialer with 119 pre-filled.
     *
     * This intentionally does not place a call or send an SMS automatically.
     * The user remains in control of the final emergency call action while the
     * product is still an unvalidated safety-assistance prototype.
     */
    fun openSystemEmergencyDialer(): Boolean {
        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$EMERGENCY_NUMBER")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            Log.i(TAG, "System emergency dialer opened with $EMERGENCY_NUMBER pre-filled.")
            true
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to open system emergency dialer.", exception)
            false
        }
    }
}
