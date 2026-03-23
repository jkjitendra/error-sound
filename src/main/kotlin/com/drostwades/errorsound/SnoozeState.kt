package com.drostwades.errorsound

import java.util.concurrent.atomic.AtomicLong

/**
 * Transient snooze state for temporarily muting all alerts.
 *
 * Intentionally NOT persisted — snooze always resets on IDE restart.
 * Thread-safe via AtomicLong. No cleanup needed: snooze expires naturally
 * when the system clock passes snoozeUntilEpochMillis.
 */
object SnoozeState {

    private val snoozeUntilEpochMillis = AtomicLong(0L)

    /** Returns true if alerts are currently snoozed. */
    fun isSnoozed(): Boolean = System.currentTimeMillis() < snoozeUntilEpochMillis.get()

    /**
     * Snooze for [durationMinutes] minutes from now.
     * Calling while already snoozed extends (or shortens) to the new duration.
     */
    fun snooze(durationMinutes: Int) {
        snoozeUntilEpochMillis.set(System.currentTimeMillis() + durationMinutes * 60_000L)
    }

    /** Cancel any active snooze immediately. */
    fun resume() {
        snoozeUntilEpochMillis.set(0L)
    }

    /**
     * Returns a human-readable status string, e.g. "Snoozed until 14:30"
     * or null if not currently snoozed.
     */
    fun statusLabel(): String? {
        val until = snoozeUntilEpochMillis.get()
        if (System.currentTimeMillis() >= until) return null
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = until }
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val m = cal.get(java.util.Calendar.MINUTE)
        return "Snoozed until %02d:%02d".format(h, m)
    }
}
