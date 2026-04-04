package com.drostwades.errorsound

import com.intellij.util.messages.Topic
import java.util.concurrent.atomic.AtomicLong

/**
 * Transient snooze state for temporarily muting all alerts.
 *
 * Intentionally NOT persisted — snooze always resets on IDE restart.
 * Thread-safe via AtomicLong. No cleanup needed: snooze expires naturally
 * when the system clock passes snoozeUntilEpochMillis.
 *
 * Any component that needs to react to snooze changes (e.g. the tool window)
 * should subscribe to [TOPIC] on the application message bus.
 */
object SnoozeState {

    /**
     * Application-level message bus topic. Published whenever snooze state changes
     * so that UI components (e.g. [ErrorSoundToolWindowPanel]) can update immediately
     * without polling.
     */
    val TOPIC: Topic<SnoozeListener> = Topic.create("ErrorSound.SnoozeChanged", SnoozeListener::class.java)

    /** Listener interface for snooze state changes. */
    fun interface SnoozeListener {
        /**
         * Called after every [snooze] or [resume] on the thread that made the call
         * (via [com.intellij.util.messages.MessageBus.syncPublisher]).
         * Implementations that touch Swing state must dispatch to the EDT themselves.
         */
        fun snoozeChanged()
    }

    private val snoozeUntilEpochMillis = AtomicLong(0L)

    /** Returns true if alerts are currently snoozed. */
    fun isSnoozed(): Boolean = System.currentTimeMillis() < snoozeUntilEpochMillis.get()

    /**
     * Snooze for [durationMinutes] minutes from now.
     * Calling while already snoozed extends (or shortens) to the new duration.
     * Publishes [TOPIC] after updating state.
     */
    fun snooze(durationMinutes: Int) {
        snoozeUntilEpochMillis.set(System.currentTimeMillis() + durationMinutes * 60_000L)
        publish()
    }

    /**
     * Cancel any active snooze immediately.
     * Publishes [TOPIC] after updating state.
     */
    fun resume() {
        snoozeUntilEpochMillis.set(0L)
        publish()
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

    private fun publish() {
        com.intellij.openapi.application.ApplicationManager.getApplication()
            .messageBus
            .syncPublisher(TOPIC)
            .snoozeChanged()
    }
}
