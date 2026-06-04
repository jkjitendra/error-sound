package com.drostwades.errorsound

enum class TerminalCommandFilterMatchType(val displayName: String) {
    EXACT_COMMAND("Exact command"),
    COMMAND_CONTAINS("Command contains"),
    COMMAND_REGEX("Command regex");

    override fun toString(): String = displayName

    companion object {
        val default: TerminalCommandFilterMatchType = COMMAND_CONTAINS

        fun fromStored(value: String?): TerminalCommandFilterMatchType =
            entries.firstOrNull { it.name == value } ?: default
    }
}
