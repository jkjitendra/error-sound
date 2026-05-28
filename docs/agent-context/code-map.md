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

## SuppressionRuleEngine

**File:** `SuppressionRuleEngine.kt`
**Purpose:** Compiles and caches user-defined ignore/suppression regex rules from `AlertSettings.State.suppressionRules`. Provides target-specific match methods that detection paths use before alert dispatch.

| Method | Description |
|---|---|
| `matchLineText(line)` | Returns true when a LINE_TEXT suppression rule matches a single line/chunk |
| `matchFullOutput(text)` | Returns true when a FULL_OUTPUT suppression rule matches complete buffered output |
| `matchExitCodeAndText(text, exitCode)` | Returns true when an EXIT_CODE_AND_TEXT suppression rule matches `"exitcode:N\n<text>"` |
| `explainLineText(line)` | Returns `SuppressionRuleMatch` with rule id, pattern, target, and description |
| `explainFullOutput(text)` | Returns `SuppressionRuleMatch` for full-output matching |
| `explainExitCodeAndText(text, exitCode)` | Returns `SuppressionRuleMatch` for exit-code-and-text matching |

**Guards:** `hasLineTextRules`, `hasFullOutputRules`, `hasExitCodeAndTextRules`.

**Constants:** Reuses custom rule limits: `MAX_RULES = 100`, `MAX_PATTERN_LENGTH = 500`; adds `MAX_DESCRIPTION_LENGTH = 200`.

**Runtime policy:** Disabled rules, blank patterns, and invalid regex patterns are skipped safely. Matching suppression rules return before `AlertDispatcher`, so no sound, notification, or Alert History entry is produced.

- **Risk:** LOW — pure computation, no side effects

---

## AlertMatchExplanation

**File:** `AlertMatchExplanation.kt`
**Purpose:** Runtime-facing explanation object describing why an alert classification was produced. This feeds Alert History and remains separate from playback.

| Field / Type | Description |
|---|---|
| `Source` | RUN_DEBUG, CONSOLE, TERMINAL |
| `Cause` | CUSTOM_REGEX_RULE, BUILT_IN_CLASSIFIER, TERMINAL_EXIT_CODE_RULE, TERMINAL_EXIT_CODE_SUPPRESSED, SUPPRESSION_RULE, SUCCESS_FALLBACK, NO_MATCH, DURATION_THRESHOLD_SUPPRESSED |
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
| `suppressionRule(...)` | Debug explanation for ignore/suppression rule matches before dispatch |
| `terminalBuiltInFallback(...)` | Explanation for terminal non-zero exit fallback |
| `successFallback(...)` | Explanation for `NONE + exitCode 0 -> SUCCESS` |
| `noMatch(...)` | Explanation for no-alert classification outcomes |
| `durationThresholdSuppressed(...)` | Explanation for Run/Debug duration-threshold suppression |
| `runConfigurationOverrideSuppressed(...)` | Debug explanation for Run/Debug suppression by a per-run-configuration override |

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
- `suppressionRules` (schema version 2)
- `exitCodeRules`

**Nested DTOs:**
- `CustomRule(id, enabled, pattern, matchTarget, kind)`
- `SuppressionRule(id, enabled, pattern, matchTarget, description)`
- `ExitCodeRule(exitCode, enabled, kind, soundId?, suppress)`

**Scope boundary:** This bundle intentionally excludes full plugin settings, global sound settings, per-kind volume, success settings, project profiles/overrides, alert history, and snooze state.

- **Risk:** LOW — serialization data model only

---

## RuleImportExportResult

**File:** `RuleImportExportResult.kt`
**Purpose:** Import result value passed from the pure validation service to the settings UI.

| Field | Description |
|---|---|
| `customRules` | Valid imported custom regex rules, in JSON order |
| `suppressionRules` | Valid imported suppression rules, in JSON order |
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
| `exportRules(customRules, suppressionRules, exitCodeRules, pluginVersion)` | Pretty-prints schema version 2 JSON containing custom regex, suppression, and terminal exit-code rules |
| `importRules(json)` | Parses JSON, validates the schema and rule rows, preserves ordering, and returns a `RuleImportExportResult` |

