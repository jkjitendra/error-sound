# Architecture — Error Sound Alert

## Detection Paths

### 1. ExecutionListener Path
- **Class:** `AlertOnErrorExecutionListener`
- **Trigger:** `processStarted()` registers a `ProcessListener` on the `ProcessHandler`
- **Flow:**
  1. `onTextAvailable()` — captures output into a `StringBuilder` (capped at 1M chars), runs `ErrorClassifier.detect()` per chunk
  2. `processTerminated()` — evaluates final output + exit code → determines `ErrorKind`
  3. If final kind is `NONE` and exit code is `0` → converts to `SUCCESS`
  4. → `AlertDispatcher.tryAlert(key, settings, kind)`

### 2. ConsoleFilterProvider Path
- **Class:** `ErrorConsoleFilterProvider` → `ErrorDetectionFilter`
- **Trigger:** IntelliJ calls `applyFilter()` for every line printed to any `ConsoleView`
- **Flow:**
  1. Line matched against Regex patterns (Exception, Error, FATAL, BUILD FAILED, etc.)
  2. On match → `ErrorClassifier.detect(line, 1)` to classify
  3. → `AlertDispatcher.tryAlert("console:{projectHash}:{kind}", settings, kind)`
  4. Returns `null` (no text modification — sound side-effect only)

### 3. Terminal Reflection Path
- **Class:** `AlertOnTerminalCommandListener` (implements `ProjectActivity`)
- **Trigger:** `backgroundPostStartupActivity` in `terminal-features.xml` (only loaded when terminal plugin is present)
- **Flow:**
  1. Builds a JDK `Proxy` implementing `ShellCommandListener` and/or `TerminalCommandExecutionListener`
  2. Attaches to both Block/Classic and Reworked terminal engines via reflection
  3. Proxy handles `commandFinished`-like callbacks → `extractCommandAndExitCode()` → `ErrorClassifier.detectTerminal()`
  4. → `AlertDispatcher.tryAlert("terminal:{projectHash}:{command}:{exitCode}:{kind}", settings, kind)`

## Routing Flow

```
Detection Source
  ├── AlertOnErrorExecutionListener
  ├── ErrorConsoleFilterProvider
  └── AlertOnTerminalCommandListener
         │
         ▼
   AlertDispatcher.tryAlert(key, settings, kind)
         │
         ├── SnoozeState.isSnoozed()
         │     └── short-circuits all gates (transient, no settings needed)
         │
         ├── AlertMonitoring.shouldMonitor(settings, kind)
         │     └── checks: settings.enabled + per-kind monitor flags
         │
         ├── AlertEventGate.shouldPlay(key)
         │     └── per-key cooldown (4s) + global cooldown (2s) + eviction
         │
         └── ErrorSoundPlayer.play(settings, kind)
               └── last-resort debounce (250ms) + sound resolution + playback
```

## Settings / Configuration Flow

- `AlertSettings` — `@Service(APP)`, persisted to `errorSoundAlert.xml`
- `ErrorSoundConfigurable` — Settings UI panel under **Tools → Error Sound Alert**
- `ErrorSoundToolWindowFactory` — **Error Monitor** sidebar panel (right anchor, secondary)
- Tool window directly mutates `AlertSettings.state` for monitor toggle flags
- Settings panel uses `apply()` / `reset()` pattern with `loadState()` for all other settings

## UI Relationships

```
Settings → Tools → Error Sound Alert
  └── ErrorSoundConfigurable (Configurable)
        └── manages: sound source, built-in/custom sounds, per-kind sounds,
            volume, duration, global mode, preview

Error Monitor (Tool Window, right sidebar)
  └── ErrorSoundToolWindowFactory → ErrorSoundToolWindowPanel
        └── manages: enable/disable, per-kind monitor toggles,
            presets (All / Build Only / Runtime Only)
        └── links to: ErrorSoundConfigurable via "Open sound settings" button
```

## Resource Usage / Audio Playback

- `ErrorSoundPlayer` uses a single-threaded bounded executor (`AppExecutorUtil`)
- Playback uses JVM `javax.sound.sampled.Clip` — loops clip until alert duration expires
- Volume: dB-accurate scaling via `FloatControl.MASTER_GAIN`
- Preview: separate daemon thread with token-based cancellation
- Fallback chain: custom file → built-in WAV → generated 880 Hz tone → system beep

## Deduplication Strategy

Two layers:

1. **`AlertEventGate`** (primary) — per-key cooldown (4s) + global cooldown (2s). Keys include source type + project hash + error details. Map eviction at 512 entries (60s TTL).
2. **`ErrorSoundPlayer`** (last-resort) — 250ms debounce via `AtomicLong` timestamp. Only catches near-simultaneous calls that slip past the gate.

## Terminal Reflection Compatibility

- Zero direct imports from terminal plugin — everything loaded via `Class.forName()`
- Proxy implements both `ShellCommandListener` (block/classic) and `TerminalCommandExecutionListener` (reworked)
- Block path: `TerminalToolWindowManager` → widgets → `view` field → `session` field → `addCommandListener`
- Reworked path: `TerminalToolWindowTabsManager` → tabs → `getView()` → `getShellIntegrationDeferred()` → `getCompleted()` → `addCommandExecutionListener`
- Retry/poll: up to 60 attempts × 500ms for deferred shell integration
- Argument order try/catch: `(proxy, disposable)` first, then `(disposable, proxy)`

## Fallback Behavior

| Level | Fallback |
|---|---|
| Sound file | custom file → built-in WAV → 880 Hz generated tone → system beep |
| Terminal attach | immediate attach → deferred completion hook → scheduled retry (30s) |
| Terminal interface | Block `ShellCommandListener` → Reworked `TerminalCommandExecutionListener` |
| Shell integration | `getShellIntegrationDeferred().getCompleted()` → `getShellIntegration()` → `getTerminalShellIntegration()` → type-matching getter → field access |

---
*Last updated from code scan: 2026-03-23*
