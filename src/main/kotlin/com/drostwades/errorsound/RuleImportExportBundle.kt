package com.drostwades.errorsound

data class RuleImportExportBundle(
    val schemaVersion: Int,
    val exportedAt: String,
    val pluginVersion: String,
    val customRules: List<CustomRule>,
    val suppressionRules: List<SuppressionRule>,
    val terminalCommandFilterMode: String,
    val terminalCommandFilters: List<TerminalCommandFilter>,
    val terminalCommandSuppressions: List<TerminalCommandSuppression>,
    val exitCodeRules: List<ExitCodeRule>,
) {
    data class CustomRule(
        val id: String,
        val enabled: Boolean,
        val pattern: String,
        val matchTarget: String,
        val kind: String,
    )

    data class SuppressionRule(
        val id: String,
        val enabled: Boolean,
        val pattern: String,
        val matchTarget: String,
        val description: String,
    )

    data class TerminalCommandFilter(
        val id: String,
        val enabled: Boolean,
        val matchType: String,
        val pattern: String,
        val description: String,
    )

    data class TerminalCommandSuppression(
        val id: String,
        val enabled: Boolean,
        val matchType: String,
        val pattern: String,
        val exitCodeMode: String,
        val exitCode: Int,
        val description: String,
    )

    data class ExitCodeRule(
        val exitCode: Int,
        val enabled: Boolean,
        val kind: String,
        val soundId: String?,
        val suppress: Boolean,
    )
}