**Validation behavior:**
- Requires a top-level object with `schemaVersion = 1` or `schemaVersion = 2`
- Exports `schemaVersion = 2`; schema version 1 imports remain supported for older exports without `suppressionRules`
- Allows missing `customRules`, `suppressionRules`, or `exitCodeRules` sections; missing sections import as empty lists
- Rejects unsupported top-level fields so full settings bundles are not imported accidentally
- Rejects unsupported `matchTarget`, `kind`, and unknown bundled sound ids
- Preserves invalid regex text but reports it; runtime will skip the rule until edited
- Preserves rule ids when present; generates ids only when omitted or blank
- Clamps custom/suppression rule count and pattern length using existing rule limits; suppression descriptions are trimmed and capped

**Safety:** No network, telemetry, permanent storage, or execution of imported content.

- **Risk:** LOW — pure validation/serialization logic; does not mutate settings directly

---

## RulePresetBundle

**File:** `RulePresetBundle.kt`
**Purpose:** Built-in preset data model for Phase 5 Rule Presets.

| Field | Description |
|---|---|
| `id` | Stable bundle id |
| `displayName` | User-facing combo-box label |
| `description` | Short UI description for the selected preset |
| `customRules` | Preset Custom Regex Rules to append |
| `exitCodeRules` | Conservative Terminal Exit-Code Rules to append |

**Bundles:** Java / Spring Boot, Gradle / Maven, Node.js / npm / pnpm, Python / pytest, Docker / Kubernetes, and Frontend test runners (Jest / Vitest / Cypress / Playwright).

- **Risk:** LOW — immutable bundled data model

---

## RulePresetApplyResult

**File:** `RulePresetApplyResult.kt`
**Purpose:** Summary of a planned preset append operation before the settings UI mutates table models.

| Field | Description |
|---|---|
| `preset` | Selected preset bundle |
| `customRulesToAdd` | Non-duplicate custom rules to append |
| `exitCodeRulesToAdd` | Non-duplicate exit-code rules to append |
| `skippedDuplicateCustomRuleIds` | Preset custom rule ids already present in the current table model |
| `skippedDuplicateExitCodes` | Preset exit codes already present in the current table model |
| `warnings` | User-facing notes, mainly custom rule count-limit skips |

- **Risk:** LOW — immutable append summary

---

## RulePresetService

**File:** `RulePresetService.kt`
**Purpose:** Pure helper containing bundled local preset definitions and duplicate-aware append planning.

| Method / Member | Description |
|---|---|
| `bundles` | Ordered list of available preset bundles |
| `prepareApply(preset, currentCustomRules, currentExitCodeRules)` | Computes which preset rules can be appended without mutating settings |

**Behavior:**
- Appends only Custom Regex Rules and conservative Terminal Exit-Code Rules
- Skips duplicate custom rules by stable rule id
- Skips exit-code rules when the current table already has the same exit code
- Preserves all existing user-created rules and appends preset rules after them
- Uses existing custom rule limits and allowed kinds from `CustomRuleEngine`
- Presets are bundled locally; no network, telemetry, remote preset downloads, script execution, file writes, or terminal imports

- **Risk:** LOW — pure settings-side planning; does not change runtime alert behavior

---

## ErrorSoundDiagnosticsService

**File:** `ErrorSoundDiagnosticsService.kt`
**Purpose:** Settings-side diagnostics and self-test helper for Phase 8. Builds a local applied-status snapshot and exposes safe self-test actions without entering runtime detection or dispatch.

