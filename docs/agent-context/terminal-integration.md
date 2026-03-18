# Terminal Integration — Error Sound Alert

## Why Reflection Is Used

The terminal plugin (`org.jetbrains.plugins.terminal`) is an **optional** dependency. Its internal classes (`ShellCommandListener`, `TerminalCommandExecutionListener`, `TerminalToolWindowManager`, `TerminalToolWindowTabsManager`) are not part of the public API and are not guaranteed to be stable across IDE versions. Using reflection ensures:

1. **No compile-time coupling** — the plugin compiles without the terminal plugin present.
2. **Graceful degradation** — if terminal APIs change, the plugin still loads; only terminal alerts fail.
3. **Cross-version support** — handles both Classic/Block (2024.x) and Reworked (2025.x) terminal engines simultaneously.

## Terminal Engine Paths

### Classic / Block Terminal Path

**Strategy A** in `AlertOnTerminalCommandListener`

```
TerminalToolWindowManager.getInstance(project)
  → addNewTerminalSetupHandler(consumer, disposable)   // future tabs
  → getTerminalWidgets()                               // existing tabs
    → widget.view (field access)
      → view.session (field access)
        → session.addCommandListener(proxy, disposable)
```

**Interface class candidates:**
- `org.jetbrains.plugins.terminal.block.session.ShellCommandListener` (2025.x)
- `org.jetbrains.plugins.terminal.exp.ShellCommandListener` (2024.x)

**Retry:** If `view` or `session` is null at attach time, tries `widget.getTerminalSizeInitializedFuture().thenAccept { ... }`.

### Reworked 2025 Terminal Path

**Strategy B** in `AlertOnTerminalCommandListener`

```
TerminalToolWindowTabsManager (project service)
  → addListener(disposable, tabsListenerProxy)         // future tabs
  → getTabs()                                          // existing tabs
    → tab.getView()
      → view.getShellIntegrationDeferred()
        → deferred.getCompleted()                      // ShellIntegration
          → shellIntegration.addCommandExecutionListener(disposable, proxy)
```

**Interface class:**
- `org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener`

**Tabs listener interface:**
- `com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener`

## Listener Registration Strategy

1. **Entry point:** `ProjectActivity.execute()` — runs on project open.
2. **ToolWindowManagerListener** — listens for Terminal tool window show events → triggers `attachAll()`.
3. **Immediate attempt** — also calls `attachAll()` at startup in case Terminal is already open.
4. Both Block and Reworked attachment run independently — failures in one do not affect the other.

## Retry / Attach Behavior

For the Reworked path, shell integration may not be immediately available:

1. **Immediate attempt** — `tryAttachReworkedViewNow()`
2. **Deferred completion hook** — `view.getShellIntegrationDeferred().invokeOnCompletion { ... }`
3. **Scheduled retry** — up to 60 attempts × 500ms = 30 seconds max wait

Double-attachment prevention:
- `hookedViews` map (by identity hash) prevents re-hooking the same view
- `completionHookRegistered` map prevents duplicate deferred hooks
- `reworkedListenerRegistered` / `blockListenerRegistered` AtomicBooleans prevent duplicate tab listeners

## Shell Integration Retrieval (4-Strategy Fallback)

The `getShellIntegration(view)` method tries four strategies in order:

1. `view.getShellIntegrationDeferred().getCompleted()` — kotlinx `Deferred` completion
2. `view.getShellIntegration()` or `view.getTerminalShellIntegration()` — direct getter
3. Type-matching scan — any zero-arg getter returning a type with "ShellIntegration" in its name
4. Field access — tries fields named `shellIntegration`, `myShellIntegration`, `_shellIntegration`

## Argument Order Try/Catch

Terminal listener methods accept `(Disposable, listener)` or `(listener, Disposable)` — the order varies between IDE versions. The code tries both:

```kotlin
try { addMethod.invoke(session, proxy, disposable) }
catch { addMethod.invoke(session, disposable, proxy) }
```

## Likely Breakage Points in Future IDE Versions

| Area | Risk | Description |
|---|---|---|
| Class names / packages | HIGH | Terminal classes frequently move packages between major versions |
| Method signatures | HIGH | `addCommandListener` / `addCommandExecutionListener` parameter types may change |
| Shell integration access | MEDIUM | Deferred vs. direct getter patterns may change |
| Widget/View internal structure | MEDIUM | Private fields (`view`, `session`) may be renamed or removed |
| Tabs manager API | MEDIUM | `TerminalTabsManagerListener` and `getTabs()` may change |

## Logging Guidance

- **Debug level** is used for all normal lifecycle events (attach, proxy build, event receipt)
- **Warn level** is reserved for actual failures (class not found, invocation errors, exhausted retries)
- Terminal logging was intentionally reduced from warn to debug for expected/harmless situations (e.g., class not found on newer IDEs)

## What to Verify After Terminal Changes

1. **Build:** `./gradlew buildPlugin` — compilation succeeds
2. **Verify:** `./gradlew verifyPlugin` — compatibility check passes
3. **Manual test on 2024.3** — Open terminal, run a failing command (e.g., `false`), verify sound plays
4. **Manual test on 2025.x** — Same test on a recent IDE version (reworked terminal)
5. **Terminal not installed** — Verify plugin loads without the terminal plugin (optional dependency)
6. **Multiple tabs** — Open several terminal tabs, run failures in each, verify deduplication works
7. **Check IDE log** — Confirm no unexpected warnings; debug-level messages should trace the attach flow

---
*Last updated from code scan: 2026-03-18*
