package com.drostwades.errorsound

data class RepoProfileLoadResult(
    val status: Status,
    val path: String? = null,
    val profile: RepoProfileState? = null,
    val warnings: List<String> = emptyList(),
) {
    enum class Status {
        NO_PROJECT_BASE_PATH,
        ABSENT,
        LOADED,
        DISABLED,
        INVALID,
    }

    val isFilePresent: Boolean
        get() = status == Status.LOADED || status == Status.DISABLED || status == Status.INVALID

    val isApplied: Boolean
        get() = status == Status.LOADED && profile?.enabled == true
}
