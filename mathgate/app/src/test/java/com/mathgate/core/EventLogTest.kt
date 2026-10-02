package com.mathgate.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventLogTest {

    private var now = 1_000L
    private val log = EventLog(capacity = 3, timeProvider = { now })

    @Test
    fun `records entries in order with timestamps`() {
        log.record("A", "first")
        now = 2_000L
        log.record("B", "second")

        val entries = log.snapshot()
        assertEquals(listOf("first", "second"), entries.map { it.message })
        assertEquals(listOf(1_000L, 2_000L), entries.map { it.timestampMs })
        assertEquals(listOf("A", "B"), entries.map { it.tag })
    }

    @Test
    fun `evicts the oldest entry once capacity is reached`() {
        log.record("A", "1")
        log.record("A", "2")
        log.record("A", "3")
        log.record("A", "4")

        assertEquals(listOf("2", "3", "4"), log.snapshot().map { it.message })
    }

    @Test
    fun `snapshot is a copy and clear empties the log`() {
        log.record("A", "1")
        val snapshot = log.snapshot()
        log.record("A", "2")

        assertEquals(1, snapshot.size)
        assertEquals(2, log.snapshot().size)

        log.clear()
        assertTrue(log.snapshot().isEmpty())
    }

    @Test
    fun `default capacity matches the roadmap value`() {
        assertEquals(500, EventLog.DEFAULT_CAPACITY)
        assertEquals(500, EventLog().capacity)
    }
}
