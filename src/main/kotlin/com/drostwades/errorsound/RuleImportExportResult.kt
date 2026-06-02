package com.drostwades.errorsound

data class RuleImportExportResult(
    val customRules: List<AlertSettings.CustomRuleState>,
    val suppressionRules: List<AlertSettings.SuppressionRuleState>,
    val terminalCommandSuppressions: List<AlertSettings.TerminalCommandSuppressionState>,
    val exitCodeRules: List<AlertSettings.ExitCodeRuleState>,
    val warnings: List<String>,
    val skippedCount: Int,
)
