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
| `explainLineText(line)` | Returns `CustomRuleMatch` with rule id, pattern, target, and kind |
| `explainFullOutput(text)` | Returns `CustomRuleMatch` for full-output matching |
| `explainExitCodeAndText(text, exitCode)` | Returns `CustomRuleMatch` for exit-code-and-text matching |

**Guards:** `hasLineTextRules`, `hasFullOutputRules`, `hasExitCodeAndTextRules` — `Boolean` flags to skip evaluation when no applicable rules exist.

**Value types:**
- `CustomRuleMatch(id, pattern, target, kind)` — richer match data used by runtime explanation plumbing and the settings sandbox

**Constants:**
- `MAX_RULES = 100` — rules beyond this count are ignored
- `MAX_PATTERN_LENGTH = 500` — patterns truncated to this length at normalization
- `ALLOWED_CUSTOM_RULE_KINDS` — CONFIGURATION, COMPILATION, TEST_FAILURE, NETWORK, EXCEPTION, GENERIC (NONE and SUCCESS excluded)

**Construction:** Compiles rules once; skips disabled rules, blank patterns, and invalid regex (`PatternSyntaxException` caught silently).

- **Risk:** LOW — pure computation, no side effects

---

## AlertMatchExplanation

**File:** `AlertMatchExplanation.kt`
**Purpose:** Runtime-facing explanation object describing why an alert classification was produced. This feeds Alert History and remains separate from playback.

| Field / Type | Description |
|---|---|
| `Source` | RUN_DEBUG, CONSOLE, TERMINAL |
| `Cause` | CUSTOM_REGEX_RULE, BUILT_IN_CLASSIFIER, TERMINAL_EXIT_CODE_RULE, TERMINAL_EXIT_CODE_SUPPRESSED, SUCCESS_FALLBACK, NO_MATCH, DURATION_THRESHOLD_SUPPRESSED |
| `kind` | Final `ErrorKind` associated with the explanation |
| `ruleId`, `rulePattern`, `matchTarget` | Custom regex rule details when applicable |
| `exitCode`, `commandOrConfig`, `soundOverride`, `suppressed` | Context for terminal/run config, sound override, or suppression cases |
| `summary()` | Compact debug/log representation |

- **Risk:** LOW — immutable data model; does not affect alert decisions by itself

---

## ClassificationExplanationFactory

**File:** `ClassificationExplanationFactory.kt`
**Purpose:** Small factory for constructing `AlertMatchExplanation` consistently near classification time.

| Method | Description |
|---|---|
| `customRegex(...)` | Explanation for custom regex matches |
| `builtIn(...)` | Explanation for built-in classifier matches |
| `terminalExitCodeRule(...)` | Explanation for terminal exit-code rule matches |
| `terminalExitCodeSuppressed(...)` | Explanation for terminal exit-code suppression |
| `terminalBuiltInFallback(...)` | Explanation for terminal non-zero exit fallback |
| `successFallback(...)` | Explanation for `NONE + exitCode 0 -> SUCCESS` |
| `noMatch(...)` | Explanation for no-alert classification outcomes |
| `durationThresholdSuppressed(...)` | Explanation for Run/Debug duration-threshold suppression |

- **Risk:** LOW — pure object construction; no dispatch or playback side effects

---

## RuleTestService

**File:** `RuleTestService.kt`
**Purpose:** Pure settings-side evaluator for the Rule Testing Sandbox. It explains how current custom regex rules and built-in classification would handle pasted sample output without participating in runtime detection or dispatch.

| Type / Method | Description |
|---|---|
| `SourceMode` | User-facing source selector: Run/Debug, Console, Terminal |
| `Input` | Sandbox input: rules, sample output, match target, exit code, source mode |
| `Result` | Sandbox output: custom match, built-in kind, regex validation errors, notes |
| `evaluate(input)` | Evaluates the selected target/source combination and returns explanation data for the settings UI |

