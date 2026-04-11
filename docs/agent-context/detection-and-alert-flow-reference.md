# Detection and Alert Flow Reference — Error Sound Alert

Complete reference for how errors are detected, classified, and dispatched to audio/visual alerts.

---

## Three Detection Paths

### 1. Run/Debug ExecutionListener (`AlertOnErrorExecutionListener`)

**Entry point:** `ExecutionListener.processStarted()` (registered via `executionListener` extension point)

#### Phase A — Chunk classification (`onTextAvailable`)
Two separate accumulators: `customDetectedKind` and `builtInDetectedKind` (both `AtomicReference<ErrorKind>`).

For each chunk of process output:
1. Append chunk to output buffer (capped at 1M chars)
2. Get compiled rule engine: `AlertSettings.getInstance().getCompiledRuleEngine()`
3. If `engine.hasLineTextRules`: call `engine.matchLineText(chunk)` → `customKind`
4. If `customKind != null` → `updateDetectedKind(customDetectedKind, customKind)` — **stop here for this chunk** (do not run built-in)
5. Else → `ErrorClassifier.detect(chunk, exitCode=0)` → `builtInKind`; if ≠ NONE → `updateDetectedKind(builtInDetectedKind, builtInKind)`

Custom and built-in accumulators are fully independent; the priority-based CAS in `updateDetectedKind` applies within each accumulator separately.

#### Phase B — Final classification (`processTerminated`)
Priority ladder (step 1 wins; later steps only reached if prior steps yield nothing):

1. If `engine.hasFullOutputRules`: `engine.matchFullOutput(fullBuffer)` → `customFinalKind`
2. If not found and `engine.hasExitCodeAndTextRules`: `engine.matchExitCodeAndText(fullBuffer, exitCode)` → `customFinalKind`
3. If not found: `customDetectedKind.get()` if ≠ NONE → `customFinalKind`
4. If `customFinalKind != null` → `errorKind = customFinalKind`
5. Else: `errorKind = builtInDetectedKind.get()` if ≠ NONE, else `ErrorClassifier.detect(fullBuffer, exitCode)`
6. If `errorKind == NONE && exitCode == 0` → `errorKind = SUCCESS`
7. If `errorKind == NONE` → return (no alert)
8. Duration threshold: if `elapsed < minProcessDurationSeconds * 1000` → suppress + log
9. **Phase 7:** `settingsState = ResolvedSettingsResolver.getInstance(env.project).resolve()`
10. `AlertDispatcher.tryAlert("exec:{handlerHash}:{kind}", settingsState, kind, project)`

---

### 2. Console Filter (`ErrorConsoleFilterProvider` → `ErrorDetectionFilter`)

**Entry point:** `Filter.applyFilter(line, entireLength)` — called by IntelliJ for every line in any `ConsoleView`

**Flow:**
1. Get compiled rule engine
2. If `engine.hasLineTextRules`: call `engine.matchLineText(line)` → `customKind`
3. If `customKind == null && !errorPattern.containsMatchIn(line)` → return `null` (no alert, fast exit)
4. `errorKind = customKind ?: ErrorClassifier.detect(line, exitCode=1)`
5. **Phase 7:** `resolvedState = ResolvedSettingsResolver.getInstance(project).resolve()`
6. `AlertDispatcher.tryAlert("console:{projectLocationHash}:{kind}", resolvedState, kind, project)`
7. Return `null` (sound side-effect only; line is not modified)

**Unsupported targets:** FULL_OUTPUT and EXIT_CODE_AND_TEXT rules are never evaluated here.

---

### 3. Terminal Listener (`AlertOnTerminalCommandListener`)

**Entry point:** `ProjectActivity.execute()` at project startup; attaches JDK `Proxy` to terminal sessions

**Flow (per command):**
1. Proxy intercepts `commandFinished`-like callback
2. `extractCommandAndExitCode(event)` → `(command: String, exitCode: Int)`
3. Get compiled rule engine
4. **Phase 7:** `resolvedState = ResolvedSettingsResolver.getInstance(project).resolve()`
5. If `engine.hasExitCodeAndTextRules`: call `engine.matchExitCodeAndText(command, exitCode)` → `customKind`
6. `AlertDispatcher.tryAlert("terminal:{projectLocationHash}:{command}:{exitCode}:{kind}", resolvedState, kind, project)` (custom path)
7. Or: `ErrorClassifier.classifyTerminal(command, exitCode, settings.state.exitCodeRules)` → `TerminalClassifyResult`
8. `AlertDispatcher.tryAlert("terminal:...", resolvedState, kind, project, soundOverride?)` (exit-code rule path)

