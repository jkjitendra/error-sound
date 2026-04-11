# Code Map — Error Sound Alert

Class-by-class reference for the `com.drostwades.errorsound` package.

---

## CustomRuleEngine

**File:** `CustomRuleEngine.kt`
**Purpose:** Compiles and caches user-defined regex rules from `AlertSettings.State.customRules`. Provides target-specific match methods used by all three detection paths.

| Method | Description |
|---|---|
| `matchLineText(line)` | Matches LINE_TEXT rules against a single line/chunk |
| `matchFullOutput(text)` | Matches FULL_OUTPUT rules against the complete buffered output |
| `matchExitCodeAndText(text, exitCode)` | Matches EXIT_CODE_AND_TEXT rules against `"exitcode:N\n<text>"` |

**Guards:** `hasLineTextRules`, `hasFullOutputRules`, `hasExitCodeAndTextRules` — `Boolean` flags to skip evaluation when no applicable rules exist.

**Constants:**
- `MAX_RULES = 100` — rules beyond this count are ignored
- `MAX_PATTERN_LENGTH = 500` — patterns truncated to this length at normalization
- `ALLOWED_CUSTOM_RULE_KINDS` — CONFIGURATION, COMPILATION, TEST_FAILURE, NETWORK, EXCEPTION, GENERIC (NONE and SUCCESS excluded)

**Construction:** Compiles rules once; skips disabled rules, blank patterns, and invalid regex (`PatternSyntaxException` caught silently).

- **Risk:** LOW — pure computation, no side effects

---

## AlertDispatcher

**File:** `AlertDispatcher.kt`  
**Purpose:** Single routing choke-point. Gate order: `SnoozeState` → `AlertMonitoring` → `AlertEventGate` → `ErrorSoundPlayer` → visual notification.

| Method | Signature | Description |
|---|---|---|
| `tryAlert` | `(key: String, settings: State, kind: ErrorKind, project: Project? = null, soundOverride: String? = null)` | Routes through all gates; optionally shows balloon notification |
| `showNotification` | `(settings: State, kind: ErrorKind, project: Project)` | Shows BALLOON notification with kind title + "Open Settings" / "Mute 1 hr" actions |

- **Inputs:** deduplication key, current settings state, detected error kind, optional project
- **Outputs:** none (fire-and-forget side effect)
- **Side effects:** may trigger audio playback; may show balloon notification
- **Risk:** LOW-MEDIUM — thin routing layer, but every detection path depends on it

---

## AlertEventGate

**File:** `AlertEventGate.kt` (53 lines)
**Purpose:** Central deduplication gate. Prevents duplicate sounds when multiple detection paths fire for the same logical failure.

| Method | Signature | Description |
|---|---|---|
| `shouldPlay` | `(key: String, now: Long): Boolean` | Returns `true` if alert is allowed, `false` if duplicate |

**Constants:**
- `PER_KEY_COOLDOWN_MS` = 4,000ms — blocks same key within 4s
- `GLOBAL_COOLDOWN_MS` = 2,000ms — blocks any sound within 2s of the last
- `EVICT_AFTER_MS` = 60,000ms — eviction TTL for stale entries
- `EVICT_THRESHOLD` = 512 — triggers eviction sweep

- **Thread safety:** `@Synchronized` method
- **Side effects:** mutates `keyLastSeen` map and `globalLastSeen` timestamp
- **Risk:** MEDIUM — tuning these values affects UX (too low = duplicates, too high = swallowed alerts)

---

## AlertMonitoring

**File:** `AlertMonitoring.kt` (34 lines)
**Purpose:** Centralized rule gate. Checks whether a given `ErrorKind` should be monitored based on current settings.

| Method | Signature | Description |
|---|---|---|
| `shouldMonitor` | `(settings: State, kind: ErrorKind): Boolean` | Master-enable check + per-kind check |
| `isKindEnabled` | `(settings: State, kind: ErrorKind): Boolean` | Per-kind flag lookup |
| `setKindEnabled` | `(settings: State, kind: ErrorKind, enabled: Boolean)` | Per-kind flag setter (used by tool window) |

