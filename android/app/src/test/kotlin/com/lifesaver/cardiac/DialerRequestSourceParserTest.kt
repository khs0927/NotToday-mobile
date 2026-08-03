package com.lifesaver.cardiac

import com.lifesaver.cardiac.escalation.DialerRequestSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialerRequestSourceParserTest {
    @Test
    fun explicitSourcesMapToTheirOwnEnumValues() {
        assertEquals(
            DialerRequestSource.USER,
            DialerRequestSourceParser.parse(
                NativeSensorForegroundService.DIALER_SOURCE_USER,
            ),
        )
        assertEquals(
            DialerRequestSource.PROMPT_TIMEOUT,
            DialerRequestSourceParser.parse(
                NativeSensorForegroundService.DIALER_SOURCE_PROMPT_TIMEOUT,
            ),
        )
    }

    @Test
    fun missingAndUnknownSourcesAreRejected() {
        assertNull(DialerRequestSourceParser.parse(null))
        assertNull(DialerRequestSourceParser.parse(""))
        assertNull(DialerRequestSourceParser.parse("unexpected"))
    }
}
