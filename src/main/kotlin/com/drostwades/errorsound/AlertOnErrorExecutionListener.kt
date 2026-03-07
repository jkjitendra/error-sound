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
        val outputBuffer = StringBuilder()
        val detectedKind = AtomicReference(ErrorKind.NONE)
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
                val text = event.text ?: return
                // Capture all available output keys; some runners use non-STDOUT/STDERR keys.
                outputBuffer.append(text)
                if (outputBuffer.length > maxCapturedOutputChars) {
                    outputBuffer.delete(0, outputBuffer.length - maxCapturedOutputChars)
                }

                val chunkKind = ErrorClassifier.detect(text, 0)
                if (chunkKind != ErrorKind.NONE) {
                    updateDetectedKind(detectedKind, chunkKind)
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                val exitCode = event.exitCode
                val bufferedKind = ErrorClassifier.detect(outputBuffer.toString(), exitCode)
                val errorKind = if (detectedKind.get() != ErrorKind.NONE) detectedKind.get() else bufferedKind
                if (errorKind == ErrorKind.NONE) {
                    return
                }

                val settings = AlertSettings.getInstance().state
                if (!AlertMonitoring.shouldMonitor(settings, errorKind)) {
                    return
                }

                log.debug(
                    "Error process detected. executorId=$executorId, profile=${env.runProfile.name}, exitCode=$exitCode, kind=$errorKind"
                )
                ErrorSoundPlayer.play(settings, errorKind)
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
            ErrorKind.GENERIC -> 1
            ErrorKind.EXCEPTION -> 2
            ErrorKind.NETWORK -> 3
            ErrorKind.TEST_FAILURE -> 4
            ErrorKind.COMPILATION -> 5
            ErrorKind.CONFIGURATION -> 6
        }
    }
}