- **Side effects:** `setKindEnabled` mutates settings state directly
- **Risk:** LOW — pure logic, but tool window depends on it for live toggling

---

## AlertOnErrorExecutionListener

**File:** `AlertOnErrorExecutionListener.kt`
**Purpose:** Listens to Run/Debug process lifecycle. Captures output, classifies errors, dispatches alerts on process termination.

| Method | Signature | Description |
|---|---|---|
| `processStarted` | `(executorId, env, handler)` | Attaches `ProcessListener` to capture output |

**Internal logic:**
- Output buffer capped at 1M characters
- `onTextAvailable()` — LINE_TEXT custom rules checked first per chunk; falls back to `ErrorClassifier.detect()` if no match
- `processTerminated()` — FULL_OUTPUT then EXIT_CODE_AND_TEXT custom rules applied to full buffer first; if matched, custom kind overrides everything; otherwise chunk accumulation + full-buffer `ErrorClassifier.detect()`
- Priority system for chunk accumulation: CONFIGURATION > COMPILATION > TEST_FAILURE > NETWORK > EXCEPTION > GENERIC > NONE
- If final kind is NONE and exitCode == 0, converts to SUCCESS
- Duration threshold: if `elapsedMillis < minProcessDurationSeconds * 1000`, alert suppressed (Run/Debug only)
- Dedup key: `"exec:{handlerIdentityHash}:{errorKind}"`
- Routes through `AlertDispatcher.tryAlert()`

- Phase 7: `processTerminated()` resolves effective settings via `ResolvedSettingsResolver.getInstance(env.project).resolve()` — project-level `enabled` override is honoured at dispatch time
- **Risk:** LOW — straightforward listener pattern

---

## AlertOnTerminalCommandListener

**File:** `AlertOnTerminalCommandListener.kt` (591 lines)
**Purpose:** Monitors terminal command completions via JDK reflection proxies. Supports both Classic/Block and Reworked 2025 terminal engines.

| Method | Scope | Description |
|---|---|---|
| `execute` | `ProjectActivity` | Entry point — builds proxy, registers ToolWindowManagerListener |
| `attachAll` | private | Attempts both Block and Reworked attachment |
| `attachBlockTerminal` | private | Block/Classic terminal: `TerminalToolWindowManager` → widgets → session |
| `attachReworkedTerminal` | private | Reworked terminal: `TerminalToolWindowTabsManager` → tabs → view → shell integration |
| `buildListenerProxy` | private | Creates JDK `Proxy` implementing 1–2 listener interfaces |
| `handleCommandFinished` | private | Three-tier precedence: (1) custom EXIT_CODE_AND_TEXT regex, (2) exit code rules via `classifyTerminal()` with suppression check, (3) built-in fallback. Phase 7: uses `ResolvedSettingsResolver` for the settings state passed to `AlertDispatcher` |
| `extractCommandAndExitCode` | private | Reflection-based extraction from event objects |
| `getShellIntegration` | private | 4-strategy fallback to get shell integration from a view |

- **Risk:** HIGH — 591 lines of reflection. Most likely file to break with IDE updates.
- **See:** `docs/agent-context/terminal-integration.md` for detailed breakdown.

---

## AlertSettings

**File:** `AlertSettings.kt`
**Purpose:** Persistent application-level settings. Stored in `errorSoundAlert.xml`.

**Nested types:**
- `enum class MatchTarget { LINE_TEXT, FULL_OUTPUT, EXIT_CODE_AND_TEXT }` — where a custom rule pattern applies
- `data class CustomRuleState(id, enabled, pattern, matchTarget, kind)` — a single user-defined rule
- `data class ExitCodeRuleState(exitCode, enabled, kind, soundId?, suppress)` — a single terminal exit-code rule

