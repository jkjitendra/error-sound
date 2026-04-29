package com.drostwades.errorsound

/**
 * Runtime-facing explanation for why an alert classification was produced.
 *
 * This is intentionally separate from playback. It is created near classification time,
 * passed through [AlertDispatcher], and can be reused by future notification/history UI.
 */
data class AlertMatchExplanation(
    val source: Source,
    val cause: Cause,
    val kind: ErrorKind,
    val message: String,
    val ruleId: String? = null,
    val rulePattern: String? = null,
    val matchTarget: AlertSettings.MatchTarget? = null,
    val exitCode: Int? = null,
    val commandOrConfig: String? = null,
    val soundOverride: String? = null,
    val suppressed: Boolean = false,
) {
    enum class Source {
        RUN_DEBUG,
        CONSOLE,
        TERMINAL,
    }

    enum class Cause {
        CUSTOM_REGEX_RULE,
        BUILT_IN_CLASSIFIER,
        TERMINAL_EXIT_CODE_RULE,
        TERMINAL_EXIT_CODE_SUPPRESSED,
        SUCCESS_FALLBACK,
        NO_MATCH,
        DURATION_THRESHOLD_SUPPRESSED,
    }

    fun summary(): String {
        val parts = mutableListOf(
            "source=$source",
            "cause=$cause",
            "kind=$kind",
        )
        exitCode?.let { parts += "exitCode=$it" }
        ruleId?.let { parts += "ruleId=$it" }
        matchTarget?.let { parts += "target=$it" }
        soundOverride?.let { parts += "soundOverride=$it" }
        if (suppressed) parts += "suppressed=true"
        commandOrConfig?.takeIf { it.isNotBlank() }?.let { parts += "context=${it.take(120)}" }
        parts += "message=${message.take(180)}"
        return parts.joinToString(", ")
    }
}
