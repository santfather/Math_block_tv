package com.mathgate.detect

import kotlin.test.Test
import kotlin.test.assertEquals

class ForegroundFilterTest {

    private val watched = setOf("com.google.android.youtube.tv", "com.google.android.youtube.tvkids")
    private val ignored = setOf("com.android.systemui")

    @Test
    fun `a watched package counts as foreground`() {
        assertEquals(
            ForegroundDecision.WATCHED,
            ForegroundFilter.decide("com.google.android.youtube.tv", watched, ignored),
        )
    }

    @Test
    fun `a non-watched package means the watched app was left`() {
        assertEquals(
            ForegroundDecision.NOT_WATCHED,
            ForegroundFilter.decide("com.google.android.apps.tv.launcherx", watched, ignored),
        )
    }

    @Test
    fun `null and empty packages are ignored`() {
        assertEquals(ForegroundDecision.IGNORE, ForegroundFilter.decide(null, watched, ignored))
        assertEquals(ForegroundDecision.IGNORE, ForegroundFilter.decide("", watched, ignored))
    }

    @Test
    fun `system overlays are ignored even if they could be watched`() {
        assertEquals(ForegroundDecision.IGNORE, ForegroundFilter.decide("com.android.systemui", watched, ignored))
        // Ignoring wins over watching when a package is in both lists.
        assertEquals(ForegroundDecision.IGNORE, ForegroundFilter.decide("com.android.systemui", ignored, ignored))
    }

    @Test
    fun `math gate's own windows do not count as leaving the watched app`() {
        val defaults = ForegroundFilter.DEFAULT_IGNORED_PACKAGES
        assertEquals(
            ForegroundDecision.IGNORE,
            ForegroundFilter.decide(ForegroundFilter.SELF_PACKAGE, watched, defaults),
        )
    }
}