**State fields:**
- `enabled` — master toggle
- `monitorConfiguration/Compilation/TestFailure/Network/Exception/Generic` — per-kind monitor flags
- `monitorSuccess` — success monitoring flag (default: `false`)
- `volumePercent` (0–100), `alertDurationSeconds` (1–10)
- `soundSource` — `BUNDLED` or `CUSTOM`
- `builtInSoundId` — global sound ID
- `useGlobalBuiltInSound` — one sound for all kinds
- `{kind}SoundEnabled`, `{kind}SoundId` — per-kind sound config
- `successSoundEnabled` (default: `false`), `successSoundId` (default: `"yeah_boy"`) — success sound config
- `minProcessDurationSeconds: Int = 0` — duration threshold for Run/Debug alerts (0 = disabled, max 300)
- `customSoundPath` — absolute path to custom audio file
- `showVisualNotification: Boolean = false` — master toggle for balloon notifications (off by default)
- `visualNotificationOnError: Boolean = true` — show balloon on error alerts (when master is on)
- `visualNotificationOnSuccess: Boolean = true` — show balloon on success alerts (when master is on)
- `customRules: MutableList<CustomRuleState> = mutableListOf()` — user-defined regex rules (evaluated before built-in classification)
- `exitCodeRules: MutableList<ExitCodeRuleState>` — terminal exit code → kind/sound/suppress mapping (4 defaults: 130 suppress, 127/137/143 GENERIC)

**Methods:**
- `getCompiledRuleEngine(): CustomRuleEngine` — returns cached compiled rule engine; lazily created, invalidated by `loadState()`

**Validation:** `loadState()` normalizes sound IDs, clamps numeric values, normalizes custom rules (count, pattern length, matchTarget, kind, IDs), and normalizes exit code rules (kind, soundId blank/CUSTOM_FILE → null).

- **Risk:** LOW — standard `PersistentStateComponent` pattern

---

## ProjectAlertSettings

**File:** `ProjectAlertSettings.kt`  
**Purpose:** Project-scoped settings service (Phase 7 — Project-Level Profiles). Persisted to workspace storage (`WORKSPACE_FILE`) so overrides are per-workspace, not shared across clones.

**Level:** `@Service(Service.Level.PROJECT)`

**State fields:**
- `useOverride: Boolean = false` — whether the project-level enabled override is active
- `enabledOverride: Boolean = true` — the override value (relevant only when `useOverride == true`)

**Methods:**
- `effectiveEnabledOverride(): Boolean?` — returns `null` (inherit global) or the override value
- `getInstance(project): ProjectAlertSettings` — companion helper

**Phase 7 scope:** Only the `enabled` flag can be overridden per project. All other settings (sounds, per-kind flags, custom rules, exit-code rules) are NOT stored here.

- **Risk:** LOW — simple persistent state component, workspace-scoped

---

## ResolvedSettingsResolver

**File:** `ResolvedSettingsResolver.kt`  
**Purpose:** Project service (Phase 7) that merges project-level overrides on top of global application settings and returns the effective `AlertSettings.State` for the current project.

**Level:** `@Service(Service.Level.PROJECT)`

| Method | Description |
|---|---|
| `resolve()` | Returns a copy of global `AlertSettings.State` with `enabled` replaced by the project override if active; returns the global state object directly when no override is set |
| `getInstance(project)` | Companion helper |

**Phase 7 merge rule:** Only `enabled` may differ per project. Everything else comes unchanged from `AlertSettings.getInstance().state`.

**Usage pattern:** All three detection paths call `ResolvedSettingsResolver.getInstance(project).resolve()` instead of `AlertSettings.getInstance().state` when passing settings to `AlertDispatcher.tryAlert()`.

- **Risk:** LOW — thin delegating wrapper; only modifies one field

---

## BuiltInSounds

**File:** `BuiltInSounds.kt` (44 lines)
**Purpose:** Registry of bundled WAV sound files.

**Available sounds:** boom, dog_laughing_meme, faaa, huh, punch, yeah_boy, yooo

| Method | Description |
|---|---|
| `findByIdOrDefault(id)` | Resolves sound by ID, falls back to `default` (boom) |
| `allWithCustom(path)` | Returns all sounds + custom file option if path is non-blank |
| `customFileOption(path)` | Creates a `BuiltInSound` entry for the custom file |

**Special constant:** `CUSTOM_FILE_ID = "__custom_file__"`

