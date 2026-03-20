package com.drostwades.errorsound

enum class ErrorKind {
    NONE,
    CONFIGURATION,
    COMPILATION,
    TEST_FAILURE,
    NETWORK,
    EXCEPTION,
    GENERIC,
    SUCCESS,
}

object ErrorClassifier {
    fun detect(outputText: String, exitCode: Int): ErrorKind {
        val text = outputText.lowercase()

        if (text.contains("could not resolve placeholder") ||
            text.contains("failed to load property source") ||
            text.contains("beancreationexception") ||
            text.contains("illegalstateexception")
        ) {
            return ErrorKind.CONFIGURATION
        }

        if (text.contains("compilation failed") ||
            text.contains("cannot find symbol") ||
            text.contains("error:") ||
            text.contains("kotlin:") && text.contains("error")
        ) {
            return ErrorKind.COMPILATION
        }

        if (text.contains("tests failed") ||
            text.contains("there were failing tests") ||
            text.contains("assertionerror")
        ) {
            return ErrorKind.TEST_FAILURE
        }

        if (text.contains("connection refused") ||
            text.contains("connect timed out") ||
            text.contains("unknownhostexception") ||
            text.contains("sockettimeoutexception")
        ) {
            return ErrorKind.NETWORK
        }

        if (text.contains("exception") ||
            text.contains("caused by:") ||
            text.contains("stacktrace")
        ) {
            return ErrorKind.EXCEPTION
        }

        if (exitCode != 0 ||
            text.contains("failed") ||
            text.contains("error")
        ) {
            return ErrorKind.GENERIC
        }

        return ErrorKind.NONE
    }

    @Suppress("UNUSED_PARAMETER")
    fun detectTerminal(command: String, exitCode: Int): ErrorKind {
        // Exit code 0 = success, no alert
        if (exitCode == 0) return ErrorKind.NONE
        // Exit code 127 = "command not found" in bash/zsh
        // Any other non-zero exit = generic shell error
        return ErrorKind.GENERIC
    }
}
