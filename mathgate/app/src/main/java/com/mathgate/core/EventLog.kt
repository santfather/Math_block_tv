package com.mathgate.core

/** One entry of the diagnostic ring log (phase 3). */
data class LogEntry(
    val timestampMs: Long,
    val tag: String,
    val message: String,
)

/**
 * Fixed-size in-memory ring log of recent transitions (last [capacity] entries).
 *
 * Used for on-device debugging and, later, the parent statistics screen (phase 7).
 * Pure Kotlin and thread-safe so it can be written from several services at once.
 */
class EventLog(
    val capacity: Int = DEFAULT_CAPACITY,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {

    private val entries = ArrayDeque<LogEntry>(capacity)
    private val lock = Any()

    /** Appends an entry, evicting the oldest one once [capacity] is reached. */
    fun record(tag: String, message: String) {
        synchronized(lock) {
            if (entries.size >= capacity) entries.removeFirst()
            entries.addLast(LogEntry(timestampMs = timeProvider(), tag = tag, message = message))
        }
    }

    /** Returns a snapshot of the current entries, oldest first. */
    fun snapshot(): List<LogEntry> = synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    companion object {
        /** Roadmap phase 3: keep roughly the last 500 records. */
        const val DEFAULT_CAPACITY: Int = 500
    }
}
