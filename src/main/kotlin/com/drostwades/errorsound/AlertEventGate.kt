package com.drostwades.errorsound

/**
 * Central deduplication gate for alert events.
 *
 * Prevents duplicate sounds when the three detection paths
 * (console filter, execution listener, terminal listener) all
 * fire for the same logical failure.
 *
 * Thread-safety: a single `@Synchronized` lock keeps the check-then-set
 * atomic. Alert frequency is very low so lock contention is negligible.
 */
object AlertEventGate {

    private const val PER_KEY_COOLDOWN_MS = 4_000L
    private const val GLOBAL_COOLDOWN_MS  = 2_000L
    private const val EVICT_AFTER_MS      = 60_000L
    private const val EVICT_THRESHOLD     = 512

    private val keyLastSeen = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var globalLastSeen = 0L

    /**
     * Returns `true` if the caller is allowed to play a sound, `false` if this
     * (key, event) is considered a duplicate.
     *
     * Rules (both must pass):
     * 1. No sound was played for [key] within [PER_KEY_COOLDOWN_MS].
     * 2. No sound was played globally within [GLOBAL_COOLDOWN_MS].
     *
     * Terminal keys include command text, so the map can grow over long sessions.
     * A lightweight eviction step runs whenever the map exceeds [EVICT_THRESHOLD]
     * entries and removes entries that are older than [EVICT_AFTER_MS].
     *
     * @param key Stable identifier for the event source. Examples:
     *   - `"console:{project.locationHash}:{errorKind}"`
     *   - `"exec:{handlerIdentityHash}:{errorKind}"`
     *   - `"terminal:{project.locationHash}:{command}:{exitCode}:{errorKind}"`
     */
    @Synchronized
    fun shouldPlay(key: String, now: Long = System.currentTimeMillis()): Boolean {
        if (keyLastSeen.size > EVICT_THRESHOLD) {
            keyLastSeen.entries.removeIf { now - it.value > EVICT_AFTER_MS }
        }
        val lastForKey = keyLastSeen[key]
        if (lastForKey != null && now - lastForKey < PER_KEY_COOLDOWN_MS) return false
        if (now - globalLastSeen < GLOBAL_COOLDOWN_MS) return false
        keyLastSeen[key] = now
        globalLastSeen = now
        return true
    }
}
