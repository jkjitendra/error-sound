package com.drostwades.errorsound

/**
 * Single choke-point between the three detection paths and the audio player.
 *
 * Every call site (console filter, execution listener, terminal listener) must
 * come through here. This keeps [AlertMonitoring], [AlertEventGate], and
 * [ErrorSoundPlayer] wired in exactly one place, making future changes trivial.
 *
 * Gate order:
 * 1. [SnoozeState] — transient mute check (fastest, no settings needed)
 * 2. [AlertMonitoring] — per-kind enabled check
 * 3. [AlertEventGate] — deduplication / cooldown
 * 4. [ErrorSoundPlayer] — playback
 *
 * @param key Stable deduplication key — see [AlertEventGate.shouldPlay].
 */
object AlertDispatcher {

    fun tryAlert(key: String, settings: AlertSettings.State, kind: ErrorKind) {
        if (SnoozeState.isSnoozed()) return
        if (!AlertMonitoring.shouldMonitor(settings, kind)) return
        if (!AlertEventGate.shouldPlay(key)) return
        ErrorSoundPlayer.play(settings, kind)
    }
}
