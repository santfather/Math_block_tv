package com.mathgate.detect

import kotlinx.coroutines.flow.StateFlow

/**
 * Unified "watched app is on the foreground" signal consumed by the engine (D-02).
 * Implemented by the accessibility detector (primary) and the UsageStats poller (fallback).
 *
 * Concrete source selection is wired when the engine subscribes (phase 5).
 */
interface ForegroundDetector {

    /** Emits `true` while one of the watched packages is in the foreground. */
    val watchedForeground: StateFlow<Boolean>
}

/** How a foreground window maps to the watched state (phase 3). */
enum class ForegroundDecision {
    /** Keep the previous state: the window is a transient system overlay or unknown. */
    IGNORE,

    /** A watched package went to the foreground. */
    WATCHED,

    /** Some other app went to the foreground, i.e. the watched app was left. */
    NOT_WATCHED,
}

/**
 * Pure classification of foreground-window packages, shared by the accessibility
 * detector (D-02, primary) and the UsageStats poller (fallback). No `android.*` imports.
 */
object ForegroundFilter {

    /**
     * Math Gate's own package. The challenge screen, the settings and the setup wizard are
     * shown *over* the watched app; if they counted as "left YouTube", the counter would stop
     * and never resume (the firmware does not always report the underlying window again when an
     * overlay closes), so the next challenge would never appear (bugfix, phase 10).
     */
    const val SELF_PACKAGE: String = "com.mathgate"

    /**
     * Package windows that must NOT change the watched state: our own surfaces and transient
     * system windows (volume panel, TV info bar) that can briefly appear above the watched app.
     *
     * The list is intentionally small; phase-3 device tests extend it as needed.
     */
    val DEFAULT_IGNORED_PACKAGES: Set<String> = setOf(
        "com.android.systemui",
        SELF_PACKAGE,
    )

    fun decide(
        packageName: String?,
        watchedPackages: Set<String>,
        ignoredPackages: Set<String> = DEFAULT_IGNORED_PACKAGES,
    ): ForegroundDecision = when {
        packageName.isNullOrEmpty() -> ForegroundDecision.IGNORE
        packageName in ignoredPackages -> ForegroundDecision.IGNORE
        packageName in watchedPackages -> ForegroundDecision.WATCHED
        else -> ForegroundDecision.NOT_WATCHED
    }
}
