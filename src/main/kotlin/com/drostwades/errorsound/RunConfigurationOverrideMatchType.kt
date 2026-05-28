package com.drostwades.errorsound

enum class RunConfigurationOverrideMatchType(val displayName: String) {
    EXACT_NAME("Exact configuration name"),
    NAME_CONTAINS("Configuration name contains"),
    NAME_REGEX("Configuration name regex"),
    TYPE_CONTAINS("Configuration type id/name contains");

    override fun toString(): String = displayName

    companion object {
        val default: RunConfigurationOverrideMatchType = EXACT_NAME

        fun fromStored(value: String?): RunConfigurationOverrideMatchType =
            entries.firstOrNull { it.name == value } ?: default
    }
}