**Behavior:**
- Reads the current in-memory custom rule table data supplied by `ErrorSoundConfigurable`
- Uses the same custom rule limits and regex options as runtime matching (`MAX_RULES`, `MAX_PATTERN_LENGTH`, ignore case, multiline)
- Reports invalid regex patterns for the selected target instead of silently skipping them
- Mirrors source/target applicability: Run/Debug supports all targets, Console supports LINE_TEXT, Terminal supports EXIT_CODE_AND_TEXT
- Calculates the built-in classifier result as an explanation only; it never calls `AlertDispatcher`

- **Risk:** LOW — pure computation; settings-side explanation only

---

## RuleImportExportBundle

**File:** `RuleImportExportBundle.kt`
**Purpose:** Rules-only JSON DTO for Phase 4 import/export.

**Shape:**
- `schemaVersion`
- `exportedAt`
- `pluginVersion`
- `customRules`
- `exitCodeRules`

**Nested DTOs:**
- `CustomRule(id, enabled, pattern, matchTarget, kind)`
- `ExitCodeRule(exitCode, enabled, kind, soundId?, suppress)`

**Scope boundary:** This bundle intentionally excludes full plugin settings, global sound settings, per-kind volume, success settings, project overrides, alert history, and snooze state.

- **Risk:** LOW — serialization data model only

---

## RuleImportExportResult

**File:** `RuleImportExportResult.kt`
**Purpose:** Import result value passed from the pure validation service to the settings UI.

| Field | Description |
|---|---|
| `customRules` | Valid imported custom regex rules, in JSON order |
| `exitCodeRules` | Valid imported terminal exit-code rules, in JSON order |
| `warnings` | User-facing validation notes such as generated ids, invalid regex warnings, truncation, and skipped entries |
| `skippedCount` | Count of invalid or unsupported entries skipped during import |

- **Risk:** LOW — immutable import summary

---

## RuleImportExportService

**File:** `RuleImportExportService.kt`
**Purpose:** Pure JSON serialization, parsing, and validation helper for Phase 4 rule import/export.

| Method | Description |
|---|---|
| `exportRules(customRules, exitCodeRules, pluginVersion)` | Pretty-prints schema version 1 JSON containing only custom regex and terminal exit-code rules |
| `importRules(json)` | Parses JSON, validates the schema and rule rows, preserves ordering, and returns a `RuleImportExportResult` |

**Validation behavior:**
- Requires a top-level object with `schemaVersion = 1`
- Allows missing `customRules` or `exitCodeRules` sections; missing sections import as empty lists
- Rejects unsupported top-level fields so full settings bundles are not imported accidentally
- Rejects unsupported `matchTarget`, `kind`, and unknown bundled sound ids
- Preserves invalid regex text but reports it; runtime will skip the rule until edited
- Preserves rule ids when present; generates ids only when omitted or blank
- Clamps custom rule count and pattern length using existing `CustomRuleEngine` limits

**Safety:** No network, telemetry, permanent storage, or execution of imported content.

- **Risk:** LOW — pure validation/serialization logic; does not mutate settings directly

---

## AlertDispatcher

**File:** `AlertDispatcher.kt`  
**Purpose:** Single routing choke-point. Gate order: `SnoozeState` → `AlertMonitoring` → `AlertEventGate` → `AlertHistoryService` → `ErrorSoundPlayer` → visual notification.

| Method | Signature | Description |
|---|---|---|
| `tryAlert` | `(key: String, settings: State, kind: ErrorKind, project: Project? = null, soundOverride: String? = null, explanation: AlertMatchExplanation? = null)` | Routes through all gates; optionally logs explanation and shows balloon notification |
| `showNotification` | `(settings: State, kind: ErrorKind, project: Project)` | Shows BALLOON notification with kind title + "Open Settings" / "Mute 1 hr" actions |

