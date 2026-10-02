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
     * System windows that can briefly appear above the watched app (volume panel, TV info
     * bar). Their events must NOT look like leaving YouTube, so the previous state is kept.
     *
     * The list is intentionally small; phase-3 device tests extend it as needed.
     */
    val DEFAULT_IGNORED_PACKAGES: Set<String> = setOf(
        "com.android.systemui",
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
