package com.drostwades.errorsound

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

/**
 * Single choke-point between the three detection paths and the audio player.
 *
 * Every call site (console filter, execution listener, terminal listener) must
 * come through here. This keeps [AlertMonitoring], [AlertEventGate],
 * [ErrorSoundPlayer], and the optional visual notification wired in exactly
 * one place, making future changes trivial.
 *
 * Gate order:
 * 1. [SnoozeState] — transient mute check (fastest, no settings needed)
 * 2. [AlertMonitoring] — per-kind enabled check
 * 3. [AlertEventGate] — deduplication / cooldown
 * 4. [ErrorSoundPlayer] — playback (optional per-event [soundOverride])
 * 5. Visual notification — balloon (after sound, guarded by [AlertSettings.State.showVisualNotification])
 *
 * @param key           Stable deduplication key — see [AlertEventGate.shouldPlay].
 * @param soundOverride Optional built-in sound ID to use for this event only (Phase 6 exit-code
 *                      rules). Null means use the normal global/per-kind resolution. All call
 *                      sites except the terminal listener pass null.
 * @param project       The active project, used to anchor balloon notifications.
 *                      Pass `null` if no project is available; notifications are skipped.
 */
object AlertDispatcher {

    private val log = Logger.getInstance(AlertDispatcher::class.java)

    fun tryAlert(
        key: String,
        settings: AlertSettings.State,
        kind: ErrorKind,
        project: Project? = null,
        soundOverride: String? = null,
        explanation: AlertMatchExplanation? = null,
    ) {
        if (SnoozeState.isSnoozed()) {
            log.debug("Alert suppressed by snooze: ${explanation?.summary() ?: "no explanation"}")
            return
        }
        if (!AlertMonitoring.shouldMonitor(settings, kind)) {
            log.debug("Alert suppressed by monitoring settings: ${explanation?.summary() ?: "no explanation"}")
            return
        }
        if (!AlertEventGate.shouldPlay(key)) {
            log.debug("Alert suppressed by dedup gate: ${explanation?.summary() ?: "no explanation"}")
            return
        }
        log.debug("Alert accepted: ${explanation?.summary() ?: "no explanation"}")
        ErrorSoundPlayer.play(settings, kind, soundOverride)
        if (settings.showVisualNotification && project != null) {
            showNotification(settings, kind, project)
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun showNotification(settings: AlertSettings.State, kind: ErrorKind, project: Project) {
        val isSuccess = kind == ErrorKind.SUCCESS
        if (isSuccess && !settings.visualNotificationOnSuccess) return
        if (!isSuccess && !settings.visualNotificationOnError) return

        val group = NotificationGroupManager.getInstance()
            .getNotificationGroup("Error Sound Alert") ?: return

        val (title, body) = notificationContent(kind)
        val type = if (isSuccess) NotificationType.INFORMATION else NotificationType.WARNING

        val notification = group.createNotification(title, body, type)

        // Action: open plugin settings
        notification.addAction(NotificationAction.createSimple("Open Settings") {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, ErrorSoundConfigurable::class.java)
        })

        // Action: mute 1 hour (integrates with Phase 3 SnoozeState)
        notification.addAction(NotificationAction.createSimple("Mute 1 hr") {
            SnoozeState.snooze(60)
            notification.expire()
        })

        notification.notify(project)
    }

    private fun notificationContent(kind: ErrorKind): Pair<String, String> = when (kind) {
        ErrorKind.CONFIGURATION -> "Configuration Error" to "A run/debug configuration error was detected."
        ErrorKind.COMPILATION   -> "Compilation Failed" to "Build failed due to a compilation error."
        ErrorKind.TEST_FAILURE  -> "Test Failed" to "One or more tests did not pass."
        ErrorKind.NETWORK       -> "Network Error" to "A network-related error was detected."
        ErrorKind.EXCEPTION     -> "Exception Detected" to "An unhandled exception appeared in the output."
        ErrorKind.GENERIC       -> "Error Detected" to "An error was detected in the output."
        ErrorKind.SUCCESS       -> "Process Completed" to "Run/debug process finished successfully."
        ErrorKind.NONE          -> "Alert" to ""
    }
}
