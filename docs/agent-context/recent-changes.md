# Recent Changes — Error Sound Alert

Engineering-significant changes to the codebase. Not a full changelog — focuses on architectural and compatibility changes.

---

## TBD — Rule Testing Sandbox (Phase 1 Roadmap)

### Scope
Phase 1 adds a **settings-side Rule Testing Sandbox** for custom regex rules. It is an explanation and validation tool only; it does not participate in runtime detection, alert dispatch, monitoring gates, deduplication, playback, or notifications.

### New File: `RuleTestService.kt`
- Pure evaluator used by the settings UI
- Inputs: custom rules, sample output, selected Source, Match Target, and optional Exit Code
- Outputs: custom rule match details, resulting `ErrorKind`, built-in classifier fallback, regex validation errors, no-match message, and source/target applicability notes
- Mirrors custom rule limits and runtime target support without mutating settings or invoking `AlertDispatcher`

### `ErrorSoundConfigurable` — Rule Testing Sandbox UI
- New section under Settings / Preferences -> Tools -> Error Sound Alert
- Controls: Source, Match Target, Exit Code, sample output, and Test Rules
- Uses the current Custom Regex Rules table model so unsaved rule edits can be tested before Apply
- Displays whether a custom rule matched, which row/id/pattern matched, the resulting kind, whether built-in classification would match if no custom rule did, and any regex validation errors

### How to use
1. Open Settings / Preferences -> Tools -> Error Sound Alert
2. Find Rule Testing Sandbox
3. Choose Source and Match Target
4. Optionally set Exit Code
5. Paste sample output
6. Click Test Rules

---

## 2026-04-29 — Phase 0 Documentation Alignment

### Scope
Phase 0 performed cleanup and baseline stabilization only. No runtime source, Gradle build logic, plugin XML, or resource behavior changed.

### Documentation updates
- Aligned plugin version references with `build.gradle.kts` version `1.1.8`
- Updated project overview limitations to reflect the current project-level `enabled` override
- Refreshed README feature coverage for currently shipped features through 1.1.8
- Added `docs/agent-context/feature-registry.md` as the available-feature inventory

---

## 1.1.8 — Per-Kind Volume (Phase 8)

### Scope
Phase 8 adds **optional per-kind volume overrides** layered on top of the existing global `volumePercent`. When a per-kind override is `null` the global volume applies unchanged, preserving all existing behavior exactly. No other settings are affected.

### `AlertSettings.State` — 7 new fields
| Field | Type | Default |
|---|---|---|
| `configurationVolumePercent` | `Int?` | `null` |
| `compilationVolumePercent` | `Int?` | `null` |
| `testFailureVolumePercent` | `Int?` | `null` |
| `networkVolumePercent` | `Int?` | `null` |
| `exceptionVolumePercent` | `Int?` | `null` |
| `genericVolumePercent` | `Int?` | `null` |
| `successVolumePercent` | `Int?` | `null` |

All are clamped to `0..100` when non-null in `loadState()`. `null` is preserved as-is.

### `ErrorSoundPlayer` — `resolveEffectiveVolumePercent()`
New `fun resolveEffectiveVolumePercent(settings, errorKind): Int`:
- Returns the per-kind override when non-null
- Falls back to `settings.volumePercent` (global) otherwise
- `ErrorKind.NONE` has no override field — always uses global

`play()` calls this once and passes the resulting `Int` explicitly to every private playback helper (`playCustom`, `playBuiltIn`, `playBuiltInById`, `playGeneratedTone`, `playClipLooping`). No helper reads `settings.volumePercent` directly anymore.

Applies to all paths: built-in sounds, custom files, generated tone fallback, and Phase 6 exit-code sound overrides.

### `ErrorSoundConfigurable` — per-kind volume UI
Each of the 7 error/success kind rows now has an additional stacked volume row containing:
- `JBCheckBox("Custom volume")` — activates the override
- `JBSlider(0, 100)` — shows the stored override (or global fallback when unchecked)
- `JBLabel("xx%")` — live value display

UI rules:
- Slider and label are **disabled** (greyed out) when checkbox is unchecked — no layout jumping
- On `reset()`: slider = stored override ?? global volume; label synced
- On `apply()`: persists `null` when checkbox unchecked
- `isModified()` compares both the activation flag and the value when active
- Per-kind preview uses `kindVolumeMap` — a lazy `Map<ComboBox, () -> Int>` that returns kind-slider value when checked, global slider otherwise
- Per-kind volume is **independent** of global built-in mode and sound-source choice

---

## 1.1.7 — Project-Level Profiles (Phase 7)

