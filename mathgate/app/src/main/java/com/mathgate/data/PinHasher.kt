package com.mathgate.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN hashing with a per-install salt (roadmap rule 6: never store a plain PIN).
 *
 * PBKDF2-HMAC-SHA256 with a high iteration count; verification compares the derived key in
 * constant time so a timing side channel cannot reveal the PIN. Pure JVM crypto, so the
 * class is unit-testable without an Android device.
 */
object PinHasher {

    const val SALT_LENGTH_BYTES = 16

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    /** Fresh random salt for a new PIN. */
    fun newSalt(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(SALT_LENGTH_BYTES).also(random::nextBytes)

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison of the derived key with [expected]. */
    fun verify(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(pin, salt), expected)
}
