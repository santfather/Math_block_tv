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
}
