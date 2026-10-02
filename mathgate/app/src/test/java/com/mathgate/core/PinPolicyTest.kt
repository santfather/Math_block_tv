package com.mathgate.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinPolicyTest {

    @Test
    fun `a pin of four to six digits is valid`() {
        assertTrue(PinPolicy.isValid("1234"))
        assertTrue(PinPolicy.isValid("12345"))
        assertTrue(PinPolicy.isValid("123456"))
    }

    @Test
    fun `a pin outside the length or with non digits is rejected`() {
        assertFalse(PinPolicy.isValid(""))
        assertFalse(PinPolicy.isValid("123"))
        assertFalse(PinPolicy.isValid("1234567"))
        assertFalse(PinPolicy.isValid("12a4"))
        assertFalse(PinPolicy.isValid("12 4"))
    }

    @Test
    fun `the first attempts are free and the pause then grows`() {
        assertEquals(0L, PinPolicy.lockoutMs(0))
        assertEquals(0L, PinPolicy.lockoutMs(1))
        assertEquals(0L, PinPolicy.lockoutMs(PinPolicy.FREE_ATTEMPTS))
        assertEquals(5_000L, PinPolicy.lockoutMs(PinPolicy.FREE_ATTEMPTS + 1))
        assertEquals(10_000L, PinPolicy.lockoutMs(PinPolicy.FREE_ATTEMPTS + 2))
        assertEquals(20_000L, PinPolicy.lockoutMs(PinPolicy.FREE_ATTEMPTS + 3))
    }

    @Test
    fun `the pause is capped at five minutes`() {
        assertEquals(5 * 60_000L, PinPolicy.lockoutMs(PinPolicy.FREE_ATTEMPTS + 7))
        assertEquals(5 * 60_000L, PinPolicy.lockoutMs(50))
        assertEquals(5 * 60_000L, PinPolicy.lockoutMs(1_000))
    }
}
