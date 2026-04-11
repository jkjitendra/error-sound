package com.drostwades.errorsound

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.atomic.AtomicReference

class AlertOnErrorExecutionListener : ExecutionListener {

    private val log = Logger.getInstance(AlertOnErrorExecutionListener::class.java)
    private val maxCapturedOutputChars = 1_000_000

    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val startedAtMillis = System.currentTimeMillis()
        val outputBuffer = StringBuilder()

        // Custom LINE_TEXT matches are tracked separately so built-in chunk detection
        // (which uses a priority ladder) cannot overwrite a custom match that landed first.
        val customDetectedKind = AtomicReference(ErrorKind.NONE)
        // Built-in chunk matches accumulate via priority — only used if no custom match wins.
        val builtInDetectedKind = AtomicReference(ErrorKind.NONE)

        val handlerKey = System.identityHashCode(handler)
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
                val text = event.text ?: return
                // Capture all available output keys; some runners use non-STDOUT/STDERR keys.
                outputBuffer.append(text)
                if (outputBuffer.length > maxCapturedOutputChars) {
                    outputBuffer.delete(0, outputBuffer.length - maxCapturedOutputChars)
                }

                val engine = AlertSettings.getInstance().getCompiledRuleEngine()
                val customKind = if (engine.hasLineTextRules) engine.matchLineText(text) else null
                if (customKind != null) {
                    // Custom rule matched: record it in the custom accumulator.
                    // We do NOT also run built-in on this chunk — custom wins for this chunk.
                    updateDetectedKind(customDetectedKind, customKind)
                } else {
                    // No custom match: run built-in classification and accumulate normally.
                    val builtInKind = ErrorClassifier.detect(text, 0)
                    if (builtInKind != ErrorKind.NONE) {
                        updateDetectedKind(builtInDetectedKind, builtInKind)
                    }
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                val exitCode = event.exitCode
                val settings = AlertSettings.getInstance()
                val engine = settings.getCompiledRuleEngine()
                val fullText = outputBuffer.toString()

                // Priority order (custom always beats built-in):
                // 1. Custom FULL_OUTPUT rule on full buffer
                // 2. Custom EXIT_CODE_AND_TEXT rule on full buffer + exit code
                // 3. Custom LINE_TEXT chunk accumulation
                // 4. Built-in chunk accumulation (highest-priority kind seen in chunks)
                // 5. Built-in full-buffer classification
                val customFinalKind =
                    (if (engine.hasFullOutputRules) engine.matchFullOutput(fullText) else null)
                        ?: (if (engine.hasExitCodeAndTextRules) engine.matchExitCodeAndText(fullText, exitCode) else null)
                        ?: customDetectedKind.get().takeIf { it != ErrorKind.NONE }

                var errorKind = if (customFinalKind != null) {
                    customFinalKind
                } else {
                    val bufferedKind = ErrorClassifier.detect(fullText, exitCode)
                    if (builtInDetectedKind.get() != ErrorKind.NONE) builtInDetectedKind.get() else bufferedKind
                }

                // Success: no error detected and clean exit → convert to SUCCESS
                if (errorKind == ErrorKind.NONE && exitCode == 0) {
                    errorKind = ErrorKind.SUCCESS
                }

                if (errorKind == ErrorKind.NONE) {
                    return
                }

                // Phase 7: use resolved effective settings so the per-project enabled override
                // is respected. All other settings (sounds, per-kind flags, …) come from global.
                val settingsState = ResolvedSettingsResolver.getInstance(env.project).resolve()

                // Duration threshold — only applies to Run/Debug path (console and terminal excluded)
                val elapsedMillis = System.currentTimeMillis() - startedAtMillis
                val thresholdMillis = settingsState.minProcessDurationSeconds * 1_000L
                if (elapsedMillis < thresholdMillis) {
                    log.debug(
                        "Alert suppressed by duration threshold: elapsed=${elapsedMillis}ms, threshold=${thresholdMillis}ms, kind=$errorKind"
                    )
                    return
                }

                log.debug(
                    "Process detected. executorId=$executorId, profile=${env.runProfile.name}, exitCode=$exitCode, kind=$errorKind, elapsed=${elapsedMillis}ms"
                )
                // Key is stable per run: one handler instance per run configuration launch
                val key = "exec:$handlerKey:$errorKind"
                AlertDispatcher.tryAlert(key, settingsState, errorKind, env.project)
            }
        })
    }

    private fun updateDetectedKind(kindRef: AtomicReference<ErrorKind>, candidate: ErrorKind) {
        while (true) {
            val current = kindRef.get()
            if (priority(candidate) <= priority(current)) {
                return
            }
            if (kindRef.compareAndSet(current, candidate)) {
                return
            }
        }
    }

    private fun priority(kind: ErrorKind): Int {
        return when (kind) {
            ErrorKind.NONE -> 0
            ErrorKind.SUCCESS -> 0  // SUCCESS never enters chunk-priority; branch for exhaustiveness
            ErrorKind.GENERIC -> 1
            ErrorKind.EXCEPTION -> 2
            ErrorKind.NETWORK -> 3
            ErrorKind.TEST_FAILURE -> 4
            ErrorKind.COMPILATION -> 5
            ErrorKind.CONFIGURATION -> 6
        }
    }
}
