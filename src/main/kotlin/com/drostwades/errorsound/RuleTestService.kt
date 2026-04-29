package com.drostwades.errorsound

import java.util.regex.PatternSyntaxException

/**
 * Pure evaluation helper for the settings Rule Testing Sandbox.
 *
 * This deliberately does not participate in runtime detection. It mirrors the relevant
 * target/source semantics so users can understand what a rule would do before applying it.
 */
object RuleTestService {

    enum class SourceMode(private val label: String) {
        RUN_DEBUG("Run/Debug"),
        CONSOLE("Console"),
        TERMINAL("Terminal");

        override fun toString(): String = label
    }

    data class Input(
        val rules: List<AlertSettings.CustomRuleState>,
        val sampleOutput: String,
        val matchTarget: AlertSettings.MatchTarget,
        val exitCode: Int,
        val sourceMode: SourceMode,
    )

    data class Result(
        val customMatch: RuleMatch?,
        val builtInKind: ErrorKind,
        val validationErrors: List<ValidationError>,
        val notes: List<String>,
    ) {
        val customMatched: Boolean = customMatch != null
        val builtInWouldMatch: Boolean = builtInKind != ErrorKind.NONE
        val resultingKind: ErrorKind = customMatch?.kind ?: builtInKind
    }

    data class RuleMatch(
        val rowNumber: Int,
        val id: String,
        val pattern: String,
        val target: AlertSettings.MatchTarget,
        val kind: ErrorKind,
        val matchedContext: String,
    )

    data class ValidationError(
        val rowNumber: Int,
        val id: String,
        val pattern: String,
        val message: String,
    )

    private data class PreparedRule(
        val rowNumber: Int,
        val id: String,
        val pattern: String,
        val target: AlertSettings.MatchTarget,
        val kind: ErrorKind,
        val regex: Regex,
    )

    private val consoleErrorPattern = Regex(
        listOf(
            """(?i)\bException\b""",
            """(?i)\bError\b""",
            """(?i)\bFATAL\b""",
            """(?i)Caused by:""",
            """^\s+at\s+[\w.${'$'}]+\(""",
            """(?i)BUILD FAILED""",
            """(?i)FAILURE:""",
            """(?i)Tests? failed""",
            """(?i)compilation failed""",
            """(?i)command not found""",
        ).joinToString("|"),
        RegexOption.MULTILINE
    )

    fun evaluate(input: Input): Result {
        val notes = mutableListOf<String>()
        if (input.rules.size > CustomRuleEngine.MAX_RULES) {
            notes += "Only the first ${CustomRuleEngine.MAX_RULES} custom rules are evaluated, matching runtime behavior."
        }

        val targetSupported = isTargetSupported(input.sourceMode, input.matchTarget)
        if (!targetSupported) {
            notes += "${input.sourceMode} does not evaluate ${input.matchTarget} custom rules at runtime."
        }

        val prepared = prepareRules(input.rules, input.matchTarget)
        val validationErrors = prepared.second
        val customMatch = if (targetSupported) {
            findCustomMatch(prepared.first, input.sampleOutput, input.matchTarget, input.exitCode)
        } else {
            null
        }

        val builtInKind = detectBuiltIn(input.sampleOutput, input.exitCode, input.sourceMode, input.matchTarget)

        return Result(
            customMatch = customMatch,
            builtInKind = builtInKind,
            validationErrors = validationErrors,
            notes = notes,
        )
    }

