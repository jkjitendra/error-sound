package com.drostwades.errorsound

data class RulePresetBundle(
    val id: String,
    val displayName: String,
    val description: String,
    val customRules: List<AlertSettings.CustomRuleState>,
    val exitCodeRules: List<AlertSettings.ExitCodeRuleState>,
) {
    override fun toString(): String = displayName
}