| Method / Type | Description |
|---|---|
| `Snapshot(rows, notes)` | Immutable diagnostics summary data rendered by settings UI |
| `SelfTestResult(success, message)` | User-facing status for sound/notification self-test actions |
| `buildSnapshot()` | Reads applied `AlertSettings`, `SnoozeState`, `AlertHistoryService`, rule counts, Run/Debug run-configuration override count, preset availability, import/export schema support, terminal integration status, selected profile merge policy, effective precedence, repo/workspace layer status, repo profile status/schema/name/warnings, and active project profile override categories when an active project is available |
| `testErrorSound()` | Plays a GENERIC error sound through the preview path using current applied settings |
| `testSuccessSound()` | Plays a SUCCESS sound through the preview path when enabled by current applied settings |
| `showTestNotification(project?)` | Sends a real IntelliJ Platform balloon notification using `NotificationGroupManager`, group id `Error Sound Alert`, and `NotificationType.INFORMATION` |

**Notification policy:** resolves an explicit project if supplied, otherwise uses the first non-disposed open project, otherwise notifies with `null`; delivery is scheduled on the EDT. The normal success path returns inline status and does not show a modal OK dialog. Warning dialogs are left to the UI for failure paths such as a missing notification group.

**Safety:** Does not call `AlertDispatcher`, record Alert History, mutate settings, write files, create persistent diagnostic logs, use network/telemetry, or touch terminal reflection logic.

- **Risk:** LOW — local settings-side helper; no runtime alert behavior changes

---

## AlertDispatcher

**File:** `AlertDispatcher.kt`  
**Purpose:** Single routing choke-point. Gate order: `SnoozeState` → `AlertMonitoring` → `AlertEventGate` → `AlertHistoryService` → `ErrorSoundPlayer` → visual notification.

| Method | Signature | Description |
|---|---|---|
| `tryAlert` | `(key: String, settings: State, kind: ErrorKind, project: Project? = null, soundOverride: String? = null, explanation: AlertMatchExplanation? = null)` | Routes through all gates; optionally logs explanation and shows balloon notification |
| `showNotification` | `(settings: State, kind: ErrorKind, project: Project, explanation?)` | Shows BALLOON notification with action buttons after dispatcher gates accept an alert |
| `formatAlertDetails` | `(kind, explanation)` | Formats capped alert detail fields for the Show alert details dialog |

- **Inputs:** deduplication key, current settings state, detected error kind, optional project
- **Outputs:** none (fire-and-forget side effect)
- **Side effects:** records accepted alert history; may trigger audio playback; may show balloon notification with actions
- **Explanation policy:** `explanation` is for runtime diagnostics, Alert History context, and capped notification details. It does not alter gate order or playback selection.
- **History policy:** records only after snooze, monitoring, and deduplication gates accept the alert. Suppressed attempts are not recorded in Phase 3.
- **Notification actions:** Open Settings, Open Error Monitor, Mute 1 hr, Disable this kind / Disable success alerts, and Show alert details when explanation data exists.
- **Alert details:** source, kind, cause, exit code, command/config, rule id/pattern, match target, sound override, and summary are capped to short strings; full console output is not shown or persisted.
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
- `onTextAvailable()` — LINE_TEXT suppression rules checked first; if not suppressed, LINE_TEXT custom rules run per chunk and fall back to `ErrorClassifier.detect()` when no custom match exists
- `processTerminated()` — FULL_OUTPUT and EXIT_CODE_AND_TEXT suppression rules checked first; if not suppressed, FULL_OUTPUT then EXIT_CODE_AND_TEXT custom rules apply to the full buffer before built-in fallback
- Phase 2: classification accumulators now retain explanation-capable results (`CustomRuleMatch` / `BuiltInClassificationResult`) so the final alert can carry an `AlertMatchExplanation`
- Priority system for chunk accumulation: CONFIGURATION > COMPILATION > TEST_FAILURE > NETWORK > EXCEPTION > GENERIC > NONE
- If final kind is NONE and exitCode == 0, converts to SUCCESS
- Resolves effective global/repo/workspace settings, then applies the first matching enabled Run Configuration Override for that Run/Debug execution
- Run Configuration Overrides can suppress all alerts, suppress success alerts, or override min duration, alert duration, play-once, and visual notification behavior for that run-specific effective settings copy
- Duration threshold: if `elapsedMillis < minProcessDurationSeconds * 1000`, alert suppressed (Run/Debug only)
- Dedup key: `"exec:{handlerIdentityHash}:{errorKind}"`
- Routes through `AlertDispatcher.tryAlert()` only when no suppression rule matched

