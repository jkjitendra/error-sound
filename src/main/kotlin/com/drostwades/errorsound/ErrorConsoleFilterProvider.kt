package com.drostwades.errorsound

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project

/**
 * Registered as a consoleFilterProvider extension point.
 * IntelliJ routes every line printed to any ConsoleView through these filters —
 * this covers run configurations, test output, Gradle, and terminal output.
 */
class ErrorConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> =
        arrayOf(ErrorDetectionFilter(project))
}

private class ErrorDetectionFilter(private val project: Project) : Filter {

    // Patterns that reliably indicate an error in console output
    private val errorPattern = Regex(
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

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val settings = AlertSettings.getInstance()
        val engine = settings.getCompiledRuleEngine()

        // Custom LINE_TEXT rules are checked first on this hot path.
        // FULL_OUTPUT and EXIT_CODE_AND_TEXT are not supported in the console filter path.
        val customKind = if (engine.hasLineTextRules) engine.matchLineText(line) else null
        if (customKind == null && !errorPattern.containsMatchIn(line)) return null

        val errorKind = customKind ?: ErrorClassifier.detect(line, 1)

        // Key: project identity + error kind — stable across many console lines from the same project
        val key = "console:${project.locationHash}:$errorKind"
        AlertDispatcher.tryAlert(key, settings.state, errorKind, project)

        // Return null — we only want the sound side-effect, not to modify the line
        return null
    }
}
