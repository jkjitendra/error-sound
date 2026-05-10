package com.drostwades.errorsound

object RulePresetService {

    val bundles: List<RulePresetBundle> = listOf(
        RulePresetBundle(
            id = "java-spring-boot",
            displayName = "Java / Spring Boot",
            description = "Spring startup and configuration failures such as bean creation, placeholders, and ApplicationContext errors.",
            customRules = listOf(
                customRule("preset.java.spring.beancreation", "BeanCreationException", ErrorKind.CONFIGURATION),
                customRule("preset.java.spring.placeholder", "Could not resolve placeholder", ErrorKind.CONFIGURATION),
                customRule("preset.java.spring.applicationcontext", "ApplicationContext.*failed to start|Failed to start.*ApplicationContext", ErrorKind.CONFIGURATION),
                customRule("preset.java.spring.illegalstate", "IllegalStateException.*(startup|ApplicationContext|Failed to load)", ErrorKind.CONFIGURATION),
            ),
            exitCodeRules = commonExitCodeRules(),
        ),
        RulePresetBundle(
            id = "gradle-maven",
            displayName = "Gradle / Maven",
            description = "Build failures, task execution failures, compilation failures, and failing test summaries.",
            customRules = listOf(
                customRule("preset.gradle.buildfailed", "BUILD FAILED", ErrorKind.COMPILATION),
                customRule("preset.gradle.taskfailed", "Execution failed for task", ErrorKind.COMPILATION),
                customRule("preset.gradle.compilationfailed", "Compilation failed|compilation failed", ErrorKind.COMPILATION),
                customRule("preset.gradle.testsfailed", "There were failing tests", ErrorKind.TEST_FAILURE),
                customRule("preset.maven.buildfailure", "BUILD FAILURE", ErrorKind.GENERIC),
            ),
            exitCodeRules = commonExitCodeRules(),
        ),
        RulePresetBundle(
            id = "node-package-managers",
            displayName = "Node.js / npm / pnpm",
            description = "Package-manager errors, missing modules, TypeScript diagnostics, and local port conflicts.",
            customRules = listOf(
                customRule("preset.node.npm.err", "npm ERR!", ErrorKind.GENERIC),
                customRule("preset.node.pnpm.err", "pnpm ERR_\\w+", ErrorKind.GENERIC),
                customRule("preset.node.module.notfound", "Module not found|Cannot find module", ErrorKind.COMPILATION),
                customRule("preset.node.typescript.error", "TS\\d{4}", ErrorKind.COMPILATION),
                customRule("preset.node.eaddrinuse", "EADDRINUSE", ErrorKind.NETWORK),
            ),
            exitCodeRules = commonExitCodeRules(),
        ),
        RulePresetBundle(
            id = "python-pytest",
            displayName = "Python / pytest",
            description = "Python import problems, tracebacks, pytest failures, and assertion failures.",
            customRules = listOf(
                customRule("preset.python.traceback", "Traceback \\(most recent call last\\)", ErrorKind.EXCEPTION),
                customRule("preset.python.modulenotfound", "ModuleNotFoundError", ErrorKind.CONFIGURATION),
                customRule("preset.python.importerror", "ImportError", ErrorKind.CONFIGURATION),
                customRule("preset.python.pytest.failed", "pytest.*failed|failed tests", ErrorKind.TEST_FAILURE),
                customRule("preset.python.assertionerror", "AssertionError", ErrorKind.TEST_FAILURE),
            ),
            exitCodeRules = commonExitCodeRules(),
        ),
        RulePresetBundle(
            id = "docker-kubernetes",
            displayName = "Docker / Kubernetes",
            description = "Docker daemon/build errors and common Kubernetes pod failure states.",
            customRules = listOf(
                customRule("preset.docker.buildfailed", "docker build.*failed|failed to solve", ErrorKind.GENERIC),
                customRule("preset.docker.daemon", "Error response from daemon", ErrorKind.GENERIC),
                customRule("preset.kubernetes.imagepullbackoff", "ImagePullBackOff", ErrorKind.NETWORK),
                customRule("preset.kubernetes.crashloopbackoff", "CrashLoopBackOff", ErrorKind.GENERIC),
                customRule("preset.kubernetes.oomkilled", "OOMKilled", ErrorKind.GENERIC),
            ),
            exitCodeRules = commonExitCodeRules(),
        ),
        RulePresetBundle(
            id = "frontend-test-runners",
            displayName = "Frontend test runners",
            description = "Jest, Vitest, Cypress, Playwright, webpack, and Vite failure summaries.",
            customRules = listOf(
                customRule("preset.frontend.jest.failed", "Jest.*failed|FAIL\\s+.*\\.test\\.", ErrorKind.TEST_FAILURE),
                customRule("preset.frontend.vitest.failed", "Vitest.*failed|Test Files.*failed", ErrorKind.TEST_FAILURE),
                customRule("preset.frontend.cypress.failed", "Cypress.*failed", ErrorKind.TEST_FAILURE),
                customRule("preset.frontend.playwright.failed", "Playwright.*failed|\\bchromium\\b.*failed", ErrorKind.TEST_FAILURE),
                customRule("preset.frontend.webpack.failed", "webpack.*compilation failed|Compilation failed", ErrorKind.COMPILATION),
                customRule("preset.frontend.vite.failed", "vite.*build failed|Build failed with", ErrorKind.COMPILATION),
            ),
            exitCodeRules = commonExitCodeRules(),
        ),
    )

