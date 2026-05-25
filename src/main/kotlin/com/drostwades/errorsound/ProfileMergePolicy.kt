package com.drostwades.errorsound

enum class ProfileMergePolicy(
    val displayName: String,
    val effectivePrecedenceText: String,
) {
    STANDARD_WORKSPACE_WINS(
        displayName = "Standard: workspace project overrides repo profile",
        effectivePrecedenceText = "Global -> repo profile -> workspace project profile",
    ),
    IGNORE_REPO_PROFILE(
        displayName = "Ignore repo profile",
        effectivePrecedenceText = "Global -> workspace project profile",
    ),
    REPO_PROFILE_WINS(
        displayName = "Repo profile overrides workspace project",
        effectivePrecedenceText = "Global -> workspace project profile -> repo profile",
    ),
    GLOBAL_ONLY(
        displayName = "Global settings only",
        effectivePrecedenceText = "Global settings only",
    );

    override fun toString(): String = displayName

    companion object {
        val default: ProfileMergePolicy = STANDARD_WORKSPACE_WINS

        fun fromStored(value: String?): ProfileMergePolicy =
            entries.firstOrNull { it.name == value } ?: default
    }
}