- **Risk:** LOW

---

## ErrorConsoleFilterProvider

**File:** `ErrorConsoleFilterProvider.kt`
**Purpose:** Registered as `consoleFilterProvider` extension. Provides an `ErrorDetectionFilter` that matches error patterns in every console line.

**Detection order:**
1. Custom LINE_TEXT rules (via `CustomRuleEngine.matchLineText()`) — checked first on every line
2. Built-in `errorPattern` regex — only reached if no custom LINE_TEXT rule matched

**FULL_OUTPUT and EXIT_CODE_AND_TEXT targets are explicitly skipped** — unsupported in this path.

**ErrorDetectionFilter patterns (built-in):** Exception, Error, FATAL, `Caused by:`, stack trace lines, BUILD FAILED, FAILURE:, Tests failed, compilation failed, command not found.

**Dedup key format:** `"console:{project.locationHash}:{errorKind}"`

- Phase 7: `AlertDispatcher.tryAlert()` call uses `ResolvedSettingsResolver.getInstance(project).resolve()` for the settings state — project-level `enabled` override is honoured
- **Side effects:** Calls `AlertDispatcher.tryAlert()` — returns `null` filter result (no text modification)
- **Risk:** LOW-MEDIUM — false positives possible for benign lines containing "error"; hot path — `hasLineTextRules` guard keeps overhead minimal when no custom rules exist

---

## ErrorClassifier / ErrorKind

**File:** `ErrorKind.kt` (121 lines)
**Purpose:** Error classification logic and value types.

**ErrorKind enum:** NONE, CONFIGURATION, COMPILATION, TEST_FAILURE, NETWORK, EXCEPTION, GENERIC, SUCCESS

**`TerminalClassifyResult`** (data class): `(kind: ErrorKind, soundOverride: String?, suppressed: Boolean)` — richer result from terminal classification carrying an optional per-event sound override and a suppression flag.

| Method | Description |
|---|---|
| `ErrorClassifier.detect(outputText, exitCode)` | Full-text classification against hardcoded string patterns |
| `ErrorClassifier.detectTerminal(command, exitCode)` | Terminal-only: returns GENERIC for exitCode ≠ 0, NONE for 0 |
| `ErrorClassifier.classifyTerminal(command, exitCode, exitCodeRules)` | Iterates exit code rules (first enabled match wins); falls back to `detectTerminal()` |

**Classification priority (first match wins):**
1. CONFIGURATION — `"could not resolve placeholder"`, `"beancreationexception"`, etc.
2. COMPILATION — `"compilation failed"`, `"cannot find symbol"`, `"error:"`, etc.
3. TEST_FAILURE — `"tests failed"`, `"assertionerror"`, etc.
4. NETWORK — `"connection refused"`, `"unknownhostexception"`, etc.
5. EXCEPTION — `"exception"`, `"caused by:"`, `"stacktrace"`
6. GENERIC — exitCode ≠ 0 or `"failed"` / `"error"` in text

- **Risk:** LOW — pure function, but `"error:"` pattern is broad (causes COMPILATION classification for non-compilation errors)

---

## ErrorSoundConfigurable

**File:** `ErrorSoundConfigurable.kt`
**Purpose:** Settings UI panel registered under **Settings → Tools → Error Sound Alert**.

**Key behaviors:**
- Implements `Configurable` interface (`createComponent`, `isModified`, `apply`, `reset`, `disposeUIResources`)
- Preview plays sound immediately on combo selection (suppressed during programmatic updates via `suppressPreview` flag)
- Global mode syncs all per-kind combos to the global selection
- Custom file path refreshes all combo models to include/exclude custom option
- `apply()` stops any active preview, stops any in-progress cell edit, then saves

**Custom Regex Rules section (Phase 5 addition):**
- `CustomRuleTableModel` (inner class) — `AbstractTableModel` with deep-copy semantics on `setRules()` so table edits don't mutate settings until Apply
- `PatternValidatingRenderer` (inner class) — renders Pattern column with red tint + tooltip for invalid regex
- `ToolbarDecorator`-wrapped `JBTable` with Add/Remove actions
- Match Target column: `DefaultCellEditor` with `JComboBox` over `MatchTarget` values
- Kind column: `DefaultCellEditor` with `JComboBox` over `ALLOWED_CUSTOM_RULE_KINDS`
- Help text: rules run before built-in; target scope limitations documented

