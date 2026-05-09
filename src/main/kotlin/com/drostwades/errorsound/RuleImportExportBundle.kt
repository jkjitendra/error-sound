package com.drostwades.errorsound

data class RuleImportExportBundle(
    val schemaVersion: Int,
    val exportedAt: String,
    val pluginVersion: String,
    val customRules: List<CustomRule>,
    val exitCodeRules: List<ExitCodeRule>,
) {
    data class CustomRule(
        val id: String,
        val enabled: Boolean,
        val pattern: String,
        val matchTarget: String,
        val kind: String,
    )

    data class ExitCodeRule(
        val exitCode: Int,
        val enabled: Boolean,
        val kind: String,
        val soundId: String?,
        val suppress: Boolean,
    )
}