    private fun prepareRules(
        rules: List<AlertSettings.CustomRuleState>,
        selectedTarget: AlertSettings.MatchTarget,
    ): Pair<List<PreparedRule>, List<ValidationError>> {
        val preparedRules = mutableListOf<PreparedRule>()
        val validationErrors = mutableListOf<ValidationError>()

        rules.take(CustomRuleEngine.MAX_RULES).forEachIndexed { index, rule ->
            if (!rule.enabled) return@forEachIndexed
            val pattern = rule.pattern.trim().take(CustomRuleEngine.MAX_PATTERN_LENGTH)
            if (pattern.isBlank()) return@forEachIndexed

            val target = AlertSettings.MatchTarget.entries.find { it.name == rule.matchTarget }
                ?: AlertSettings.MatchTarget.LINE_TEXT
            if (target != selectedTarget) return@forEachIndexed

            val kind = ErrorKind.entries.find { it.name == rule.kind }
                ?.takeIf { it in CustomRuleEngine.ALLOWED_CUSTOM_RULE_KINDS }
                ?: ErrorKind.GENERIC

            val regex = try {
                Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            } catch (e: PatternSyntaxException) {
                validationErrors += ValidationError(
                    rowNumber = index + 1,
                    id = rule.id,
                    pattern = pattern,
                    message = e.description ?: e.message ?: "Invalid regex pattern",
                )
                return@forEachIndexed
            }

            preparedRules += PreparedRule(
                rowNumber = index + 1,
                id = rule.id,
                pattern = pattern,
                target = target,
                kind = kind,
                regex = regex,
            )
        }

        return preparedRules to validationErrors
    }

    private fun findCustomMatch(
        rules: List<PreparedRule>,
        sampleOutput: String,
        target: AlertSettings.MatchTarget,
        exitCode: Int,
    ): RuleMatch? {
        return when (target) {
            AlertSettings.MatchTarget.LINE_TEXT -> {
                val lines = sampleOutput.lineSequence().ifEmpty { sequenceOf("") }
                for ((lineIndex, line) in lines.withIndex()) {
                    val match = rules.firstOrNull { it.regex.containsMatchIn(line) } ?: continue
                    return match.toRuleMatch("line ${lineIndex + 1}: ${line.abbreviateForRuleTest()}")
                }
                null
            }
            AlertSettings.MatchTarget.FULL_OUTPUT -> {
                rules.firstOrNull { it.regex.containsMatchIn(sampleOutput) }
                    ?.toRuleMatch("full output")
            }
            AlertSettings.MatchTarget.EXIT_CODE_AND_TEXT -> {
                val combined = "exitcode:$exitCode\n$sampleOutput"
                rules.firstOrNull { it.regex.containsMatchIn(combined) }
                    ?.toRuleMatch("exitcode:$exitCode + sample output")
            }
        }
    }

    private fun detectBuiltIn(
        sampleOutput: String,
        exitCode: Int,
        sourceMode: SourceMode,
        matchTarget: AlertSettings.MatchTarget,
    ): ErrorKind {
        return when (sourceMode) {
            SourceMode.RUN_DEBUG -> when (matchTarget) {
                AlertSettings.MatchTarget.LINE_TEXT -> {
                    sampleOutput.lineSequence()
                        .map { ErrorClassifier.detect(it, 0) }
                        .firstOrNull { it != ErrorKind.NONE }
                        ?: ErrorKind.NONE
                }
                AlertSettings.MatchTarget.FULL_OUTPUT,
                AlertSettings.MatchTarget.EXIT_CODE_AND_TEXT -> ErrorClassifier.detect(sampleOutput, exitCode)
            }
            SourceMode.CONSOLE -> {
                sampleOutput.lineSequence().firstNotNullOfOrNull { line ->
                    if (consoleErrorPattern.containsMatchIn(line)) ErrorClassifier.detect(line, 1) else null
                } ?: ErrorKind.NONE
            }
            SourceMode.TERMINAL -> ErrorClassifier.detectTerminal(sampleOutput.trim(), exitCode)
        }
    }

    private fun isTargetSupported(sourceMode: SourceMode, target: AlertSettings.MatchTarget): Boolean {
        return when (sourceMode) {
            SourceMode.RUN_DEBUG -> true
            SourceMode.CONSOLE -> target == AlertSettings.MatchTarget.LINE_TEXT
            SourceMode.TERMINAL -> target == AlertSettings.MatchTarget.EXIT_CODE_AND_TEXT
        }
    }

    private fun PreparedRule.toRuleMatch(matchedContext: String): RuleMatch =
        RuleMatch(
            rowNumber = rowNumber,
            id = id,
            pattern = pattern,
            target = target,
            kind = kind,
            matchedContext = matchedContext,
        )

    private fun String.abbreviateForRuleTest(maxLength: Int = 140): String {
        val singleLine = replace('\n', ' ').replace('\r', ' ')
        return if (singleLine.length <= maxLength) singleLine else singleLine.take(maxLength - 3) + "..."
    }
}