**Exit Code Rules section (Phase 6 addition):**
- `ExitCodeRuleTableModel` (inner class) — `AbstractTableModel` with internal `Row` data class; handles `SoundChoice ↔ soundId` conversion in `setRules()`/`getRules()`
- `SoundChoice` (inner data class) — wraps nullable built-in sound ID; `null` id = "(default)"; `toString()` returns label for JComboBox rendering
- `ToolbarDecorator`-wrapped `JBTable` with Add/Remove; 5 columns: Exit Code / Enabled / Kind / Sound / Suppress
- Sound column: `DefaultCellEditor(JComboBox(soundChoices))` — "(default)" + all `BuiltInSounds.all` entries
- Applies to terminal path only; exit code rules checked after custom regex rules

- **Risk:** LOW — UI-only, no business logic side effects beyond settings persistence

---

## ErrorSoundPlayer

**File:** `ErrorSoundPlayer.kt` (311 lines)
**Purpose:** Audio playback engine. Handles both real alerts and settings preview.

| Method | Description |
|---|---|
| `play(settings, kind, soundOverride?)` | Main alert path — if `soundOverride != null`, plays that built-in ID directly instead of normal resolution |
| `previewBuiltIn(id, vol, dur)` | Preview a built-in sound |
| `previewCustom(path, vol, dur)` | Preview a custom file |
| `stopPreview()` | Cancel active preview |
| `playBuiltInById(soundId, settings)` | (private) Resolves sound by ID and plays it; falls back to generated tone on failure |

**Internals:**
- Single-threaded bounded executor for alerts
- Preview uses daemon threads with token-based cancellation
- `playClipLooping()` — opens `Clip`, loops until duration expires, closes
- Volume: `FloatControl.MASTER_GAIN` with dB scaling (`20 * log10(linear)`)
- Tone fallback: generates 880 Hz WAV in-memory

- **Risk:** LOW-MEDIUM — clip resources must be closed properly; thread interruption handling for preview

---

## ErrorSoundToolWindowFactory

**File:** `ErrorSoundToolWindowFactory.kt`  
**Purpose:** Error Monitor sidebar panel. Provides quick toggles for error monitoring categories.

**Features:**
- **Project Profile section (Phase 7):** "Use project override for monitoring enabled" + "Enable monitoring for this project" checkboxes
- Master enable/disable checkbox (global)
- Per-kind checkboxes with descriptions
- Quick actions: Select All, Clear All
- Presets: All, Build Only, Runtime Only
- "Open sound settings" button → opens `ErrorSoundConfigurable`

**Behavior:**
- Project Profile checkboxes mutate `ProjectAlertSettings.state` directly
- Global enable checkbox mutates `AlertSettings.state.enabled`
- `refreshUiState()` uses `ResolvedSettingsResolver.resolve().enabled` as the effective enabled value for greying out per-kind checkboxes and building the status label
- Status label shows `(global)` or `(project override)` to clarify which setting is in effect
- When project override is unchecked, `projectEnabledCheckBox` is disabled (greyed out)

- **Risk:** LOW — UI-only

---

### SnoozeState
**File:** `SnoozeState.kt`  
**Purpose:** Transient mute state. `AtomicLong snoozeUntilEpochMillis`. Not persisted.  
**Key methods:** `isSnoozed()`, `snooze(minutes)`, `resume()`, `statusLabel()`  
**Topic:** `SnoozeState.TOPIC` (application bus) — published by `snooze()` and `resume()` so any subscriber
(e.g. the tool window) is notified of state changes regardless of call origin.  
**SnoozeListener:** `fun interface SnoozeListener { fun snoozeChanged() }` — subscribe on the app message bus.  
**Risk:** LOW — additive, no external dependencies.

---
*Last updated from code scan: 2026-04-11*
