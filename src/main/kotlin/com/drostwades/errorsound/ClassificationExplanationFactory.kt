package com.drostwades.errorsound

object ClassificationExplanationFactory {

    fun customRegex(
        source: AlertMatchExplanation.Source,
        match: CustomRuleEngine.CustomRuleMatch,
        exitCode: Int? = null,
        context: String? = null,
    ): AlertMatchExplanation =
        AlertMatchExplanation(
            source = source,
            cause = AlertMatchExplanation.Cause.CUSTOM_REGEX_RULE,
            kind = match.kind,
            message = "Custom regex rule matched ${match.target}",
            ruleId = match.id,
            rulePattern = match.pattern,
            matchTarget = match.target,
            exitCode = exitCode,
            commandOrConfig = context,
        )

    fun builtIn(
        source: AlertMatchExplanation.Source,
        result: BuiltInClassificationResult,
        exitCode: Int? = null,
        context: String? = null,
    ): AlertMatchExplanation =
        AlertMatchExplanation(
            source = source,
            cause = AlertMatchExplanation.Cause.BUILT_IN_CLASSIFIER,
            kind = result.kind,
            message = "Built-in classifier matched ${result.cause}",
            exitCode = exitCode,
            commandOrConfig = context,
        )

    fun terminalExitCodeRule(
        rule: TerminalExitCodeRuleMatch,
        command: String,
        soundOverride: String?,
    ): AlertMatchExplanation =
        AlertMatchExplanation(
            source = AlertMatchExplanation.Source.TERMINAL,
            cause = AlertMatchExplanation.Cause.TERMINAL_EXIT_CODE_RULE,
            kind = rule.kind,
            message = "Terminal exit-code rule matched exit code ${rule.exitCode}",
            exitCode = rule.exitCode,
            commandOrConfig = command,
            soundOverride = soundOverride,
        )

    fun terminalExitCodeSuppressed(
        rule: TerminalExitCodeRuleMatch,
        command: String,
    ): AlertMatchExplanation =
        AlertMatchExplanation(
            source = AlertMatchExplanation.Source.TERMINAL,
            cause = AlertMatchExplanation.Cause.TERMINAL_EXIT_CODE_SUPPRESSED,
            kind = rule.kind,
            message = "Terminal exit-code rule suppressed exit code ${rule.exitCode}",
            exitCode = rule.exitCode,
            commandOrConfig = command,
            soundOverride = rule.soundId,
            suppressed = true,
        )

    fun terminalBuiltInFallback(command: String, exitCode: Int, kind: ErrorKind): AlertMatchExplanation =
        AlertMatchExplanation(
            source = AlertMatchExplanation.Source.TERMINAL,
            cause = AlertMatchExplanation.Cause.BUILT_IN_CLASSIFIER,
            kind = kind,
            message = "Built-in terminal classifier matched non-zero exit code",
            exitCode = exitCode,
            commandOrConfig = command,
        )

    fun successFallback(source: AlertMatchExplanation.Source, exitCode: Int, context: String? = null): AlertMatchExplanation =
        AlertMatchExplanation(
            source = source,
            cause = AlertMatchExplanation.Cause.SUCCESS_FALLBACK,
            kind = ErrorKind.SUCCESS,
            message = "No error matched and exit code was 0",
            exitCode = exitCode,
            commandOrConfig = context,
        )

    fun noMatch(source: AlertMatchExplanation.Source, exitCode: Int? = null, context: String? = null): AlertMatchExplanation =
        AlertMatchExplanation(
            source = source,
            cause = AlertMatchExplanation.Cause.NO_MATCH,
            kind = ErrorKind.NONE,
            message = "No custom or built-in classifier matched",
            exitCode = exitCode,
            commandOrConfig = context,
        )

    fun durationThresholdSuppressed(
        kind: ErrorKind,
        exitCode: Int,
        elapsedMillis: Long,
        thresholdMillis: Long,
        context: String?,
    ): AlertMatchExplanation =
        AlertMatchExplanation(
            source = AlertMatchExplanation.Source.RUN_DEBUG,
            cause = AlertMatchExplanation.Cause.DURATION_THRESHOLD_SUPPRESSED,
            kind = kind,
            message = "Suppressed by duration threshold: elapsed=${elapsedMillis}ms threshold=${thresholdMillis}ms",
            exitCode = exitCode,
            commandOrConfig = context,
            suppressed = true,
        )
}