### Scope
Phase 7 adds **per-project overrides for the master `enabled` flag only**. Everything else (sounds, per-kind flags, custom regex rules, exit-code rules) continues to come from global/application settings. No field-by-field project overrides are added in this phase.

### New File: `ProjectAlertSettings.kt`
- `@Service(Service.Level.PROJECT)`, `@State` with `Storage(StoragePathMacros.WORKSPACE_FILE)`
- State: `useOverride: Boolean = false`, `enabledOverride: Boolean = true`
- `effectiveEnabledOverride(): Boolean?` — returns `null` (inherit global), `true`, or `false`
- `getInstance(project)` companion helper
- Nullability modelled via two fields (`useOverride` + `enabledOverride`) — XML serializer cannot represent `Boolean?` directly

### New File: `ResolvedSettingsResolver.kt`
- `@Service(Service.Level.PROJECT)` — thin project service
- `resolve(): AlertSettings.State` — returns the effective settings for the current project:
  - If `effectiveEnabledOverride() == null` → returns global `AlertSettings.state` unchanged
  - If override is set → returns `global.copy(enabled = override)`
- Only `enabled` may differ; all other fields come from global settings unchanged
- `getInstance(project)` companion helper

### plugin.xml
- Registered `ProjectAlertSettings` and `ResolvedSettingsResolver` as project services

### Detection Paths — resolver wired in
All three alert-entry paths now call `ResolvedSettingsResolver.getInstance(project).resolve()` at dispatch time instead of `AlertSettings.getInstance().state`:

- **`AlertOnErrorExecutionListener`** — `processTerminated()` uses resolved state for `AlertDispatcher.tryAlert()`; classification logic unchanged
- **`ErrorConsoleFilterProvider`** — `ErrorDetectionFilter.applyFilter()` uses resolved state; custom rule engine remains global
- **`AlertOnTerminalCommandListener`** — `handleCommandFinished()` resolves state; custom rule engine and exit-code rules remain global (only `enabled` is project-aware)

### `ErrorSoundToolWindowFactory` — Project Profile UI section
New "Project Profile" section above the existing "Global Monitoring" section:
- Hint label: "Override the global "Enable monitoring" for this project only."
- `useProjectOverrideCheckBox` — "Use project override for monitoring enabled"; activates per-project override
- `projectEnabledCheckBox` — "Enable monitoring for this project"; set the override value; **disabled (greyed out)** when override is off
- `refreshUiState()` now calls `ResolvedSettingsResolver.resolve()` for the effective enabled value; status label shows `(global)` or `(project override)` to indicate the source

### Effective behavior rules
```
projectOverride == null  →  effective enabled = AlertSettings.state.enabled
projectOverride == true  →  effective enabled = true  (regardless of global)
projectOverride == false →  effective enabled = false (regardless of global)
```

### Explicitly NOT changed in Phase 7
- Per-kind monitor flags remain global
- Sounds (built-in, per-kind, custom) remain global
- Custom regex rules remain global
- Exit-code rules remain global
- No new settings panel / configurable screen

---

## 1.1.6 — Exit-Code-Specific Terminal Sounds

### AlertSettings — New Type
- `data class ExitCodeRuleState(exitCode, enabled, kind, soundId?, suppress)` (nested in `AlertSettings`)
- `State.exitCodeRules: MutableList<ExitCodeRuleState>` — four default rules:
  - exitCode=130 suppress=true (SIGINT / Ctrl+C — silenced by default)
  - exitCode=127 suppress=false (command not found)
  - exitCode=137 suppress=false (SIGKILL / OOM)
  - exitCode=143 suppress=false (SIGTERM)

### AlertSettings — loadState() Normalization (exit code rules)
- `kind` normalized to allowed kinds (NONE/SUCCESS rejected → GENERIC)
- `soundId` blank or `CUSTOM_FILE_ID` → `null`; non-null soundId passed through `normalizeSoundId()`

### ErrorKind — New Types
- `data class TerminalClassifyResult(kind: ErrorKind, soundOverride: String?, suppressed: Boolean)` — richer result from terminal classification
- `ErrorClassifier.classifyTerminal(command, exitCode, exitCodeRules)` — iterates exit code rules (first enabled match wins), falls back to `detectTerminal()` if no rule matches

### AlertOnTerminalCommandListener — Three-step terminal precedence
`handleCommandFinished()` now applies three precedence tiers in order:
1. Phase 5 custom EXIT_CODE_AND_TEXT regex rules (highest priority)
2. Phase 6 exit code rules via `ErrorClassifier.classifyTerminal()` — check `suppressed` flag first; if suppressed, return silently
3. Built-in `detectTerminal()` fallback (inside `classifyTerminal()` when no rule matches)

