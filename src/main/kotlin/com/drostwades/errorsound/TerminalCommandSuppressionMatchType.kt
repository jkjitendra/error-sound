package com.drostwades.errorsound

enum class TerminalCommandSuppressionMatchType(val displayName: String) {
    EXACT_COMMAND("Exact command"),
    COMMAND_CONTAINS("Command contains"),
    COMMAND_REGEX("Command regex");

    override fun toString(): String = displayName

    companion object {
        val default: TerminalCommandSuppressionMatchType = COMMAND_CONTAINS

        fun fromStored(value: String?): TerminalCommandSuppressionMatchType =
            entries.firstOrNull { it.name == value } ?: default
    }
}
