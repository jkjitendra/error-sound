package com.drostwades.errorsound

data class RulePresetApplyResult(
    val preset: RulePresetBundle,
    val customRulesToAdd: List<AlertSettings.CustomRuleState>,
    val exitCodeRulesToAdd: List<AlertSettings.ExitCodeRuleState>,
    val skippedDuplicateCustomRuleIds: List<String>,
    val skippedDuplicateExitCodes: List<Int>,
    val warnings: List<String>,
) {
    val addedCustomRuleCount: Int
        get() = customRulesToAdd.size

    val addedExitCodeRuleCount: Int
        get() = exitCodeRulesToAdd.size

    val skippedDuplicateCount: Int
        get() = skippedDuplicateCustomRuleIds.size + skippedDuplicateExitCodes.size
}
