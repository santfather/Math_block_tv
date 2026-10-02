package com.mathgate.detect

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetectorHeartbeatTest {

    @Test
    fun `a detector that never reported is stale`() {
        val heartbeat = DetectorHeartbeat { 10_000L }
        assertTrue(heartbeat.isStale(now = 10_000L, thresholdMs = 1_000L))
    }

    @Test
    fun `a fresh event is not stale`() {
        val heartbeat = DetectorHeartbeat { 10_000L }
        heartbeat.mark()
        assertFalse(heartbeat.isStale(now = 10_500L, thresholdMs = 1_000L))
    }

    @Test
    fun `the channel goes stale once the threshold passes`() {
        val heartbeat = DetectorHeartbeat { 10_000L }
        heartbeat.mark()
        assertTrue(heartbeat.isStale(now = 11_001L, thresholdMs = 1_000L))
    }
}
