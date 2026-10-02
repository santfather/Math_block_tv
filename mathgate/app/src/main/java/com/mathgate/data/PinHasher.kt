package com.mathgate.data

/**
 * PIN hashing with a per-install salt (roadmap rule 6: never store a plain PIN).
 * PBKDF2/Argon2 implementation lands in phase 7.
 */
object PinHasher {

    fun hash(pin: String, salt: ByteArray): ByteArray = TODO("implemented in phase 7")

    fun verify(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        TODO("implemented in phase 7")
}