- Phase 12: `processTerminated()` resolves effective settings via `ResolvedSettingsResolver.getInstance(env.project).resolve()`, then applies Run/Debug-only run-configuration overrides before duration threshold and dispatch
- **Risk:** LOW — straightforward listener pattern

---

## RunConfigurationOverrideMatchType

**File:** `RunConfigurationOverrideMatchType.kt`
**Purpose:** Stored/display enum for Run/Debug run-configuration override matching.

**Values:** `EXACT_NAME`, `NAME_CONTAINS`, `NAME_REGEX`, `TYPE_CONTAINS`.

**Helpers:** `displayName`, `toString()`, and `fromStored(value)`; unknown values normalize to `EXACT_NAME`.

- **Risk:** LOW — pure enum

---

## RunConfigurationOverrideEngine

**File:** `RunConfigurationOverrideEngine.kt`
**Purpose:** Pure Run/Debug override matcher and applier. Evaluates `AlertSettings.RunConfigurationOverrideState` rows against the current run configuration context.

| Method / Type | Description |
|---|---|
| `Context(configurationName, configurationTypeId?, configurationTypeName?)` | Runtime input extracted from `ExecutionEnvironment` |
| `Match(rowNumber, override)` | First matching enabled override row |
| `firstMatch(context)` | Returns the first enabled matching row; blank and invalid regex rows are skipped safely |
| `applyTo(settings, match)` | Returns a run-specific `AlertSettings.State` copy with duration/play-once/visual/min-duration overrides applied |

**Limits:** `MAX_OVERRIDES = 100`, `MAX_DESCRIPTION_LENGTH = 240`; pattern length follows `CustomRuleEngine.MAX_PATTERN_LENGTH` during settings normalization.

**Scope boundary:** Does not mutate global settings, project profile state, repo profile data, or the stored override row. Does not apply to Console or Terminal paths.

- **Risk:** LOW-MEDIUM — affects Run/Debug dispatch eligibility and run-specific settings only

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
| `handleCommandFinished` | private | Precedence: (1) suppression EXIT_CODE_AND_TEXT regex returns before dispatch, (2) custom EXIT_CODE_AND_TEXT regex, (3) exit code rules via `classifyTerminal()` with suppression check, (4) built-in fallback. Uses `ResolvedSettingsResolver` for the effective settings state passed to `AlertDispatcher` |
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
- `data class SuppressionRuleState(id, enabled, pattern, matchTarget, description)` — a single user-defined suppression rule
- `data class ExitCodeRuleState(exitCode, enabled, kind, soundId?, suppress)` — a single terminal exit-code rule
- `data class RunConfigurationOverrideState(...)` — a Run/Debug-only per-run-configuration override row

**State fields:**
- `enabled` — master toggle
- `monitorConfiguration/Compilation/TestFailure/Network/Exception/Generic` — per-kind monitor flags
- `monitorSuccess` — success monitoring flag (default: `false`)
- `volumePercent` (0–100), `alertDurationSeconds` (1–10)
- `useActualSoundDuration: Boolean = false` — optional play-once mode; when true, file-based playback uses the selected clip's actual length instead of the configured alert duration
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
- `suppressionRules: MutableList<SuppressionRuleState> = mutableListOf()` — user-defined regex rules that silence matching contexts before dispatch
- `exitCodeRules: MutableList<ExitCodeRuleState>` — terminal exit code → kind/sound/suppress mapping (4 defaults: 130 suppress, 127/137/143 GENERIC)
- `runConfigurationOverrides: MutableList<RunConfigurationOverrideState> = mutableListOf()` — Run/Debug-only per-run-configuration overrides

**Methods:**
- `getCompiledRuleEngine(): CustomRuleEngine` — returns cached compiled rule engine; lazily created, invalidated by `loadState()`
- `getCompiledSuppressionRuleEngine(): SuppressionRuleEngine` — returns cached compiled suppression engine; lazily created, invalidated by `loadState()`

