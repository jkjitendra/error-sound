package com.drostwades.errorsound

enum class TerminalCommandSuppressionExitCodeMode(val displayName: String) {
    ANY_NON_ZERO("Any non-zero exit code"),
    SPECIFIC_EXIT_CODE("Specific exit code");

    override fun toString(): String = displayName

    companion object {
        val default: TerminalCommandSuppressionExitCodeMode = ANY_NON_ZERO

        fun fromStored(value: String?): TerminalCommandSuppressionExitCodeMode =
            entries.firstOrNull { it.name == value } ?: default
    }
}
