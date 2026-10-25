package com.example

import com.example.data.remote.TempNumberService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun verifyTempNumberServiceAndOtpReadiness() {
        val service = TempNumberService()

        // 1. Verify OTP extraction logic
        val otp1 = service.extractOtpFromSms("<#> Your WhatsApp code is 482-910. Don't share this code.")
        assertEquals("482910", otp1)

        val otp2 = service.extractOtpFromSms("Your Flipkart verification OTP is 739204. Valid for 10 mins.")
        assertEquals("739204", otp2)

        // 2. Verify freshness check
        assertTrue(service.isFreshRealTimeMessage("just now"))
        assertTrue(service.isFreshRealTimeMessage("12 min ago"))
        assertTrue(service.isFreshRealTimeMessage("1 hour ago"))
        assertFalse(service.isFreshRealTimeMessage("1 months ago"))
        assertFalse(service.isFreshRealTimeMessage("2 weeks ago"))
        assertNotNull(otp1)
    }
}