**Validation:** `loadState()` normalizes sound IDs, clamps numeric values, normalizes custom rules (count, pattern length, matchTarget, kind, IDs), normalizes suppression rules (count, pattern length, matchTarget, IDs, description length), normalizes exit code rules (kind, soundId blank/CUSTOM_FILE → null), and normalizes run-configuration overrides (max 100, IDs, match type, pattern length, duration ranges, description length). Blank/invalid patterns are preserved and skipped safely at runtime.

- **Risk:** LOW — standard `PersistentStateComponent` pattern

---

## ProfileMergePolicy

**File:** `ProfileMergePolicy.kt`
**Purpose:** Workspace/project-scoped enum for Phase 11 Profile Merge Policy UI. Controls how `ResolvedSettingsResolver` combines global settings, the optional repo profile, and workspace project profile overrides.

**Values:**
- `STANDARD_WORKSPACE_WINS` — default; `Global -> repo profile -> workspace project profile`
- `IGNORE_REPO_PROFILE` — `Global -> workspace project profile`
- `REPO_PROFILE_WINS` — `Global -> workspace project profile -> repo profile`
- `GLOBAL_ONLY` — global settings only

**Helpers:** `displayName`, `effectivePrecedenceText`, and `fromStored(value)` for resilient XML string normalization. Unknown or missing stored values normalize to `STANDARD_WORKSPACE_WINS`.

- **Risk:** LOW — pure enum; no runtime behavior beyond resolver policy selection

---

## ProjectAlertSettings

**File:** `ProjectAlertSettings.kt`  
**Purpose:** Project-scoped settings service for Full Per-Project Profiles and Profile Merge Policy UI. Persisted to workspace storage (`WORKSPACE_FILE`) so overrides and selected policy are per-workspace, not shared across clones.

**Level:** `@Service(Service.Level.PROJECT)`

**State fields:**
- `useProfileOverrides: Boolean = false` — master opt-in switch; when false all project override fields are ignored and global settings are inherited
- `profileMergePolicy: String = STANDARD_WORKSPACE_WINS` — workspace-scoped merge policy stored as a string and normalized through `ProfileMergePolicy.fromStored()`
- `useOverride`, `enabledOverride` — master monitoring enabled override; legacy enabled-only state is preserved and migrated by normalization
- `useMonitoringOverrides` plus monitor-kind override booleans — CONFIGURATION, COMPILATION, TEST_FAILURE, NETWORK, EXCEPTION, GENERIC, SUCCESS
- `useSoundOverrides` plus built-in/global sound mode, global built-in sound id, per-kind sound enabled/id, and success sound enabled/id
- `useVolumeOverrides` plus global volume and nullable per-kind volume overrides
- `useDurationOverrides` plus `alertDurationSecondsOverride` and `useActualSoundDurationOverride`
- `useVisualNotificationOverrides` plus visual notification master/error/success flags
- `useMinProcessDurationOverride` plus `minProcessDurationSecondsOverride`

**Methods:**
- `effectiveEnabledOverride(): Boolean?` — returns `null` (inherit global) or the enabled override value
- `copyGlobalSettings(global)` — seeds the project profile from current global settings and enables all supported override groups
- `resetOverrides()` — clears the project profile back to inheritance defaults
- `activeOverrideLabels()` — returns active override category labels for diagnostics
- `mergePolicy()` — returns the normalized `ProfileMergePolicy`
- `getInstance(project): ProjectAlertSettings` — companion helper

**Scope boundary:** Rules, presets, rule import/export schema, Alert History, terminal integration, and custom file path remain global/application-level. Repo profile data is separate and read from `.error-sound-alert.json`; the merge policy is not stored in the repo profile file.

- **Risk:** LOW-MEDIUM — workspace-scoped persistent state; storage path and migration behavior must stay stable

---

## RepoProfileLoadResult

**File:** `RepoProfileLoadResult.kt`
**Purpose:** Immutable status object returned by `RepoProfileService` after attempting to load `.error-sound-alert.json`.

