# Architecture — Error Sound Alert

## Detection Paths

### 1. ExecutionListener Path
- **Class:** `AlertOnErrorExecutionListener`
- **Trigger:** `processStarted()` registers a `ProcessListener` on the `ProcessHandler`
- **Flow:**
  1. `onTextAvailable()` — two separate accumulators: `customDetectedKind` and `builtInDetectedKind`
     - If a custom LINE_TEXT rule matches the chunk → update `customDetectedKind`; skip built-in for that chunk
     - Otherwise → run `ErrorClassifier.detect()` and update `builtInDetectedKind`
  2. `processTerminated()` — priority order (highest wins):
     1. Custom FULL_OUTPUT rule on full buffer
     2. Custom EXIT_CODE_AND_TEXT rule on full buffer + exit code
     3. `customDetectedKind` (any custom LINE_TEXT chunk match)
     4. `builtInDetectedKind` (highest-priority built-in chunk match)
     5. `ErrorClassifier.detect(fullBuffer, exitCode)`
  3. If final kind is `NONE` and exit code is `0` → converts to `SUCCESS`
  4. → `AlertDispatcher.tryAlert(key, settings, kind)`

### 2. ConsoleFilterProvider Path
- **Class:** `ErrorConsoleFilterProvider` → `ErrorDetectionFilter`
- **Trigger:** IntelliJ calls `applyFilter()` for every line printed to any `ConsoleView`
- **Flow:**
  1. Custom LINE_TEXT rules checked first (`engine.matchLineText(line)`)
  2. If no custom match, line matched against built-in Regex patterns (Exception, Error, FATAL, BUILD FAILED, etc.)
  3. On match → `ErrorClassifier.detect(line, 1)` to classify (only if custom rule did not match)
  4. → `AlertDispatcher.tryAlert("console:{projectHash}:{kind}", settings, kind)`
  5. Returns `null` (no text modification — sound side-effect only)
  - FULL_OUTPUT and EXIT_CODE_AND_TEXT rules are **skipped** in this path

### 3. Terminal Reflection Path
- **Class:** `AlertOnTerminalCommandListener` (implements `ProjectActivity`)
- **Trigger:** `backgroundPostStartupActivity` in `terminal-features.xml` (only loaded when terminal plugin is present)
- **Flow:**
  1. Builds a JDK `Proxy` implementing `ShellCommandListener` and/or `TerminalCommandExecutionListener`
  2. Attaches to both Block/Classic and Reworked terminal engines via reflection
  3. Proxy handles `commandFinished`-like callbacks → `extractCommandAndExitCode()` → three-tier classification:
     - **Tier 1:** custom EXIT_CODE_AND_TEXT rules (`engine.matchExitCodeAndText()`) — if matched, dispatch immediately with no sound override
     - **Tier 2:** `ErrorClassifier.classifyTerminal(command, exitCode, exitCodeRules)` → `TerminalClassifyResult(kind, soundOverride, suppressed)`
       - if `suppressed == true` → return silently (no alert, no log)
       - if `kind == NONE` → return
       - dispatch with `soundOverride` (null if the matching rule has no per-code sound set)
     - **Tier 3:** built-in fallback — `classifyTerminal()` internally calls `detectTerminal()` when no exit-code rule matches (GENERIC for non-zero exit, NONE for zero)
  4. LINE_TEXT and FULL_OUTPUT rules are **skipped** in this path
  5. → `AlertDispatcher.tryAlert("terminal:{projectHash}:{command}:{exitCode}:{kind}", settings, kind, project, soundOverride?)`

## Routing Flow

```
Detection Source
  ├── AlertOnErrorExecutionListener
  │     ├── onTextAvailable: CustomRuleEngine.matchLineText() → ErrorClassifier.detect()
  │     └── processTerminated: matchFullOutput() / matchExitCodeAndText() → ErrorClassifier.detect()
  ├── ErrorConsoleFilterProvider
  │     └── applyFilter: CustomRuleEngine.matchLineText() → built-in errorPattern → ErrorClassifier.detect()
  └── AlertOnTerminalCommandListener
        └── handleCommandFinished:
              1. CustomRuleEngine.matchExitCodeAndText()   [Phase 5 — highest priority]
              2. ErrorClassifier.classifyTerminal()        [Phase 6 — exit-code rules + suppression]
              3. ErrorClassifier.detectTerminal()          [built-in fallback inside classifyTerminal]
         │
         ▼
   AlertDispatcher.tryAlert(key, settings, kind, project?, soundOverride?)
         │
         ├── SnoozeState.isSnoozed()
         │     └── short-circuits all gates (transient, no settings needed)
         │
         ├── AlertMonitoring.shouldMonitor(settings, kind)
         │     └── checks: settings.enabled + per-kind monitor flags
         │     └── NOTE: kind-enable check still applies even when soundOverride is set
         │           (soundOverride changes which sound plays; it does not bypass the kind gate)
         │
         ├── AlertEventGate.shouldPlay(key)
         │     └── per-key cooldown (4s) + global cooldown (2s) + eviction
         │
         └── ErrorSoundPlayer.play(settings, kind, soundOverride?)
               └── if soundOverride != null → play that built-in ID directly (playBuiltInById)
               └── else → normal sound resolution (global/per-kind/custom-file)
               └── last-resort debounce (250ms)
```

## soundOverride Policy

**Decision: kind-enable check is NOT bypassed by soundOverride.**

When a terminal exit-code rule specifies a `soundId` (i.e., `soundOverride` is non-null), the override only changes *which* sound is played — it does not skip the `AlertMonitoring.shouldMonitor()` gate or the per-kind sound-enable check inside `ErrorSoundPlayer.play()`.

Example:
- Exit code 137 → exit-code rule maps it to `kind=GENERIC`, `soundId="boom"`
- User has `genericSoundEnabled = false` (GENERIC sounds disabled in settings)
- **Result:** no sound plays — `isErrorKindEnabled()` returns `false` for GENERIC, playback is skipped entirely

**Rationale:** The kind enable/disable flags represent the user's intent to receive or not receive a category of alerts. A per-event sound override is a cosmetic choice (which sound), not an escalation that overrides the alert policy. If a user disables GENERIC sounds, they mean to suppress GENERIC alerts — including those triggered by exit-code rules.

This policy is consistent, predictable, and avoids "phantom" sounds surprising users who have explicitly disabled a kind.

---

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
## Custom Rule Classification

`CustomRuleEngine` compiles `AlertSettings.State.customRules` once and caches the result in `AlertSettings`. The cache is invalidated every time `loadState()` is called (i.e., on every settings Apply).

**Target semantics — where each target is evaluated:**

| Target | Run/Debug chunks | Run/Debug final | Console filter | Terminal |
|---|---|---|---|---|
| LINE_TEXT | ✓ | ✗ | ✓ | ✗ |
| FULL_OUTPUT | ✗ | ✓ | ✗ | ✗ |
| EXIT_CODE_AND_TEXT | ✗ | ✓ | ✗ | ✓ |

Unsupported targets are deterministically skipped — not reinterpreted.

**Priority within processTerminated():**
1. Custom FULL_OUTPUT match → wins over everything
2. Custom EXIT_CODE_AND_TEXT match → wins if no FULL_OUTPUT match
3. Custom LINE_TEXT chunk accumulation (`customDetectedKind`) → wins over all built-in
4. Built-in chunk accumulation (`builtInDetectedKind`, highest-priority kind seen in chunks)
5. Built-in `ErrorClassifier.detect()` on full buffer

*Last updated from code scan: 2026-04-07*
