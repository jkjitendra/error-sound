package com.drostwades.errorsound

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

@Service(Service.Level.APP)
class AlertHistoryService {

    data class Entry(
        val timestampMillis: Long,
        val projectName: String?,
        val source: AlertMatchExplanation.Source?,
        val kind: ErrorKind,
        val cause: AlertMatchExplanation.Cause?,
        val ruleId: String?,
        val rulePattern: String?,
        val exitCode: Int?,
        val commandOrConfig: String?,
        val soundOverrideUsed: Boolean,
    )

    fun interface Listener {
        fun historyChanged()
    }

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun record(
        project: Project?,
        kind: ErrorKind,
        soundOverride: String?,
        explanation: AlertMatchExplanation?,
    ) {
        entries.addFirst(
            Entry(
                timestampMillis = System.currentTimeMillis(),
                projectName = project?.name,
                source = explanation?.source,
                kind = kind,
                cause = explanation?.cause,
                ruleId = explanation?.ruleId,
                rulePattern = explanation?.rulePattern,
                exitCode = explanation?.exitCode,
                commandOrConfig = explanation?.commandOrConfig,
                soundOverrideUsed = !soundOverride.isNullOrBlank() || !explanation?.soundOverride.isNullOrBlank(),
            )
        )
        while (entries.size > MAX_ENTRIES) {
            entries.removeLast()
        }
        publishChanged()
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() {
        if (entries.isEmpty()) return
        entries.clear()
        publishChanged()
    }

    private fun publishChanged() {
        ApplicationManager.getApplication()
            .messageBus
            .syncPublisher(TOPIC)
            .historyChanged()
    }

    companion object {
        const val MAX_ENTRIES: Int = 100

        val TOPIC: Topic<Listener> = Topic.create(
            "Error Sound Alert History",
            Listener::class.java,
        )

        fun getInstance(): AlertHistoryService =
            ApplicationManager.getApplication().getService(AlertHistoryService::class.java)
    }
}