**Fields:**
- `status` — NO_PROJECT_BASE_PATH, ABSENT, LOADED, DISABLED, or INVALID
- `path` — resolved project-root file path when available
- `profile` — parsed `RepoProfileState` when valid
- `warnings` — validation notes for unknown fields, invalid values, clamping, or read/parse failures

**Derived properties:** `isFilePresent`, `isApplied`.

- **Risk:** LOW — data model only

---

## RepoProfileState

**File:** `RepoProfileState.kt`
**Purpose:** Schema version 1 model for team-shared repo profile defaults.

**Top-level fields:**
- `schemaVersion` — must be `1`
- `profileName` — optional display name
- `enabled` — disables the repo profile layer when false
- `overrides` — optional profile-default values

**Supported override categories:** master enabled, monitoring kinds, built-in/global sound behavior, per-kind sound enabled/id, success sound enabled/id, global volume, per-kind volume overrides, alert duration, play-once mode, visual notifications, and minimum process duration.

**Methods:**
- `applyTo(base)` — returns a copied `AlertSettings.State` with repo profile fields layered over the base state

**Scope boundary:** No custom regex rules, suppression rules, terminal exit-code rules, rule presets, rule import/export, Alert History, custom audio file paths, network state, telemetry, or terminal reflection settings.

- **Risk:** LOW — pure model/application helper

---

## RepoProfileService

**File:** `RepoProfileService.kt`
**Purpose:** Project service that reads optional team-shared repo profile defaults from `project.basePath/.error-sound-alert.json`.

**Level:** `@Service(Service.Level.PROJECT)` registered in `plugin.xml`.

| Method | Description |
|---|---|
| `load(refresh = false)` | Lazily loads and caches the repo profile result for the project |
| `reload()` | Forces a re-read from disk |
| `openProfileFile()` | Opens the existing repo profile file in the editor when present |
| `getInstance(project)` | Companion helper |

**Validation / safety:**
- Reads only `.error-sound-alert.json` directly under `project.basePath`
- Does not scan parent directories, perform network calls, execute content, or write files
- Requires top-level JSON object and `schemaVersion = 1`
- Missing fields mean no repo override for that field
- Unknown fields are ignored with warnings
- Invalid sound ids and unsupported enum names are ignored with warnings
- Numeric values are clamped to supported ranges where applicable
- Invalid JSON, invalid schema, unreadable file, non-regular file, or oversized file returns INVALID and leaves runtime behavior on global + workspace overrides

- **Risk:** LOW-MEDIUM — reads untrusted local JSON; must remain read-only and fail safe

---

## ResolvedSettingsResolver

**File:** `ResolvedSettingsResolver.kt`  
**Purpose:** Project service that layers repo-shared and workspace project profile overrides on top of global application settings and returns the effective `AlertSettings.State` for the current project.

**Level:** `@Service(Service.Level.PROJECT)`

| Method | Description |
|---|---|
| `resolve()` | Returns an `AlertSettings.State` copy after applying the selected `ProfileMergePolicy` |
| `getInstance(project)` | Companion helper |

**Resolution policies:**
- `STANDARD_WORKSPACE_WINS` — `Global -> repo profile -> workspace project profile`
- `IGNORE_REPO_PROFILE` — `Global -> workspace project profile`
- `REPO_PROFILE_WINS` — `Global -> workspace project profile -> repo profile`
- `GLOBAL_ONLY` — global settings only

**Resolution safety:** Does not mutate global `AlertSettings.State`, repo profile model, or project `ProjectAlertSettings.State`. Missing/disabled/invalid repo profile data is ignored safely while warnings remain visible. Workspace project profile enablement still controls whether workspace overrides apply, except `GLOBAL_ONLY` bypasses them.

**Global/application-level:** `customRules`, `suppressionRules`, `exitCodeRules`, rule presets, rule import/export, Alert History, terminal reflection behavior, and custom sound file path.