### AlertDispatcher — soundOverride parameter
- `tryAlert()` gains `soundOverride: String? = null` (backward-compatible)
- Passes `soundOverride` to `ErrorSoundPlayer.play()` — only the terminal listener passes a non-null value

### ErrorSoundPlayer — soundOverride support
- `play(settings, errorKind, soundOverride: String? = null)` — if non-null, skips normal sound resolution
- New private `playBuiltInById(soundId, settings)` — resolves and plays by ID directly, falls back to generated tone on failure

### ErrorSoundConfigurable — Exit Code Rules section
- New "Exit-Code Rules" table section (below Custom Regex Rules, separated by divider)
- `JBTable` via `ToolbarDecorator` with Add/Remove; 5 columns: Exit Code / Enabled / Kind / Sound / Suppress
- `ExitCodeRuleTableModel` (inner class) — `AbstractTableModel` with internal `Row` data class; `SoundChoice` for nullable sound column
- `SoundChoice` (inner data class) — wraps nullable sound ID; `null` = "(default)"; uses `toString()` = label for JComboBox display
- Sound column: `DefaultCellEditor(JComboBox(soundChoices))` — "(default)" + all `BuiltInSounds.all` entries
- Kind column: `DefaultCellEditor(JComboBox(ALLOWED_CUSTOM_RULE_KINDS))`
- Help text: exit-code rules apply to terminal only; Sound "(default)" semantics; Suppress explained

---

## 1.1.5 — Custom Regex Rules

### New File
- `CustomRuleEngine.kt` — compiles and caches user-defined rules; provides `matchLineText()`, `matchFullOutput()`, `matchExitCodeAndText()` methods.

### AlertOnErrorExecutionListener — chunk precedence fix
- Introduced separate `customDetectedKind` (`AtomicReference`) alongside existing `builtInDetectedKind`
- In `onTextAvailable()`: if a custom LINE_TEXT rule matches, the kind goes into `customDetectedKind` only; built-in `ErrorClassifier.detect()` is skipped for that chunk
- In `processTerminated()`: custom wins at every level — FULL_OUTPUT → EXIT_CODE_AND_TEXT → custom LINE_TEXT accumulation → built-in accumulation → built-in full-buffer
- This prevents a later high-priority built-in chunk match (e.g. CONFIGURATION) from overwriting an earlier custom match

### AlertSettings — New Types
- `enum class MatchTarget { LINE_TEXT, FULL_OUTPUT, EXIT_CODE_AND_TEXT }` (nested in `AlertSettings`)
- `data class CustomRuleState(id, enabled, pattern, matchTarget, kind)` (nested in `AlertSettings`)
- `State.customRules: MutableList<CustomRuleState> = mutableListOf()` — persisted list of rules

### AlertSettings — Engine Cache
- `compiledRuleEngine: CustomRuleEngine? (@Volatile)` — invalidated by `loadState()` on every Apply
- `getCompiledRuleEngine()` — returns cached engine, compiles lazily on first call after invalidation

### AlertSettings — loadState() Normalization
- Clamps rule count to max 100
- Trims/limits pattern to 500 chars
- Normalizes `matchTarget` to valid `MatchTarget` enum (default LINE_TEXT)
- Normalizes `kind` to a valid allowed kind (default GENERIC); NONE and SUCCESS excluded
- Preserves/generates IDs; keeps invalid regex text for UI editing

### CustomRuleEngine
- Compiles rules once on construction; skips disabled rules, blank patterns, and invalid regex
- `ALLOWED_CUSTOM_RULE_KINDS` = CONFIGURATION, COMPILATION, TEST_FAILURE, NETWORK, EXCEPTION, GENERIC
- `MAX_RULES = 100`, `MAX_PATTERN_LENGTH = 500`
- `hasLineTextRules / hasFullOutputRules / hasExitCodeAndTextRules` — fast guards to skip paths with no applicable rules
- EXIT_CODE_AND_TEXT matching uses combined string `"exitcode:N\n<text>"` so patterns can reference exit code and/or text content

### AlertOnErrorExecutionListener
- `onTextAvailable()` — LINE_TEXT custom rules checked first; falls back to `ErrorClassifier.detect()`
- `processTerminated()` — FULL_OUTPUT then EXIT_CODE_AND_TEXT custom rules applied to full buffer; if any match, custom kind overrides all built-in detection; otherwise existing logic (chunk accumulation + full-buffer detect) unchanged

