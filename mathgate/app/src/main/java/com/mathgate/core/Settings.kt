package com.mathgate.core

/** User-configurable settings (phases 2 and 7). Defaults come from the roadmap. */
data class Settings(
    /** Time of accumulated viewing before the challenge appears (default 15 min). */
    val limitMs: Long = DEFAULT_LIMIT_MS,
    /** Problem difficulty, 1..4. */
    val difficultyLevel: Int = 1,
    /** D-09: optional 4-answer mode. Digital input is the default. */
    val multipleChoice: Boolean = false,
    /** Warning shown before the limit is reached; 0 disables it. */
    val warnBeforeMs: Long = 60_000L,
    /** D-04: reset the counter on a real power loss. Off by default. */
    val resetOnPowerLoss: Boolean = false,
    /** Packages treated as "watched" (D-02). Values must be confirmed in phase 0. */
    val watchedPackages: List<String> = DEFAULT_WATCHED_PACKAGES,
) {
    companion object {
        const val DEFAULT_LIMIT_MS: Long = 15 * 60 * 1000L

        /** Placeholders; the real package names are recorded in DEVICE_NOTES.md (phase 0). */
        val DEFAULT_WATCHED_PACKAGES: List<String> = listOf(
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvkids",
            "com.google.android.youtube.tvmusic",
        )
    }
}