**Usage pattern:** All three detection paths call `ResolvedSettingsResolver.getInstance(project).resolve()` instead of `AlertSettings.getInstance().state` when passing settings to `AlertDispatcher.tryAlert()`.

- **Risk:** LOW-MEDIUM — central settings resolver used by all detection paths

---

## ProjectProfilePanel

**File:** `ProjectProfilePanel.kt`
**Purpose:** Error Monitor UI panel for workspace-scoped project profile overrides.

**Controls:**
- Repo profile status label plus **Reload repo profile** and **Open repo profile file** actions
- **Profile merge policy** dropdown and effective precedence text
- Top-level `Use project profile overrides`, `Copy current global settings`, and `Reset project overrides`
- Collapsible override groups for master monitoring, monitoring kinds, built-in sound behavior, volume, duration / play once, visual notifications, and minimum process duration
- Advanced groups stay grouped under Project Profile so the narrow Error Monitor tool window remains readable

**Behavior:**
- Clarifies selected merge behavior and whether the repo layer is included or skipped
- Reload action refreshes the cached repo profile result; open-file action opens the existing `.error-sound-alert.json`
- Mutates `ProjectAlertSettings.state` for the current project only
- Copy action seeds project profile values from current global `AlertSettings.State`
- Reset action clears project overrides and returns the project to inheritance while preserving the selected merge policy
- Collapsing sections does not change active overrides or lose values

- **Risk:** LOW — project UI only; persistence remains in `ProjectAlertSettings`

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
1. Suppression LINE_TEXT rules — checked first; matching rules return before dispatch
2. Custom LINE_TEXT rules (via `CustomRuleEngine.matchLineText()`)
3. Built-in `errorPattern` regex — only reached if no custom LINE_TEXT rule matched

**FULL_OUTPUT and EXIT_CODE_AND_TEXT targets are explicitly skipped** — unsupported in this path for both custom and suppression rules.

**ErrorDetectionFilter patterns (built-in):** Exception, Error, FATAL, `Caused by:`, stack trace lines, BUILD FAILED, FAILURE:, Tests failed, compilation failed, command not found.

**Dedup key format:** `"console:{project.locationHash}:{errorKind}"`

- Phase 9: `AlertDispatcher.tryAlert()` call uses `ResolvedSettingsResolver.getInstance(project).resolve()` for the settings state — selected project profile overrides are honoured
- **Side effects:** Calls `AlertDispatcher.tryAlert()` only when not suppressed — returns `null` filter result (no text modification)
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
- Import reads a user-selected local JSON file, delegates strict parsing/validation to `RuleImportExportService`, shows a confirmation summary, then replaces only the custom regex, suppression, and terminal exit-code rule table models
- Imported changes follow normal settings semantics: they are not persisted until Apply; Reset reloads persisted settings and discards imported-but-not-applied table state
- Overwrite protection is explicit for export; import/export is local file based only

**Rule Presets section (Phase 5 addition):**
- Adds a Rule Presets combo, selected-preset description, and **Add Preset Rules** action near the rule controls
- Uses `RulePresetService.prepareApply()` to build a confirmation summary before appending anything
- Appends only non-duplicate preset custom regex rules and terminal exit-code rules to the current table models
- Duplicate custom rule ids and existing exit codes are skipped; existing user-created rules are preserved
- Added preset rules follow normal settings semantics: they are not persisted until Apply; Reset discards unapplied preset additions

**Play Once Sound Duration controls (1.1.14 addition):**
- Adds **Use actual sound file duration (play once)** checkbox in the audio settings area
- Disables the alert duration slider and value label while selected
- Preview calls pass the checkbox state through to `ErrorSoundPlayer`
- Checkbox changes follow normal settings semantics: they are not persisted until Apply; Reset discards unapplied changes