**Unsupported targets:** LINE_TEXT and FULL_OUTPUT rules are never evaluated here.
**Resolver note:** Exit-code rules still read from `settings.state.exitCodeRules` (global); only the state object passed to `AlertDispatcher` is resolved.

---

## Custom Rule Target Matrix

| Target | Run/Debug chunks | Run/Debug final | Console filter | Terminal |
|---|---|---|---|---|
| LINE_TEXT | ✓ (per chunk) | ✗ | ✓ (per line) | ✗ |
| FULL_OUTPUT | ✗ | ✓ (full buffer) | ✗ | ✗ |
| EXIT_CODE_AND_TEXT | ✗ | ✓ (buffer + exitCode) | ✗ | ✓ (command + exitCode) |

Unsupported combinations are **skipped deterministically** — the path moves on without reinterpreting the rule as a different target.

### EXIT_CODE_AND_TEXT combined string format

The pattern is matched against: `"exitcode:<N>\n<text>"`

Examples:
- Pattern `exitcode:1` → matches any exit code 1
- Pattern `my-error` → matches "my-error" anywhere in the text
- Pattern `exitcode:127` → matches only exit code 127
- Pattern `(?i)connection refused` → matches text regardless of case

All rules compile with `IGNORE_CASE` and `MULTILINE` flags.

---

## AlertDispatcher Gate Order

All three paths call `AlertDispatcher.tryAlert(key, resolvedSettings, kind, project?)`. Gates are applied in this order; the first failure short-circuits:

```
1. SnoozeState.isSnoozed()          → if snoozed: return (no alert)
2. AlertMonitoring.shouldMonitor()  → checks resolvedSettings.enabled + per-kind monitor flags
                                      └─ Phase 7: resolvedSettings.enabled may differ per project
3. AlertEventGate.shouldPlay(key)   → per-key cooldown 4s + global cooldown 2s
4. ErrorSoundPlayer.play()          → 250ms last-resort debounce, then playback
5. showNotification() [optional]    → balloon if resolvedSettings.showVisualNotification enabled
```

---

## `ErrorClassifier` Built-in Detection

### `detect(outputText, exitCode)` — used by Run/Debug and Console paths

Priority order (first match wins):

| Priority | Kind | Patterns |
|---|---|---|
| 1 | CONFIGURATION | `could not resolve placeholder`, `failed to load property source`, `beancreationexception`, `illegalstateexception` |
| 2 | COMPILATION | `compilation failed`, `cannot find symbol`, `error:`, `kotlin:` + `error` |
| 3 | TEST_FAILURE | `tests failed`, `there were failing tests`, `assertionerror` |
| 4 | NETWORK | `connection refused`, `connect timed out`, `unknownhostexception`, `sockettimeoutexception` |
| 5 | EXCEPTION | `exception`, `caused by:`, `stacktrace` |
| 6 | GENERIC | exitCode ≠ 0 OR `failed` OR `error` in text |
| — | NONE | clean exit, no matches |

### `detectTerminal(command, exitCode)` — used by Terminal path

- exitCode == 0 → NONE
- exitCode ≠ 0 → GENERIC

Custom EXIT_CODE_AND_TEXT rules are evaluated before this method, so more specific classification is possible via custom rules.

---

## Deduplication Strategy

Two layers, both must pass for an alert to play:

### `AlertEventGate` (primary)
- Per-key cooldown: 4 seconds (no sound for same key within window)
- Global cooldown: 2 seconds (no sound globally within window)
- Map eviction: entries older than 60s purged when map exceeds 512 entries

**Key formats:**
- `"exec:{handlerIdentityHash}:{kind}"` — Run/Debug
- `"console:{projectLocationHash}:{kind}"` — Console
- `"terminal:{projectLocationHash}:{command}:{exitCode}:{kind}"` — Terminal

### `ErrorSoundPlayer` (last-resort)
- 250ms debounce via `AtomicLong` timestamp
- Only catches near-simultaneous calls that slip past the gate

---
*Last updated: 2026-04-11*
