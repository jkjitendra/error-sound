package com.drostwades.errorsound

enum class TerminalCommandFilterMode(val displayName: String) {
    OFF("Off / Monitor all terminal commands"),
    ALLOWLIST_ONLY("Allowlist only"),
    BLOCKLIST("Blocklist");

    override fun toString(): String = displayName

    companion object {
        val default: TerminalCommandFilterMode = OFF

        fun fromStored(value: String?): TerminalCommandFilterMode =
            entries.firstOrNull { it.name == value } ?: default
    }
}
