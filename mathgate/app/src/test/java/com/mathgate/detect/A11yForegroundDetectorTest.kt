package com.mathgate.detect

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class A11yForegroundDetectorTest {

    private val watched = setOf("com.google.android.youtube.tv", "com.google.android.youtube.tvkids")
    private val detector = A11yForegroundDetector()

    @Test
    fun `starts as not watched`() {
        assertFalse(detector.watchedForeground.value)
    }

    @Test
    fun `watched package turns the signal on and other apps turn it off`() {
        detector.onPackageChanged("com.google.android.youtube.tv", watched)
        assertTrue(detector.watchedForeground.value)

        detector.onPackageChanged("com.google.android.apps.tv.launcherx", watched)
        assertFalse(detector.watchedForeground.value)
    }

    @Test
    fun `system overlays do not change the state`() {
        detector.onPackageChanged("com.google.android.youtube.tv", watched)
        detector.onPackageChanged("com.android.systemui", watched)
        assertTrue(detector.watchedForeground.value)

        detector.onPackageChanged("com.google.android.apps.tv.launcherx", watched)
        detector.onPackageChanged("com.android.systemui", watched)
        assertFalse(detector.watchedForeground.value)
    }

    @Test
    fun `null package does not change the state`() {
        detector.onPackageChanged(null, watched)
        assertFalse(detector.watchedForeground.value)

        detector.onPackageChanged("com.google.android.youtube.tv", watched)
        detector.onPackageChanged(null, watched)
        assertTrue(detector.watchedForeground.value)
    }

    @Test
    fun `the challenge window does not stop counting and no resume event is needed`() {
        // YouTube is being watched.
        detector.onPackageChanged("com.google.android.youtube.tv", watched)
        assertTrue(detector.watchedForeground.value)

        // The challenge covers YouTube; our own window must not look like leaving it.
        detector.onPackageChanged(ForegroundFilter.SELF_PACKAGE, watched)
        assertTrue(detector.watchedForeground.value)

        // The child solves it; YouTube is visible again without any new window event, so the
        // signal must still be "watched" — otherwise the counter would never restart.
        assertTrue(detector.watchedForeground.value)
    }
}