- **Inputs:** deduplication key, current settings state, detected error kind, optional project
- **Outputs:** none (fire-and-forget side effect)
- **Side effects:** records accepted alert history; may trigger audio playback; may show balloon notification
- **Explanation policy:** `explanation` is for runtime diagnostics, Alert History context, and future UI. It does not alter gate order, playback selection, or notification content.
- **History policy:** records only after snooze, monitoring, and deduplication gates accept the alert. Suppressed attempts are not recorded in Phase 3.
- **Risk:** LOW-MEDIUM — thin routing layer, but every detection path depends on it

---

## AlertHistoryService

**File:** `AlertHistoryService.kt`
**Purpose:** Application service that stores recent accepted alert events for the Error Monitor Alert History panel.

| Member | Description |
|---|---|
| `MAX_ENTRIES = 100` | Hard bound for retained in-memory history |
| `record(project, kind, soundOverride, explanation)` | Adds a newest-first entry after dispatcher gates accept an alert, then publishes a bus refresh |
| `snapshot()` | Returns a stable newest-first copy for UI rendering |
| `clear()` | Removes all entries and publishes a refresh when history was non-empty |
| `TOPIC` | Application message-bus topic used by the tool window to refresh the table |
| `Entry` | Timestamp, project name, source, kind, cause, rule id/pattern, exit code, command/config context, and sound override status |

**Behavior:**
- In-memory only; no persistent storage, network calls, telemetry, or user output archival
- Bounded to the latest 100 accepted alerts
- Newest-first ordering is preserved in the service snapshot
- Clearable from the Error Monitor UI
- Message-bus refreshed so an open Error Monitor updates when alerts are recorded or cleared

- **Risk:** LOW — bounded application memory state; no detection decisions or playback behavior by itself

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
- Phase 2: classification accumulators now retain explanation-capable results (`CustomRuleMatch` / `BuiltInClassificationResult`) so the final alert can carry an `AlertMatchExplanation`
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

**Phase 2 explanation value types:**
- `BuiltInClassificationResult(kind, cause)` — preserves the built-in classifier cause such as CONFIGURATION_PATTERN, NON_ZERO_EXIT_CODE, GENERIC_TEXT_PATTERN, or NO_MATCH
- `TerminalExitCodeRuleMatch(exitCode, kind, soundId, suppress)` — records which terminal exit-code rule matched

| Method | Description |
|---|---|
| `ErrorClassifier.detect(outputText, exitCode)` | Full-text classification against hardcoded string patterns |
| `ErrorClassifier.detectWithExplanation(outputText, exitCode)` | Same classification as `detect()`, plus built-in cause reporting |
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

**Rule Import / Export controls (Phase 4 addition):**
- Adds `Export Rules…` and `Import Rules…` controls near the rule sections
- Export stops active rule cell editing and serializes the current table-model state, including unsaved edits
- Import reads a user-selected local JSON file, delegates strict parsing/validation to `RuleImportExportService`, shows a confirmation summary, then replaces only the custom regex and terminal exit-code rule table models
- Imported changes follow normal settings semantics: they are not persisted until Apply; Reset reloads persisted settings and discards imported-but-not-applied table state
- Overwrite protection is explicit for export; import/export is local file based only

**Custom Regex Rules section (Phase 5 addition):**
- `CustomRuleTableModel` (inner class) — `AbstractTableModel` with deep-copy semantics on `setRules()` so table edits don't mutate settings until Apply
- `PatternValidatingRenderer` (inner class) — renders Pattern column with red tint + tooltip for invalid regex
- `ToolbarDecorator`-wrapped `JBTable` with Add/Remove actions
- Match Target column: `DefaultCellEditor` with `JComboBox` over `MatchTarget` values
- Kind column: `DefaultCellEditor` with `JComboBox` over `ALLOWED_CUSTOM_RULE_KINDS`
- Help text: rules run before built-in; target scope limitations documented