### ErrorConsoleFilterProvider
- Custom LINE_TEXT rules checked before built-in `errorPattern`; FULL_OUTPUT and EXIT_CODE_AND_TEXT skipped (unsupported target for this path)

### AlertOnTerminalCommandListener
- EXIT_CODE_AND_TEXT custom rules checked before `ErrorClassifier.detectTerminal()`; LINE_TEXT and FULL_OUTPUT skipped (unsupported)

### ErrorSoundConfigurable
- New "Custom Regex Rules" section (below Visual Notifications, separated by divider)
- `JBTable` via `ToolbarDecorator` with columns: Enabled / Pattern / Match Target / Kind
- Add/Remove toolbar actions
- Pattern column has inline validation renderer (red tint + tooltip for invalid regex)
- Match Target column: combo box with LINE_TEXT / FULL_OUTPUT / EXIT_CODE_AND_TEXT
- Kind column: combo box restricted to allowed kinds
- Help text: custom rules run before built-in classification; target scope limitations documented inline

---

## 1.1.4 — Visual Alert Companion

### AlertDispatcher
- `tryAlert()` gains a new optional parameter: `project: Project? = null`
- After `ErrorSoundPlayer.play()`, conditionally calls `showNotification(settings, kind, project)`
- Gate order updated: `SnoozeState` → `AlertMonitoring` → `AlertEventGate` → `ErrorSoundPlayer` → visual notification
- Notification uses `NotificationGroupManager` with group id `"Error Sound Alert"` (registered in `plugin.xml`)
- Balloon type: `WARNING` for errors, `INFORMATION` for success
- Two inline actions: **Open Settings** (opens `ErrorSoundConfigurable`) and **Mute 1 hr** (calls `SnoozeState.snooze(60)`)

### plugin.xml
- Registered new `<notificationGroup>` extension: id `"Error Sound Alert"`, `displayType="BALLOON"`, `isLogByDefault="false"`

### AlertSettings.State — New Fields
- `showVisualNotification: Boolean = false` — master toggle (off by default)
- `visualNotificationOnError: Boolean = true` — show balloon on error alerts
- `visualNotificationOnSuccess: Boolean = true` — show balloon on success alerts

### ErrorSoundConfigurable
- New "Visual Notifications" section (below Min Process Duration, separated by divider)
- Master checkbox: **Show balloon notification alongside sound**
- Sub-row ("Notify on:"): **On errors** / **On successes** checkboxes
- Sub-checkboxes disabled when master toggle is off

### Call Sites Updated
- `AlertOnErrorExecutionListener` → passes `env.project`
- `ErrorConsoleFilterProvider` → passes `project`
- `AlertOnTerminalCommandListener` → passes `project`

### Spam Prevention
- No extra deduplication needed — existing `AlertEventGate` cooldown naturally prevents balloon spam

### SnoozeState — Bus Integration
- Added `TOPIC: Topic<SnoozeListener>` on the application message bus
- `snooze(minutes)` and `resume()` now call `publish()` after updating state
- `ErrorSoundToolWindowPanel` subscribes to the topic in `init` — fires on any source (tool window links or balloon action)
- Subscriber starts/stops `snoozeRefreshTimer` and calls `refreshUiState()` — same logic, single path
- `doSnooze()` and `doResume()` in the tool window simplified to just call `SnoozeState`; bus handles the rest

---

## 1.4.0 — Snooze / Mute

### New File
- `SnoozeState.kt` — transient singleton using `AtomicLong`. Provides `isSnoozed()`, `snooze(minutes)`, `resume()`, `statusLabel()`.

### AlertDispatcher
- Snooze check added as the **first gate** in `tryAlert()`, before `AlertMonitoring`
- Gate order: SnoozeState → AlertMonitoring → AlertEventGate → ErrorSoundPlayer

### ErrorSoundToolWindowFactory
- New "Snooze" section with three `ActionLink`s: **Mute 15 min** / **Mute 1 hour** / **Resume**
- `snoozeLabel` shows "All alerts muted" while active
- `statusLabel` shows snooze expiry time (e.g. "Snoozed until 14:30") when snoozed
- `Resume` link enabled only while snoozed

### Scope
- No settings persistence — snooze is always transient (resets on IDE restart)
- No settings panel changes needed

---

## 1.3.0 — Execution Time Threshold

### New Setting
- `minProcessDurationSeconds: Int = 0` — suppress alerts if the process finishes faster than this value
- Clamped to 0–300 in `loadState()`. Default 0 preserves existing behavior.

### Logic
- `AlertOnErrorExecutionListener.processStarted()` now records `startedAtMillis = System.currentTimeMillis()`
- In `processTerminated()`: if `elapsed < threshold * 1000`, alert is silently suppressed with a debug log entry
- Applies to both error and success alerts from Run/Debug
- **Explicitly excluded:** console filter (stateless/per-line), terminal listener (no clean start timestamp)

