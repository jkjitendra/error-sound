package com.drostwades.errorsound

enum class ErrorKind {
    NONE,
    CONFIGURATION,
    COMPILATION,
    TEST_FAILURE,
    NETWORK,
    EXCEPTION,
    GENERIC,
    SUCCESS,
}

/**
 * Richer result returned by [ErrorClassifier.classifyTerminal].
 *
 * - [kind]          — the resolved [ErrorKind] (NONE means no alert)
 * - [soundOverride] — built-in sound ID to use for this event only; null means normal resolution
 * - [suppressed]    — true means the alert must be silenced entirely (e.g. SIGINT / exit 130)
 */
data class TerminalClassifyResult(
    val kind: ErrorKind,
    val soundOverride: String?,
    val suppressed: Boolean,
)

object ErrorClassifier {
    fun detect(outputText: String, exitCode: Int): ErrorKind {
        val text = outputText.lowercase()

        if (text.contains("could not resolve placeholder") ||
            text.contains("failed to load property source") ||
            text.contains("beancreationexception") ||
            text.contains("illegalstateexception")
        ) {
            return ErrorKind.CONFIGURATION
        }

        if (text.contains("compilation failed") ||
            text.contains("cannot find symbol") ||
            text.contains("error:") ||
            text.contains("kotlin:") && text.contains("error")
        ) {
            return ErrorKind.COMPILATION
        }

        if (text.contains("tests failed") ||
            text.contains("there were failing tests") ||
            text.contains("assertionerror")
        ) {
            return ErrorKind.TEST_FAILURE
        }

        if (text.contains("connection refused") ||
            text.contains("connect timed out") ||
            text.contains("unknownhostexception") ||
            text.contains("sockettimeoutexception")
        ) {
            return ErrorKind.NETWORK
        }

        if (text.contains("exception") ||
            text.contains("caused by:") ||
            text.contains("stacktrace")
        ) {
            return ErrorKind.EXCEPTION
        }

        if (exitCode != 0 ||
            text.contains("failed") ||
            text.contains("error")
        ) {
            return ErrorKind.GENERIC
        }

        return ErrorKind.NONE
    }

    @Suppress("UNUSED_PARAMETER")
    fun detectTerminal(command: String, exitCode: Int): ErrorKind {
        // Exit code 0 = success, no alert
        if (exitCode == 0) return ErrorKind.NONE
        // Any non-zero exit = generic shell error
        return ErrorKind.GENERIC
    }

    /**
     * Full terminal classification used by [AlertOnTerminalCommandListener].
     *
     * Checks [exitCodeRules] in order first (Phase 6); falls back to [detectTerminal] if no
     * enabled rule matches. Returns a [TerminalClassifyResult] carrying the kind, an optional
     * per-event sound override, and a suppression flag.
     *
     * This method is intentionally isolated to the terminal path — Run/Debug and console paths
     * are unaffected.
     */
    fun classifyTerminal(
        command: String,
        exitCode: Int,
        exitCodeRules: List<AlertSettings.ExitCodeRuleState>,
    ): TerminalClassifyResult {
        for (rule in exitCodeRules) {
            if (!rule.enabled) continue
            if (rule.exitCode != exitCode) continue
            val kind = ErrorKind.entries.find { it.name == rule.kind }
                ?.takeIf { it in CustomRuleEngine.ALLOWED_CUSTOM_RULE_KINDS }
                ?: ErrorKind.GENERIC
            return TerminalClassifyResult(
                kind = kind,
                soundOverride = rule.soundId?.takeIf { it.isNotBlank() },
                suppressed = rule.suppress,
            )
        }
        // No matching exit-code rule — use built-in fallback
        return TerminalClassifyResult(
            kind = detectTerminal(command, exitCode),
            soundOverride = null,
            suppressed = false,
        )
    }
}