**Diagnostics / Self-Test section (Phase 8 roadmap addition):**
- Adds a Settings-only **Diagnostics / Self-Test** section under **Settings / Preferences → Tools → Error Sound Alert**
- Renders local applied-status rows from `ErrorSoundDiagnosticsService.buildSnapshot()`
- Provides safe self-test actions: **Test error sound**, **Test success sound**, and **Test visual notification**
- Sound tests use preview playback and respect Play Once Sound Duration where applicable
- Visual notification test uses `NotificationGroupManager`, existing group id `Error Sound Alert`, `NotificationType.INFORMATION`, active project fallback, and EDT delivery
- Normal visual notification success updates inline status only; no modal OK dialog is shown
- Diagnostics are not part of the Error Monitor tool window and do not mutate settings or Alert History

**Suppression Rules section (Phase 6 roadmap addition):**
- Adds Suppression Rules table near rule controls
- Columns: Enabled, Pattern, Match Target, Description
- Uses `PatternValidatingRenderer` so invalid regex text is preserved and highlighted for editing
- `SuppressionRuleTableModel` deep-copies state so table edits do not mutate settings until Apply
- Apply persists `suppressionRules`; Reset discards unapplied suppression-rule changes
- Import/export uses current suppression table-model state, including unsaved edits

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

**Run Configuration Overrides section (Phase 12):**
- `RunConfigurationOverrideTableModel` (inner class) — `AbstractTableModel` for `AlertSettings.RunConfigurationOverrideState`
- Settings-side table under **Run Configuration Overrides**
- Columns cover enabled state, match type, pattern, suppress-all, suppress-success, min-duration override, alert-duration override, play-once override, visual-notification overrides, and description
- Applies to Run/Debug only; first matching enabled row wins at runtime
- Not included in rule import/export or repo profile schema

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
| `previewBuiltIn(id, vol, dur, useActualSoundDuration)` | Preview a built-in sound, using play-once mode when requested |
| `previewCustom(path, vol, dur, useActualSoundDuration)` | Preview a custom file, using play-once mode when requested |
| `stopPreview()` | Cancel active preview |

**Internals:**
- Single-threaded bounded executor for alerts
- Preview uses daemon threads with token-based cancellation
- `playClipLooping(bytes, settings, volumePercent)` — explicit `volumePercent` arg (Phase 8); no longer reads `settings.volumePercent` internally
- `settings.useActualSoundDuration == false` keeps the default configured-duration looping/restart behavior
- `settings.useActualSoundDuration == true` starts the opened clip once, waits for the clip length or stop/close event, then stops/flushes/closes safely
- Preview follows the same play-once vs configured-duration branch where practical
- Volume: `FloatControl.MASTER_GAIN` with dB scaling (`20 * log10(linear)`)
- Tone fallback: generates 880 Hz WAV in-memory

- **Risk:** LOW-MEDIUM — clip resources must be closed properly; thread interruption handling for preview

---

## ErrorSoundToolWindowFactory

**File:** `ErrorSoundToolWindowFactory.kt`  
**Purpose:** Error Monitor sidebar panel. Provides quick toggles for error monitoring categories and a read-only Alert History view.

**Features:**
- Collapsible **Project Profile** section containing `ProjectProfilePanel` for repo profile status plus full per-project profile overrides
- Master enable/disable checkbox (global)
- Collapsible **Error Types** section with per-kind checkboxes, descriptions, Select All / Clear All, and presets
- Collapsible **Success** section for success monitoring
- Alert History table: Time, Source, Kind, Cause, Context
- Clear history action
- "Open sound settings" button → opens `ErrorSoundConfigurable`
- No Diagnostics / Self-Test controls; diagnostics are Settings-only

**Behavior:**
- Project Profile shows repo profile status, warning count, reload/open-file controls, selected merge policy, and effective precedence
- Project Profile controls mutate `ProjectAlertSettings.state` directly
- Global enable checkbox mutates `AlertSettings.state.enabled`
- `refreshUiState()` uses `ResolvedSettingsResolver.resolve()` as the effective settings state for display and enablement
- Status label shows active enabled-count context and whether repo/project profile overrides are active
- Project Profile, Error Types, and Success collapse by default to keep the right-side tool window compact; Global Monitoring, Snooze, Alert History, and Open sound settings remain visible
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
*Last updated from code scan: 2026-05-27*
