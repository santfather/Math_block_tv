package com.mathgate.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinHasherTest {

    @Test
    fun `the salt is random and has the expected length`() {
        val salt = PinHasher.newSalt()
        assertEquals(PinHasher.SALT_LENGTH_BYTES, salt.size)
        assertFalse(salt.all { it == 0.toByte() })
        assertFalse(salt.contentEquals(PinHasher.newSalt()))
    }

    @Test
    fun `hashing is deterministic for the same pin and salt`() {
        val salt = PinHasher.newSalt()
        assertContentEquals(PinHasher.hash("1234", salt), PinHasher.hash("1234", salt))
    }

    @Test
    fun `a different salt produces a different hash`() {
        val first = PinHasher.hash("1234", PinHasher.newSalt())
        val second = PinHasher.hash("1234", PinHasher.newSalt())
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `verify accepts the right pin and rejects a wrong one`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234", salt)

        assertTrue(PinHasher.verify("1234", salt, hash))
        assertFalse(PinHasher.verify("4321", salt, hash))
        assertFalse(PinHasher.verify("1234", PinHasher.newSalt(), hash))
    }
}