### UI
- Settings panel: "Min process duration (sec)" spinner (0–300) with help text documenting scope

---

## 1.2.0 — Success Sounds

### ErrorKind.SUCCESS
- **New enum value** `SUCCESS` added to `ErrorKind`
- `AlertOnErrorExecutionListener.processTerminated()` converts `NONE` + exit code 0 → `SUCCESS`
- SUCCESS kept out of chunk-priority model; conversion happens post-classification only

### New Settings Fields
- `monitorSuccess: Boolean = false` — success monitoring OFF by default
- `successSoundEnabled: Boolean = false` — success sound OFF by default
- `successSoundId: String = "yeah_boy"` — default success sound

### UI Updates
- **Settings panel:** Success sound row (enable checkbox + sound combo), separated from error kinds
- **Error Monitor tool window:** SUCCESS checkbox under "Success" separator section
- Status label shows success state (e.g., "Active · 6 error + success enabled")

### Scope Boundaries
- Terminal success sounds not included (terminal detection unchanged)
- Console filter unaffected (only triggers on error patterns)

---

## 1.1.1 — Deduplication & Dispatch Overhaul

### AlertDispatcher Introduction
- **New class** `AlertDispatcher.kt` — single choke-point between all three detection paths and `ErrorSoundPlayer`
- All detection sources now route through `AlertDispatcher.tryAlert()` instead of calling `ErrorSoundPlayer.play()` directly
- Ensures `AlertMonitoring` → `AlertEventGate` → `ErrorSoundPlayer` is wired in exactly one place

### AlertEventGate Introduction
- **New class** `AlertEventGate.kt` — central deduplication gate
- Per-key cooldown (4s) and global cooldown (2s) prevent duplicate sounds
- Map eviction at 512 entries (60s TTL) prevents memory leaks from terminal command keys
- Thread-safe via `@Synchronized`

### Reduced Audio-Player Debounce
- `ErrorSoundPlayer.DEBOUNCE_MS` reduced from a larger value to 250ms
- Now serves only as a last-resort guard — `AlertEventGate` owns real throttling

### Console Detection via AlertDispatcher
- `ErrorConsoleFilterProvider` now uses `AlertDispatcher.tryAlert()` with key `"console:{projectHash}:{errorKind}"`
- Previously called `ErrorSoundPlayer` directly

### Execution Listener via AlertDispatcher
- `AlertOnErrorExecutionListener` now routes through `AlertDispatcher.tryAlert()`
- Uses dedup key `"exec:{handlerIdentityHash}:{errorKind}"`

### Terminal Logging Cleanup
- Debug-level logging for expected situations (class not found, view not ready)
- Warn-level logging reserved for actual failures
- Reduces noise in IDE logs for users on newer IDE versions where some terminal classes don't exist

---

## 1.1.0 — Platform Migration

### IntelliJ Platform Gradle Plugin 2.x Migration
- Migrated from IntelliJ Gradle Plugin 1.x to IntelliJ Platform Gradle Plugin 2.12.0
- New DSL: `intellijPlatform { }` block replaces `intellij { }` block
- `pluginConfiguration`, `pluginVerification`, `signing`, `publishing` now nested under `intellijPlatform`

### Baseline Raise to 2024.3 / Build 243
- `sinceBuild` raised from 241 to 243
- Required because Kotlin 2.x stdlib is only bundled in 2024.3+
- Breaking change documented in changelog

### Java 21 / Kotlin 2.3.10 Setup
- Java toolchain raised to 21
- Kotlin compiler version raised to 2.3.10
- apiVersion/languageVersion set to 2.0 for compatibility
- `-Xjvm-default=all` compiler flag added

### Gradle 9.0
- Gradle wrapper updated to 9.0
- Configuration cache and build caching enabled

### Open untilBuild Policy
- `untilBuild = provider { null }` — no upper bound
- Forward-compatible with future IDE releases

---

## 1.0.4 — Error Monitor Tool Window
- Added **Error Monitor** sidebar panel (`ErrorSoundToolWindowFactory`)
- Per-kind toggle checkboxes with presets (All, Build Only, Runtime Only)
- `AlertMonitoring` object introduced for centralized monitoring rule checks

## 1.0.3 — Console Detection & Terminal
- Added `ErrorConsoleFilterProvider` for real-time console output scanning
- Improved terminal compatibility with 2025.x reworked terminal engine

---
*Last updated from code scan: 2026-04-11*
