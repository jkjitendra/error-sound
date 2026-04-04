package com.drostwades.errorsound

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Listens to IntelliJ terminal command completions and plays a sound on errors.
 *
 * Uses ONLY reflection for terminal plugin classes — zero direct imports from the
 * terminal plugin — so the class loads and compiles against IntelliJ 2024.3 while
 * supporting both Classic and Reworked 2025 terminal engines at runtime.
 *
 * Entry point: ToolWindowManagerListener fires when the Terminal tool window is shown,
 * guaranteeing the terminal infrastructure is fully initialized before we attach.
 */
class AlertOnTerminalCommandListener : ProjectActivity {

    private val log = Logger.getInstance(AlertOnTerminalCommandListener::class.java)

    /** Tracks views already hooked (by identity hash) to prevent double-attachment */
    private val hookedViews = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()
    private val completionHookRegistered = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    /** Whether we've already registered the tabs-manager listener (avoid duplicates) */
    private val reworkedListenerRegistered = AtomicBoolean(false)
    private val blockListenerRegistered = AtomicBoolean(false)

    override suspend fun execute(project: Project) {
        log.debug("ErrorSound: terminal listener ProjectActivity starting")

        val proxy = buildListenerProxy(project)
        if (proxy == null) {
            log.warn("ErrorSound: NO listener interfaces found — terminal sound alerts disabled")
            return
        }
        log.debug("ErrorSound: listener proxy built OK")

        // Listen for terminal tool window activation — this fires when the user opens
        // the terminal, guaranteeing the terminal infrastructure is ready.
        project.messageBus.connect(project as Disposable).subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowShown(toolWindow: com.intellij.openapi.wm.ToolWindow) {
                    if (toolWindow.id == "Terminal") {
                        log.debug("ErrorSound: Terminal tool window shown, attaching listeners")
                        attachAll(project, proxy)
                    }
                }
            }
        )
        log.debug("ErrorSound: registered ToolWindowManagerListener for Terminal show events")

        // Also try immediately in case the terminal is already open at startup
        attachAll(project, proxy)
    }

    private fun attachAll(project: Project, proxy: Any) {
        try { attachBlockTerminal(project, proxy) } catch (e: Throwable) {
            log.warn("ErrorSound: Block-terminal attach failed: ${e.javaClass.simpleName}: ${e.message}")
        }
        try { attachReworkedTerminal(project, proxy) } catch (e: Throwable) {
            log.warn("ErrorSound: Reworked-terminal attach failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ── Strategy A: Block / Classic terminal ──────────────────────────────────

    private fun attachBlockTerminal(project: Project, proxy: Any) {
        val managerClass = tryLoad(
            "org.jetbrains.plugins.terminal.TerminalToolWindowManager"
        )
        if (managerClass == null) {
            log.debug("ErrorSound: [Block] TerminalToolWindowManager class NOT found — classic terminal not present")
            return
        }

        val manager = managerClass.getMethod("getInstance", Project::class.java)
            .invoke(null, project)
        if (manager == null) {
            log.warn("ErrorSound: [Block] getInstance returned null")
            return
        }

        // Register setup handler for future tabs (once only)
        if (blockListenerRegistered.compareAndSet(false, true)) {
            val consumer = java.util.function.Consumer<Any> { widget ->
                log.debug("ErrorSound: [Block] setupHandler fired for widget: ${widget.javaClass.name}")
                tryAttachBlockWidget(widget, project, proxy)
            }
            manager.javaClass.getMethod(
                "addNewTerminalSetupHandler",
                java.util.function.Consumer::class.java,
                Disposable::class.java
            ).invoke(manager, consumer, project as Disposable)
            log.debug("ErrorSound: [Block] registered setup handler")
        }

        // Hook already-open terminal widgets
        try {
            @Suppress("UNCHECKED_CAST")
            val widgets = manager.javaClass.getMethod("getTerminalWidgets")
                .invoke(manager) as? Set<Any?>
            log.debug("ErrorSound: [Block] existing widgets: ${widgets?.size ?: "null"}")
            widgets?.forEach { w ->
                if (w != null) tryAttachBlockWidget(w, project, proxy)
            }
        } catch (e: Throwable) {
            log.warn("ErrorSound: [Block] getTerminalWidgets failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun tryAttachBlockWidget(widget: Any, project: Project, proxy: Any) {
        if (tryAttachBlockWidgetNow(widget, project, proxy)) return

        try {
            @Suppress("UNCHECKED_CAST")
            val future = widget.javaClass
                .getMethod("getTerminalSizeInitializedFuture")
                .invoke(widget) as? CompletableFuture<*>
            future?.thenAccept { _ ->
                tryAttachBlockWidgetNow(widget, project, proxy)
            }
        } catch (e: Throwable) {
            log.warn("ErrorSound: [Block] getTerminalSizeInitializedFuture failed: ${e.javaClass.simpleName}")
        }
    }

    private fun tryAttachBlockWidgetNow(widget: Any, project: Project, proxy: Any): Boolean {
        return try {
            val viewField = findField(widget.javaClass, "view") ?: return false
            viewField.isAccessible = true
            val view = viewField.get(widget) ?: return false

            val sessionField = findField(view.javaClass, "session") ?: return false
            sessionField.isAccessible = true
            val session = sessionField.get(view) ?: return false

            val addMethod = session.javaClass.methods.firstOrNull { m ->
                m.name == "addCommandListener" && m.parameterCount == 2
            } ?: return false

            val attached = try {
                addMethod.invoke(session, proxy, project as Disposable)
                true
            } catch (_: Throwable) {
                try {
                    addMethod.invoke(session, project as Disposable, proxy)
                    true
                } catch (e: Throwable) {
                    log.warn("ErrorSound: [Block] addCommandListener invocation failed: ${e.javaClass.simpleName}: ${e.message}")
                    false
                }
            }
            if (!attached) return false
            log.debug("ErrorSound: [Block] ATTACHED listener to ${widget.javaClass.simpleName}")
            true
        } catch (e: Throwable) {
            log.warn("ErrorSound: [Block] attach exception: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    // ── Strategy B: Reworked 2025 terminal ────────────────────────────────────

    private fun attachReworkedTerminal(project: Project, proxy: Any) {
        // Try loading TerminalToolWindowTabsManager (frontend content module)
        val tabsManagerClass = tryLoad(
            "com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager"
        )
        if (tabsManagerClass == null) {
            log.debug("ErrorSound: [Reworked] TerminalToolWindowTabsManager class NOT found — reworked terminal not present")
            return
        }

        // Get service instance — use project.getService() as primary, reflection getInstance() as fallback
        val tabsManager = getServiceInstance(project, tabsManagerClass)
        if (tabsManager == null) {
            log.warn("ErrorSound: [Reworked] could not get TerminalToolWindowTabsManager instance")
            return
        }
        log.debug("ErrorSound: [Reworked] tabsManager: ${tabsManager.javaClass.name}")

        // Register listener for future tabs (once only)
        if (reworkedListenerRegistered.compareAndSet(false, true)) {
            val listenerInterface = tryLoad(
                "com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener"
            )
            if (listenerInterface == null) {
                log.warn("ErrorSound: [Reworked] TerminalTabsManagerListener NOT found")
                reworkedListenerRegistered.set(false)
                return
            }

            val tabsListenerProxy = Proxy.newProxyInstance(
                listenerInterface.classLoader,
                arrayOf(listenerInterface)
            ) { _, method, args ->
                if (method.name == "tabAdded" && args?.size == 1) {
                    try {
                        val tab = args[0] ?: return@newProxyInstance null
                        log.debug("ErrorSound: [Reworked] tabAdded: ${tab.javaClass.name}")
                        val view = tab.javaClass.getMethod("getView").invoke(tab) ?: return@newProxyInstance null
                        log.debug("ErrorSound: [Reworked] tab view: ${view.javaClass.name}")
                        scheduleReworkedViewAttach(view, project, proxy)
                    } catch (e: Throwable) {
                        log.warn("ErrorSound: [Reworked] tabAdded error: ${e.javaClass.simpleName}: ${e.message}")
                    }
                }
                null
            }

            tabsManager.javaClass.getMethod("addListener", Disposable::class.java, listenerInterface)
                .invoke(tabsManager, project as Disposable, tabsListenerProxy)
            log.debug("ErrorSound: [Reworked] registered tabs listener")
        }

        // Hook already-open tabs
        try {
            @Suppress("UNCHECKED_CAST")
            val tabs = tabsManager.javaClass.getMethod("getTabs")
                .invoke(tabsManager) as? List<Any?>
            log.debug("ErrorSound: [Reworked] existing tabs: ${tabs?.size ?: "null"}")
            tabs?.forEach { tab ->
                if (tab != null) try {
                    val view = tab.javaClass.getMethod("getView").invoke(tab) ?: return@forEach
                    scheduleReworkedViewAttach(view, project, proxy)
                } catch (e: Throwable) {
                    log.warn("ErrorSound: [Reworked] existing tab error: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            log.warn("ErrorSound: [Reworked] getTabs error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Gets a service instance. Tries project.getService() first (most reliable),
     * then falls back to static getInstance(Project).
     */
    private fun getServiceInstance(project: Project, serviceClass: Class<*>): Any? {
        // Strategy 1: Direct service lookup
        try {
            val svc = project.getService(serviceClass)
            if (svc != null) {
                log.debug("ErrorSound: got service via project.getService(): ${svc.javaClass.name}")
                return svc
            }
        } catch (e: Throwable) {
            log.debug("ErrorSound: project.getService() failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Strategy 2: Static getInstance(Project) method
        try {
            val method = serviceClass.getMethod("getInstance", Project::class.java)
            val svc = method.invoke(null, project)
            if (svc != null) {
                log.debug("ErrorSound: got service via getInstance(): ${svc.javaClass.name}")
                return svc
            }
        } catch (e: Throwable) {
            log.debug("ErrorSound: getInstance() failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        return null
    }

    private fun scheduleReworkedViewAttach(view: Any, project: Project, proxy: Any) {
        registerReworkedCompletionHook(view, project, proxy)

        if (tryAttachReworkedViewNow(view, project, proxy)) {
            log.debug("ErrorSound: [Reworked] attached immediately to ${view.javaClass.simpleName}")
            return
        }

        val executor = com.intellij.util.concurrency.AppExecutorUtil
            .getAppScheduledExecutorService()
        val futureRef = AtomicReference<java.util.concurrent.ScheduledFuture<*>>()
        val attempts = AtomicInteger(0)
        val maxAttempts = 60 // 60 × 500ms = 30s

        val task = Runnable {
            val attempt = attempts.incrementAndGet()
            if (attempt > maxAttempts) {
                log.warn("ErrorSound: [Reworked] gave up attaching after $maxAttempts attempts for ${view.javaClass.simpleName}")
                futureRef.get()?.cancel(false)
                return@Runnable
            }
            if (tryAttachReworkedViewNow(view, project, proxy)) {
                log.debug("ErrorSound: [Reworked] attached on attempt $attempt")
                futureRef.get()?.cancel(false)
            }
        }
        futureRef.set(
            executor.scheduleWithFixedDelay(task, 500, 500, TimeUnit.MILLISECONDS)
        )
    }

    private fun registerReworkedCompletionHook(view: Any, project: Project, proxy: Any) {
        val key = System.identityHashCode(view)
        if (completionHookRegistered.putIfAbsent(key, true) != null) return

        try {
            val deferred = view.javaClass.getMethod("getShellIntegrationDeferred")
                .invoke(view) ?: return

            val invokeOnCompletion = deferred.javaClass.methods.firstOrNull { m ->
                m.name == "invokeOnCompletion" && m.parameterCount == 1
            }
            if (invokeOnCompletion == null) {
                log.warn("ErrorSound: [Reworked] invokeOnCompletion(handler) not found for ${deferred.javaClass.name}")
                return
            }

            val completionHandler = object : kotlin.jvm.functions.Function1<Throwable?, kotlin.Unit> {
                override fun invoke(cause: Throwable?): kotlin.Unit {
                    if (cause != null) {
                        log.warn("ErrorSound: [Reworked] shell integration deferred completed exceptionally: ${cause.javaClass.simpleName}: ${cause.message}")
                        return kotlin.Unit
                    }
                    if (tryAttachReworkedViewNow(view, project, proxy)) {
                        log.debug("ErrorSound: [Reworked] attached from deferred completion callback")
                    } else {
                        log.warn("ErrorSound: [Reworked] deferred completed but attach still failed")
                    }
                    return kotlin.Unit
                }
            }

            invokeOnCompletion.invoke(deferred, completionHandler)
            log.debug("ErrorSound: [Reworked] registered deferred completion hook for ${view.javaClass.simpleName}")
        } catch (e: Throwable) {
            log.warn("ErrorSound: [Reworked] register completion hook failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun tryAttachReworkedViewNow(view: Any, project: Project, proxy: Any): Boolean {
        val key = System.identityHashCode(view)
        if (hookedViews.containsKey(key)) return true

        return try {
            val shellIntegration = getShellIntegration(view)
            if (shellIntegration == null) return false

            log.debug("ErrorSound: [Reworked] shellIntegration: ${shellIntegration.javaClass.name}")

            // Find listener registration method — try several names
            val addMethod = shellIntegration.javaClass.methods.firstOrNull { m ->
                m.name == "addCommandExecutionListener" && m.parameterCount == 2
            } ?: shellIntegration.javaClass.methods.firstOrNull { m ->
                m.name.contains("addCommand", ignoreCase = true) &&
                    m.name.contains("Listener", ignoreCase = true) && m.parameterCount == 2
            }
            if (addMethod == null) {
                val relevant = shellIntegration.javaClass.methods
                    .filter { it.name.startsWith("add") || it.name.contains("listener", ignoreCase = true) }
                    .map { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
                log.warn("ErrorSound: [Reworked] addCommandExecutionListener NOT found. Listener methods: $relevant")
                log.warn("ErrorSound: [Reworked] All methods: ${shellIntegration.javaClass.methods.map { it.name }.distinct().sorted().joinToString()}")
                return false
            }

            log.debug("ErrorSound: [Reworked] ${addMethod.name} params: ${addMethod.parameterTypes.map { it.name }.joinToString()}")
            val attached = try {
                addMethod.invoke(shellIntegration, project as Disposable, proxy)
                true
            } catch (_: Throwable) {
                try {
                    addMethod.invoke(shellIntegration, proxy, project as Disposable)
                    true
                } catch (e: Throwable) {
                    log.warn("ErrorSound: [Reworked] ${addMethod.name} invocation failed: ${e.javaClass.simpleName}: ${e.message}")
                    false
                }
            }
            if (!attached) return false
            hookedViews[key] = true
            log.debug("ErrorSound: [Reworked] ATTACHED command execution listener")
            true
        } catch (e: Throwable) {
            log.warn("ErrorSound: [Reworked] tryAttach exception: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * Tries multiple strategies to obtain the shell integration object from a terminal view.
     */
    private fun getShellIntegration(view: Any): Any? {
        // Strategy 1: getShellIntegrationDeferred() → getCompleted()
        try {
            val deferred = view.javaClass.getMethod("getShellIntegrationDeferred").invoke(view)
            if (deferred != null) {
                try {
                    val result = deferred.javaClass.getMethod("getCompleted").invoke(deferred)
                    if (result != null) return result
                    log.debug("ErrorSound: [Reworked] getCompleted() returned null")
                } catch (e: Throwable) {
                    val cause = if (e is java.lang.reflect.InvocationTargetException) e.targetException else e
                    log.warn("ErrorSound: [Reworked] getCompleted() failed on ${deferred.javaClass.name}: ${cause.javaClass.simpleName}: ${cause.message}")
                }
            }
        } catch (_: Throwable) {}

        // Strategy 2: direct getShellIntegration() on the view
        for (methodName in listOf("getShellIntegration", "getTerminalShellIntegration")) {
            try {
                val result = view.javaClass.getMethod(methodName).invoke(view)
                if (result != null) {
                    log.debug("ErrorSound: [Reworked] got shell integration via view.$methodName()")
                    return result
                }
            } catch (_: Throwable) {}
        }

        // Strategy 3: search for any getter that returns a shell-integration-like type
        for (m in view.javaClass.methods) {
            if (m.parameterCount == 0 && m.name.startsWith("get") &&
                m.returnType.name.contains("ShellIntegration", ignoreCase = true) &&
                !m.returnType.name.contains("Deferred", ignoreCase = true)
            ) {
                try {
                    val result = m.invoke(view)
                    if (result != null) {
                        log.debug("ErrorSound: [Reworked] got shell integration via view.${m.name}()")
                        return result
                    }
                } catch (_: Throwable) {}
            }
        }

        // Strategy 4: field access
        for (fieldName in listOf("shellIntegration", "myShellIntegration", "_shellIntegration")) {
            val field = findField(view.javaClass, fieldName)
            if (field != null) {
                try {
                    field.isAccessible = true
                    val result = field.get(view)
                    if (result != null) {
                        log.debug("ErrorSound: [Reworked] got shell integration via field '$fieldName'")
                        return result
                    }
                } catch (_: Throwable) {}
            }
        }

        // Log diagnostics
        val viewMethods = view.javaClass.methods
            .filter { m -> m.parameterCount == 0 && (m.name.contains("shell", ignoreCase = true) || m.name.contains("integration", ignoreCase = true) || m.name.contains("command", ignoreCase = true)) }
            .map { "${it.name}() → ${it.returnType.simpleName}" }
        log.warn("ErrorSound: [Reworked] could not get shell integration. Relevant view methods: $viewMethods")
        return null
    }

    // ── Dynamic proxy listener ────────────────────────────────────────────────

    private fun buildListenerProxy(project: Project): Any? {
        val interfaces = mutableListOf<Class<*>>()

        // Block terminal — try 2025.x package first, fall back to 2024.x
        val blockCandidates = listOf(
            "org.jetbrains.plugins.terminal.block.session.ShellCommandListener",
            "org.jetbrains.plugins.terminal.exp.ShellCommandListener"
        )
        for (name in blockCandidates) {
            val cls = tryLoad(name)
            log.debug("ErrorSound: [Proxy] tryLoad($name) → ${cls?.name ?: "NOT FOUND"}")
            if (cls != null) { interfaces += cls; break }
        }

        // Reworked 2025 terminal
        val reworkedName = "org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener"
        val reworkedCls = tryLoad(reworkedName)
        log.debug("ErrorSound: [Proxy] tryLoad($reworkedName) → ${reworkedCls?.name ?: "NOT FOUND"}")
        reworkedCls?.let { interfaces += it }

        log.debug("ErrorSound: [Proxy] interfaces: ${interfaces.map { it.name }}")
        if (interfaces.isEmpty()) return null

        // Log declared methods on each interface so we know what to handle
        for (iface in interfaces) {
            val methods = iface.methods.map { "${it.name}(${it.parameterCount})" }
            log.debug("ErrorSound: [Proxy] ${iface.simpleName} declares: $methods")
        }

        val loader = interfaces.first().classLoader
        return Proxy.newProxyInstance(loader, interfaces.toTypedArray()) { _, method, args ->
            val name = method.name
            // Skip Object methods
            if (name == "toString" || name == "hashCode" || name == "equals") {
                return@newProxyInstance when (name) {
                    "hashCode" -> System.identityHashCode(this)
                    "equals" -> args?.get(0) === this
                    else -> "ErrorSound-ListenerProxy"
                }
            }

            log.debug("ErrorSound: [Event] method=$name argCount=${args?.size ?: 0}")

            // Handle any method that looks like a command-finished callback
            if (args?.size == 1 && args[0] != null &&
                (name.contains("finish", ignoreCase = true) || name.contains("complet", ignoreCase = true) || name.contains("command", ignoreCase = true))
            ) {
                try { handleCommandFinished(args[0]!!, project) } catch (e: Throwable) {
                    log.warn("ErrorSound: [Event] handleCommandFinished error: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            null
        }
    }

    private fun handleCommandFinished(event: Any, project: Project) {
        val result = extractCommandAndExitCode(event)
        if (result == null) {
            log.warn("ErrorSound: [Event] could not extract command/exitCode from ${event.javaClass.name}")
            return
        }
        val (command, exitCode) = result
        log.debug("ErrorSound: [Event] command='$command' exitCode=$exitCode")

        val settings = AlertSettings.getInstance().state
        val errorKind = ErrorClassifier.detectTerminal(command, exitCode)
        if (errorKind == ErrorKind.NONE) return

        log.debug("ErrorSound: [Event] dispatching alert for '$command' exitCode=$exitCode kind=$errorKind")
        // Key: project + command + exit code + kind — stable for repeated identical commands
        val key = "terminal:${project.locationHash}:${command.trim()}:$exitCode:$errorKind"
        AlertDispatcher.tryAlert(key, settings, errorKind, project)
    }

    private fun extractCommandAndExitCode(event: Any): Pair<String, Int>? {
        log.debug("ErrorSound: [Event] event class: ${event.javaClass.name}")
        val eventMethods = event.javaClass.methods
            .filter { it.parameterCount == 0 && it.name.startsWith("get") }
            .map { "${it.name}() → ${it.returnType.simpleName}" }
        log.debug("ErrorSound: [Event] event getters: $eventMethods")

        // Block terminal: CommandFinishedEvent → getCommand(), getExitCode()
        return try {
            val command = event.javaClass.getMethod("getCommand").invoke(event) as? String ?: ""
            val exitCode = event.javaClass.getMethod("getExitCode").invoke(event) as? Int ?: return null
            command to exitCode
        } catch (_: NoSuchMethodException) {
            // Reworked terminal: TerminalCommandFinishedEvent → getCommandBlock()
            try {
                val block = event.javaClass.getMethod("getCommandBlock").invoke(event) ?: return null
                log.debug("ErrorSound: [Event] commandBlock class: ${block.javaClass.name}")
                val blockMethods = block.javaClass.methods
                    .filter { it.parameterCount == 0 && it.name.startsWith("get") }
                    .map { "${it.name}() → ${it.returnType.simpleName}" }
                log.debug("ErrorSound: [Event] block getters: $blockMethods")
                val exitCode = block.javaClass.getMethod("getExitCode").invoke(block) as? Int ?: return null
                val command = try {
                    block.javaClass.getMethod("getExecutedCommand").invoke(block) as? String ?: ""
                } catch (_: NoSuchMethodException) {
                    try {
                        block.javaClass.getMethod("getCommand").invoke(block) as? String ?: ""
                    } catch (_: NoSuchMethodException) { "" }
                }
                command to exitCode
            } catch (e: Throwable) {
                log.warn("ErrorSound: [Event] extract failed: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun findField(cls: Class<*>, name: String): java.lang.reflect.Field? {
        var c: Class<*>? = cls
        while (c != null) {
            try { return c.getDeclaredField(name) } catch (_: NoSuchFieldException) {}
            c = c.superclass
        }
        return null
    }

    private fun tryLoad(className: String): Class<*>? =
        try { Class.forName(className) } catch (_: ClassNotFoundException) { null }
}
