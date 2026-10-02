package com.mathgate.detect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Primary detector fed by `GuardAccessibilityService` window events (phase 3).
 * System overlays (volume panel, TV bar) are filtered out so they never look like leaving YouTube.
 *
 * Pure Kotlin (only coroutines): unit-testable on the JVM.
 */
class A11yForegroundDetector(
    private val ignoredPackages: Set<String> = ForegroundFilter.DEFAULT_IGNORED_PACKAGES,
) : ForegroundDetector {

    private val _watchedForeground = MutableStateFlow(false)
    override val watchedForeground: StateFlow<Boolean> = _watchedForeground.asStateFlow()

    /**
     * Called by the accessibility service on every window-state change.
     * Ignored windows (null package, system overlays) leave the current value untouched.
     */
    fun onPackageChanged(packageName: String?, watchedPackages: Set<String>) {
        when (ForegroundFilter.decide(packageName, watchedPackages, ignoredPackages)) {
            ForegroundDecision.IGNORE -> Unit
            ForegroundDecision.WATCHED -> _watchedForeground.value = true
            ForegroundDecision.NOT_WATCHED -> _watchedForeground.value = false
        }
    }
}
