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
        arrayOf(ErrorDetectionFilter())
}

private class ErrorDetectionFilter : Filter {

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
        val settings = AlertSettings.getInstance().state
        if (!errorPattern.containsMatchIn(line)) return null

        val errorKind = ErrorClassifier.detect(line, 1)
        if (!AlertMonitoring.shouldMonitor(settings, errorKind)) return null

        ErrorSoundPlayer.play(settings, errorKind)
        // Return null — we only want the sound side-effect, not to modify the line
        return null
    }
}
