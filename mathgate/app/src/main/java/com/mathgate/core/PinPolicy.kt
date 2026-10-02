package com.mathgate.core

/**
 * Pure rules for the parental PIN (phase 7): the accepted format and the anti-brute-force
 * pause. Hashing itself lives in `data/PinHasher`, so `core/` stays free of cryptography
 * and `android.*`.
 */
object PinPolicy {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    /** Wrong attempts allowed before the growing pause kicks in (roadmap: limit + growing pause). */
    const val FREE_ATTEMPTS = 3

    private const val BASE_LOCKOUT_MS = 5_000L
    private const val MAX_LOCKOUT_MS = 5 * 60_000L

    /** A PIN is 4-6 digits. */
    fun isValid(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    /**
     * Pause after [failedAttempts] cumulative failures: `0, 0, 0, 5s, 10s, 20s, …`,
     * capped at 5 minutes.
     */
    fun lockoutMs(failedAttempts: Int): Long {
        val extra = failedAttempts - FREE_ATTEMPTS
        if (extra <= 0) return 0L
        // Avoid an overflowing shift for absurd attempt counts; anything above ~10 is capped anyway.
        if (extra > 10) return MAX_LOCKOUT_MS
        return minOf(BASE_LOCKOUT_MS shl (extra - 1), MAX_LOCKOUT_MS)
    }
}