**Rule Testing Sandbox section (Phase 1 roadmap addition):**
- Settings-side tool below Custom Regex Rules; does not save settings or trigger alerts
- Controls: Source, Match Target, optional Exit Code, sample output, Test Rules button, read-only result area
- Uses `RuleTestService.evaluate()` against the current table model so unsaved rule edits can be tested before Apply
- Shows custom rule match status, matched rule row/id/pattern, resulting `ErrorKind`, built-in classifier fallback, regex validation errors, no-match message, and source/target applicability notes

**Exit Code Rules section (Phase 6 addition):**
- `ExitCodeRuleTableModel` (inner class) — `AbstractTableModel` with internal `Row` data class; handles `SoundChoice ↔ soundId` conversion in `setRules()`/`getRules()`
- `SoundChoice` (inner data class) — wraps nullable built-in sound ID; `null` id = "(default)"; `toString()` returns label for JComboBox rendering
- `ToolbarDecorator`-wrapped `JBTable` with Add/Remove; 5 columns: Exit Code / Enabled / Kind / Sound / Suppress
- Sound column: `DefaultCellEditor(JComboBox(soundChoices))` — "(default)" + all `BuiltInSounds.all` entries
- Applies to terminal path only; exit code rules checked after custom regex rules

- **Risk:** LOW — UI-only, no business logic side effects beyond settings persistence

---

## ErrorSoundPlayer

**File:** `ErrorSoundPlayer.kt`
**Purpose:** Audio playback engine. Handles both real alerts and settings preview.

| Method | Description |
|---|---|
| `play(settings, kind, soundOverride?)` | Main alert path. Calls `resolveEffectiveVolumePercent()` once and passes result to all playback helpers |
| `resolveEffectiveVolumePercent(settings, kind)` | **Phase 8** — returns per-kind override Int? or falls back to `settings.volumePercent` |
| `previewBuiltIn(id, vol, dur)` | Preview a built-in sound |
| `previewCustom(path, vol, dur)` | Preview a custom file |
| `stopPreview()` | Cancel active preview |

**Internals:**
- Single-threaded bounded executor for alerts
- Preview uses daemon threads with token-based cancellation
- `playClipLooping(bytes, settings, volumePercent)` — explicit `volumePercent` arg (Phase 8); no longer reads `settings.volumePercent` internally
- Volume: `FloatControl.MASTER_GAIN` with dB scaling (`20 * log10(linear)`)
- Tone fallback: generates 880 Hz WAV in-memory

- **Risk:** LOW-MEDIUM — clip resources must be closed properly; thread interruption handling for preview

---

## ErrorSoundToolWindowFactory

**File:** `ErrorSoundToolWindowFactory.kt`  
**Purpose:** Error Monitor sidebar panel. Provides quick toggles for error monitoring categories and a read-only Alert History view.

**Features:**
- **Project Profile section (Phase 7):** "Use project override for monitoring enabled" + "Enable monitoring for this project" checkboxes
- Master enable/disable checkbox (global)
- Per-kind checkboxes with descriptions
- Quick actions: Select All, Clear All
- Presets: All, Build Only, Runtime Only
- Alert History table: Time, Source, Kind, Cause, Context
- Clear history action
- "Open sound settings" button → opens `ErrorSoundConfigurable`

**Behavior:**
- Project Profile checkboxes mutate `ProjectAlertSettings.state` directly
- Global enable checkbox mutates `AlertSettings.state.enabled`
- `refreshUiState()` uses `ResolvedSettingsResolver.resolve().enabled` as the effective enabled value for greying out per-kind checkboxes and building the status label
- Status label shows `(global)` or `(project override)` to clarify which setting is in effect
- When project override is unchecked, `projectEnabledCheckBox` is disabled (greyed out)
- Alert History subscribes to `AlertHistoryService.TOPIC`, renders newest-first snapshots, and refreshes on the EDT
- Context may include project/config/command, exit code, matched rule id/pattern, and sound override status

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
*Last updated from code scan: 2026-05-02*