    fun prepareApply(
        preset: RulePresetBundle,
        currentCustomRules: List<AlertSettings.CustomRuleState>,
        currentExitCodeRules: List<AlertSettings.ExitCodeRuleState>,
    ): RulePresetApplyResult {
        val existingCustomIds = currentCustomRules.map { it.id }.toMutableSet()
        val existingExitCodes = currentExitCodeRules.map { it.exitCode }.toMutableSet()
        val customRulesToAdd = mutableListOf<AlertSettings.CustomRuleState>()
        val exitCodeRulesToAdd = mutableListOf<AlertSettings.ExitCodeRuleState>()
        val skippedCustomIds = mutableListOf<String>()
        val skippedExitCodes = mutableListOf<Int>()
        val warnings = mutableListOf<String>()

        for (rule in preset.customRules) {
            if (rule.id in existingCustomIds) {
                skippedCustomIds += rule.id
                continue
            }
            if (currentCustomRules.size + customRulesToAdd.size >= CustomRuleEngine.MAX_RULES) {
                warnings += "Skipped custom rule '${rule.id}' because only ${CustomRuleEngine.MAX_RULES} custom rules are supported."
                continue
            }
            existingCustomIds += rule.id
            customRulesToAdd += normalizeCustomRule(rule)
        }

        for (rule in preset.exitCodeRules) {
            if (rule.exitCode in existingExitCodes) {
                skippedExitCodes += rule.exitCode
                continue
            }
            existingExitCodes += rule.exitCode
            exitCodeRulesToAdd += normalizeExitCodeRule(rule)
        }

        return RulePresetApplyResult(
            preset = preset,
            customRulesToAdd = customRulesToAdd,
            exitCodeRulesToAdd = exitCodeRulesToAdd,
            skippedDuplicateCustomRuleIds = skippedCustomIds,
            skippedDuplicateExitCodes = skippedExitCodes,
            warnings = warnings,
        )
    }

    private fun customRule(
        id: String,
        pattern: String,
        kind: ErrorKind,
        matchTarget: AlertSettings.MatchTarget = AlertSettings.MatchTarget.LINE_TEXT,
    ): AlertSettings.CustomRuleState {
        require(kind in CustomRuleEngine.ALLOWED_CUSTOM_RULE_KINDS) { "Unsupported preset kind: $kind" }
        return AlertSettings.CustomRuleState(
            id = id,
            enabled = true,
            pattern = pattern.take(CustomRuleEngine.MAX_PATTERN_LENGTH),
            matchTarget = matchTarget.name,
            kind = kind.name,
        )
    }

    private fun commonExitCodeRules(): List<AlertSettings.ExitCodeRuleState> = listOf(
        exitCodeRule(exitCode = 127, suppress = false),
        exitCodeRule(exitCode = 130, suppress = true),
        exitCodeRule(exitCode = 137, suppress = false),
        exitCodeRule(exitCode = 143, suppress = false),
    )

    private fun exitCodeRule(exitCode: Int, suppress: Boolean): AlertSettings.ExitCodeRuleState =
        AlertSettings.ExitCodeRuleState(
            exitCode = exitCode,
            enabled = true,
            kind = ErrorKind.GENERIC.name,
            soundId = null,
            suppress = suppress,
        )

    private fun normalizeCustomRule(rule: AlertSettings.CustomRuleState): AlertSettings.CustomRuleState {
        val matchTarget = AlertSettings.MatchTarget.entries.find { it.name == rule.matchTarget }
            ?: AlertSettings.MatchTarget.LINE_TEXT
        val kind = ErrorKind.entries.find { it.name == rule.kind }
            ?.takeIf { it in CustomRuleEngine.ALLOWED_CUSTOM_RULE_KINDS }
            ?: ErrorKind.GENERIC
        return rule.copy(
            pattern = rule.pattern.trim().take(CustomRuleEngine.MAX_PATTERN_LENGTH),
            matchTarget = matchTarget.name,
            kind = kind.name,
        )
    }

    private fun normalizeExitCodeRule(rule: AlertSettings.ExitCodeRuleState): AlertSettings.ExitCodeRuleState {
        val kind = ErrorKind.entries.find { it.name == rule.kind }
            ?.takeIf { it in CustomRuleEngine.ALLOWED_CUSTOM_RULE_KINDS }
            ?: ErrorKind.GENERIC
        return rule.copy(
            kind = kind.name,
            soundId = rule.soundId
                ?.takeIf { it.isNotBlank() && it != BuiltInSounds.CUSTOM_FILE_ID }
                ?.let { soundId -> BuiltInSounds.all.find { it.id == soundId }?.id },
        )
    }
}
