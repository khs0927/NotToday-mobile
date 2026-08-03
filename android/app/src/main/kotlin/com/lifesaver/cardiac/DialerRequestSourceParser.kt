package com.lifesaver.cardiac

import com.lifesaver.cardiac.escalation.DialerRequestSource

/**
 * Parses only explicit dialer request sources.
 *
 * Missing or unknown values are rejected so an untrusted caller cannot be
 * silently upgraded to a user-approved side effect.
 */
internal object DialerRequestSourceParser {
    fun parse(value: String?): DialerRequestSource? =
        when (value) {
            NativeSensorForegroundService.DIALER_SOURCE_USER ->
                DialerRequestSource.USER
            NativeSensorForegroundService.DIALER_SOURCE_PROMPT_TIMEOUT ->
                DialerRequestSource.PROMPT_TIMEOUT
            else -> null
        }
}
