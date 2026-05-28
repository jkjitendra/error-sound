# Recent Changes — Error Sound Alert

Engineering-significant changes to the codebase. Not a full changelog — focuses on architectural and compatibility changes.

---

## 1.1.22 — Per-Run-Configuration Overrides (Phase 12)

### Scope
Phase 12 adds shipped user-facing **Per-Run-Configuration Overrides** for Run/Debug executions. Users can customize alert behavior for specific run configuration names or configuration types without changing global, repo profile, or workspace project profile settings.

### User-facing behavior
- Added **Run Configuration Overrides** in **Settings -> Tools -> Error Sound Alert**
- First matching enabled row wins
- Match types: exact configuration name, configuration name contains, configuration name regex, and configuration type id/name contains
- Overrides can suppress all alerts, suppress success alerts, override minimum process duration, override alert duration, override play-once sound duration, and override visual notification behavior

### Runtime behavior
- `AlertOnErrorExecutionListener` applies run-configuration overrides after global/repo/project settings resolution and before duration threshold/dispatch
- Matching overrides create a run-specific effective settings copy and do not mutate stored global settings, repo profile data, or project profile state
- Suppressed run-config matches skip `AlertDispatcher`, sound playback, visual notifications, and Alert History
- Blank patterns and invalid regex are preserved in settings and skipped safely at runtime

### Implementation notes
- Added `RunConfigurationOverrideEngine.kt`
- Added `RunConfigurationOverrideMatchType.kt`
- Added `AlertSettings.State.runConfigurationOverrides`
- Added debug-only `RUN_CONFIGURATION_OVERRIDE_SUPPRESSED` explanation cause
- Diagnostics now reports Run/Debug run-configuration override count

### Safety Boundaries
- Run/Debug path only
- No terminal behavior changes
- No console-only behavior changes
- No repo profile schema changes
- No rule import/export schema changes
- No Alert History persistence changes
- No terminal reflection changes
- No network, telemetry, or file writes beyond normal application settings persistence
- Marketplace verifier compatibility fixes remain preserved: no `PluginManagerCore`, `PluginId`, or `FileSaverDescriptor` usage in source/resources

### Version
- Plugin version is `1.1.22`

---

## 1.1.21 — Profile Merge Policy UI (Phase 11)

### Scope
Phase 11 adds a shipped user-facing **Profile Merge Policy UI** for controlling how global settings, the repo-shared profile, and workspace project profile overrides are combined for the current project workspace.

### User-facing behavior
- Added **Profile merge policy** in **Error Monitor -> Project Profile**
- Shows effective precedence text next to the selected policy
- Diagnostics now reports selected policy, effective precedence, repo layer included/skipped status, workspace layer included/skipped status, and repo warnings when present

### Policy options
- **Standard: workspace project overrides repo profile** — default; `Global -> repo profile -> workspace project profile`
- **Ignore repo profile** — `Global -> workspace project profile`
- **Repo profile overrides workspace project** — `Global -> workspace project profile -> repo profile`
- **Global settings only** — global application settings only

### Implementation notes
- Added `ProfileMergePolicy.kt`
- Added `ProjectAlertSettings.State.profileMergePolicy`
- Updated `ResolvedSettingsResolver` to apply the selected policy without mutating global settings, repo profile data, or project workspace state
- Invalid/missing stored policy values normalize to `STANDARD_WORKSPACE_WINS`, preserving Phase 10 behavior for existing users

### Safety Boundaries
- Repo profile schema remains unchanged
- Repo profile file is not written or auto-created
- Rule import/export remains rules-only
- Terminal reflection behavior is unchanged
- No network, telemetry, or file writes beyond normal workspace state persistence
- Marketplace verifier compatibility fixes remain preserved: no `PluginManagerCore`, `PluginId`, or `FileSaverDescriptor` usage in source/resources

### Version
- Plugin version is `1.1.21`

---

## 1.1.20 — Marketplace Verifier Compatibility Fix

### Scope
This compatibility patch fixes Marketplace verifier API findings on the latest codebase so the next Marketplace upload can be published as 1.1.20 without uploading each unpublished intermediate version separately.

### API compatibility fixes
- Removed internal `PluginManagerCore.getPlugin(PluginId)` usage from settings-side rule export metadata
- Added bundled `error-sound-alert.properties` as the public-safe plugin version source
- Replaced deprecated `FileSaverDescriptor` constructor usage in rule export with a standard save chooser while preserving JSON filtering, user-selected path, explicit overwrite confirmation, and local-only file writes

### Marketplace metadata
- Plugin version is `1.1.20`
- Change notes summarize verifier fixes and include the unpublished user-facing work since 1.1.12

### Safety Boundaries
- No runtime alert behavior changes
- No terminal reflection logic changes
- No rule import/export schema changes
- No new product features
- No network, telemetry, or dependency additions

---

## 1.1.19 — Team-Shared Repo Profile File (Phase 10)

### Scope
Phase 10 adds a shipped user-facing **Team-Shared Repo Profile File**. If `.error-sound-alert.json` exists directly under `project.basePath`, it is loaded as a read-only repo-shared profile layer.

### New Files
- `RepoProfileState.kt` — schema version 1 model and pure application helper for safe profile defaults
- `RepoProfileLoadResult.kt` — load status, profile, path, and warnings model
- `RepoProfileService.kt` — project service that reads, validates, caches, reloads, and opens the repo profile file

### Effective Settings Resolution
Resolution precedence is fixed for Phase 10:
1. Global application settings
2. Repo-shared profile file
3. Workspace project profile overrides

`ResolvedSettingsResolver` returns an effective settings copy without mutating global settings, repo profile data, or project workspace state. Workspace project profile overrides always win over repo profile values.

### Repo Profile Schema
- File name: `.error-sound-alert.json`
- Location: project root / `project.basePath`
- Supported schema: `schemaVersion = 1`
- Supported categories: master enabled, per-kind monitoring, built-in/global sound behavior, per-kind sound enabled/id, success sound enabled/id, global/per-kind volume, alert duration, play-once mode, visual notifications, and minimum process duration

### UI / Diagnostics
- Error Monitor -> Project Profile shows repo profile status, profile name when available, and warning count
- Added **Reload repo profile** and **Open repo profile file** actions
- Diagnostics now reports repo profile status, schema, name, warnings count, and effective precedence

### Safety Boundaries
- No custom regex rules
- No suppression rules
- No terminal exit-code rules
- No rule presets
- No rule import/export schema changes
- No Alert History
- No custom audio file paths
- No telemetry or network calls
- No script execution
- No terminal reflection behavior changes
- No automatic `.error-sound-alert.json` creation or writes
- Missing file, invalid JSON, invalid schema, unknown fields, and invalid values fall back safely with warnings where applicable

### Marketplace metadata
- Plugin version is `1.1.19`
- Marketplace change notes and feature description include Team-Shared Repo Profile File as a shipped feature

---

## 1.1.18 — Full Per-Project Profiles (Phase 9)

### Scope
Phase 9 expands the earlier enabled-only project override into opt-in workspace-scoped **Full Per-Project Profiles**. Projects inherit global settings when profile overrides are disabled; when enabled, only selected override groups replace global values and unresolved fields continue inheriting globals.

### `ProjectAlertSettings` — expanded workspace state
- Preserves legacy `useOverride` / `enabledOverride` fields and migrates old enabled-only workspace overrides into the new profile master behavior
- Adds override groups for master enabled, monitoring kinds, built-in/global and per-kind sound behavior, success sound, global/per-kind volume, alert duration/play-once, visual notifications, and minimum process duration
- Stores project profile state in workspace storage (`WORKSPACE_FILE`), not shared repo files
- Adds helpers to copy current global settings, reset overrides, and report active override category labels for diagnostics

### `ResolvedSettingsResolver` — layered effective settings
- Starts from global `AlertSettings.State`
- Applies only selected project override groups
- Returns an effective settings copy without mutating global settings or project workspace state
- Leaves rules, presets, import/export schema, Alert History, and terminal integration global/application-level

### `ProjectProfilePanel` / `ErrorSoundToolWindowFactory` — compact project UI
- Adds a dedicated project profile panel inside Error Monitor
- Provides **Use project profile overrides**, **Copy current global settings**, and **Reset project overrides**
- Uses collapsible top-level Error Monitor sections for **Project Profile**, **Error Types**, and **Success** to keep the right-side tool window compact
- Keeps Global Monitoring, Snooze, Alert History, and Open sound settings visible
- Keeps Select all / Clear all and presets inside the Error Types section

### Diagnostics
- Diagnostics snapshot now reports active project profile override categories when an active project is available

### Marketplace metadata
- Plugin version is `1.1.18`
- Marketplace change notes and feature description include Full Per-Project Profiles as a shipped feature

### Safety Boundaries
- No per-project custom regex rules
- No per-project suppression rules
- No per-project exit-code rules
- No per-project rule presets or rule import/export
- No per-project Alert History
- No repo-shared profile file in Phase 9; repo profile support was added later in Phase 10
- No per-run-configuration overrides
- No AlertDispatcher, Alert History, rule import/export schema, terminal reflection, network, or telemetry behavior changes

---

## 1.1.17 — Diagnostics / Self-Test (Phase 8)

### Scope
Phase 8 adds a shipped user-facing **Diagnostics / Self-Test** section only in Settings / Preferences -> Tools -> Error Sound Alert. Diagnostics are not shown in the Error Monitor tool window.

### New File: `ErrorSoundDiagnosticsService.kt`
- Builds a local applied-status snapshot for monitoring, snooze, visual notifications, sound configuration, rule counts, Alert History count, rule preset availability, import/export schema support, and terminal integration status
- Provides safe self-test helpers for GENERIC error sound, SUCCESS sound, and diagnostic visual notification
- Sound self-tests use preview playback and respect Play Once Sound Duration where applicable

### `ErrorSoundConfigurable` — Diagnostics UI
- Adds a Settings-only **Diagnostics / Self-Test** section
- Provides **Refresh Diagnostics**, **Test error sound**, **Test success sound**, and **Test visual notification** actions
- Shows normal visual notification success as inline status, not a modal OK dialog
- Shows warning/error dialogs only for failure paths such as an unavailable notification group

### Diagnostic visual notification
- Sends a real IntelliJ Platform balloon notification
- Uses `NotificationGroupManager`
- Uses existing notification group id `Error Sound Alert`
- Uses `NotificationType.INFORMATION`
- Resolves an active project fallback because Settings has no direct project context
- Delivers notification on the EDT

### Marketplace metadata
- Plugin version is `1.1.17`
- Marketplace change notes and feature description include Diagnostics / Self-Test as a shipped feature

### Safety Boundaries
- No `AlertDispatcher` call
- No Alert History entries from self-tests
- No settings mutation
- No file writes
- No persistent diagnostic logs
- No telemetry
- No network calls
- No terminal reflection logic changes
- No direct imports from `org.jetbrains.plugins.terminal`

---

## 1.1.16 — Actionable Notification Actions v1 (Phase 7)

### Scope
Phase 7 improves visual alert notifications with lightweight actions. Notifications still appear only after the existing dispatcher gates accept an alert, and suppressed alerts still do not show notifications.

### `AlertDispatcher` — notification actions
- Preserves existing **Open Settings** and **Mute 1 hr** actions
- Adds **Open Error Monitor** to focus the Error Monitor tool window for the notification project
- Adds **Disable this kind** / **Disable success alerts** using `AlertMonitoring.setKindEnabled(settings, kind, false)`
- Adds **Show alert details** when `AlertMatchExplanation` is available

### Alert details dialog
- Shows capped, lightweight fields only:
  - source
  - kind
  - cause
  - exit code when present
  - command/config when present
  - rule id/pattern when present
  - match target when present
  - sound override when present
  - short summary
- Does not expose full console output
- Does not persist extra detail data beyond existing Alert History behavior

### Marketplace metadata
- Plugin version is `1.1.16`
- Marketplace change notes and feature description include Actionable Notification Actions as a shipped feature

### Safety Boundaries
- Dispatcher gate order unchanged
- Sound playback unchanged
- Alert History behavior unchanged
- Suppression behavior unchanged
- Terminal reflection logic unchanged
- No telemetry
- No network calls
- No file writes

### Known limitation
- **Disable this kind** updates the underlying monitoring setting immediately; if the Error Monitor tool window is already open, it may need refresh/reopen to visually reflect the changed checkbox state.

---

## 1.1.15 — Ignore / Suppression Rules (Phase 6)

### Scope
Phase 6 adds shipped user-facing **Suppression Rules** in Settings / Preferences -> Tools -> Error Sound Alert. Suppression rules silence known noisy false positives before alerts are dispatched.

Suppression wins over both custom regex classification and built-in classification. Suppressed matches do not call `AlertDispatcher`, play sound, show visual notifications, or enter Alert History.

### New File: `SuppressionRuleEngine.kt`
- Pure compiled regex helper for `AlertSettings.State.suppressionRules`
- Supports LINE_TEXT, FULL_OUTPUT, and EXIT_CODE_AND_TEXT targets
- Exposes `hasLineTextRules`, `hasFullOutputRules`, `hasExitCodeAndTextRules`, and target-specific match/explain methods
- Skips disabled rules, blank patterns, and invalid regex safely at runtime while preserving text in settings

### `AlertSettings.State`
- Adds `SuppressionRuleState(id, enabled, pattern, matchTarget, description)`
- Adds `suppressionRules: MutableList<SuppressionRuleState> = mutableListOf()`
- Adds cached `getCompiledSuppressionRuleEngine()`
- `loadState()` normalizes rule count, pattern length, match target, id, and description length

### `ErrorSoundConfigurable`
- Adds Suppression Rules table with Enabled, Pattern, Match Target, and Description columns
- Invalid regex is highlighted but preserved for editing
- Suppression rules are table-model-only until Apply
- Reset discards unapplied suppression-rule changes

### Detection paths
- Run/Debug: LINE_TEXT suppression can skip chunk-level classification; FULL_OUTPUT and EXIT_CODE_AND_TEXT suppression can skip final full-buffer alerts
- Console: LINE_TEXT suppression only
- Terminal: EXIT_CODE_AND_TEXT suppression only
- No terminal reflection attachment logic changed
- No direct imports from `org.jetbrains.plugins.terminal` were added

### Rule Import / Export
- Export now writes `schemaVersion = 2`
- Schema version 2 includes `customRules`, `suppressionRules`, and `exitCodeRules`
- Schema version 1 remains import-compatible for older exports without suppression rules
- Imported suppression rules update the settings UI table model only and are not persisted until Apply

### Marketplace metadata
- Plugin version is `1.1.15`
- Marketplace change notes and feature description include Suppression Rules as a shipped feature

### Safety Boundaries
- No sound changes
- No volume changes
- No play-once duration changes
- No project profile changes
- No alert history persistence changes
- No telemetry
- No network calls
- No remote rule downloads
- No script execution

---

## 1.1.14 — Play Once Sound Duration (PR #32 Integration)

### Scope
Adds shipped user-facing **Use actual sound file duration (play once)** behavior for file-based alert playback. The feature idea was ported from external PR #32 into the current architecture; the PR branch was not merged directly.

Default behavior remains unchanged: the option is disabled by default, and sounds continue to loop/restart until the configured alert duration expires.

### `AlertSettings.State`
- Adds `useActualSoundDuration: Boolean = false`
- The field affects playback duration behavior only
- It does not modify sound selection, volume settings, success settings, rules, alert history, visual notifications, snooze state, or project profiles

### `ErrorSoundConfigurable`
- Adds **Use actual sound file duration (play once)** checkbox in Settings / Preferences -> Tools -> Error Sound Alert
- Disables the alert duration slider/value label while selected
- Checkbox follows standard settings semantics: Apply persists; Reset discards unapplied checkbox changes
- Preview passes the selected mode to `ErrorSoundPlayer` where practical

### `ErrorSoundPlayer`
- When `useActualSoundDuration == true`, file-based built-in/custom playback opens the clip, applies volume, starts once, waits for clip length or stop/close, then stops/flushes/closes safely
- When `useActualSoundDuration == false`, the existing configured-duration looping behavior remains the default
- Preview uses the same play-once vs configured-duration branch

### Marketplace metadata
- Plugin version is `1.1.14`
- Marketplace change notes credit contributor PR #32
- Marketplace feature description includes the play-once option

### New sounds status
- The seven proposed PR #32 sounds were not shipped in 1.1.14
- Audio files were not present in the repository, and licensing/approval was not confirmed
- Future inclusion should verify file provenance and Marketplace-safe licensing before adding bundled sound entries

### Safety Boundaries
- No terminal reflection changes
- No rule preset, import/export, alert history, or custom rule behavior changes
- No network, telemetry, remote sound downloads, script execution, or file writes

---

## 1.1.13 — Rule Presets (Phase 5)

### Scope
Phase 5 adds a shipped user-facing **Rule Presets** section in Settings / Preferences -> Tools -> Error Sound Alert. Presets append bundled local rules to the existing rule tables only:
- Custom Regex Rules
- conservative Terminal Exit-Code Rules

Presets do not modify sound settings, volume settings, success settings, project overrides, alert history, snooze state, or full profiles/settings bundles.

### New File: `RulePresetBundle.kt`
- Data model for a bundled preset
- Fields: `id`, `displayName`, `description`, `customRules`, `exitCodeRules`
- `toString()` returns `displayName` for combo-box rendering

### New File: `RulePresetApplyResult.kt`
- Result object for a planned preset append operation
- Carries selected preset, custom rules to add, exit-code rules to add, duplicate custom rule ids, duplicate exit codes, warnings, and derived added/skipped counts
- Used by the settings UI to build the confirmation summary before table models are changed

### New File: `RulePresetService.kt`
- Pure helper containing bundled local preset definitions and duplicate-aware append planning
- Available bundles:
  - Java / Spring Boot
  - Gradle / Maven
  - Node.js / npm / pnpm
  - Python / pytest
  - Docker / Kubernetes
  - Frontend test runners (Jest / Vitest / Cypress / Playwright)
- `prepareApply()` skips duplicate preset custom rule ids and existing terminal exit codes
- Existing user-created rules are preserved; accepted preset rules are appended after them
- Uses existing `CustomRuleEngine` limits and allowed kinds

### `ErrorSoundConfigurable` — Rule Presets UI
- Adds Rule Presets dropdown, selected-preset description, and **Add Preset Rules** button near the rule controls
- On click, stops active rule table editing and shows a confirmation summary
- Adds accepted preset rules to the current table models only
- Preset additions follow normal settings semantics: Apply persists; Reset discards unapplied additions

### Marketplace metadata
- Plugin version is `1.1.13`
- Marketplace change notes and feature description include Rule Presets as a shipped feature

### Safety Boundaries
- Bundled presets only
- No network
- No telemetry
- No remote preset downloads
- No script execution
- No file writes
- No direct terminal plugin imports or terminal reflection changes

---

## 1.1.12 — Rule Import / Export (Phase 4)

### Scope
Phase 4 adds a shipped user-facing **Rule Import / Export** feature in Settings / Preferences -> Tools -> Error Sound Alert. It is rules-only and covers exactly:
- Custom Regex Rules
- Terminal Exit-Code Rules

It does not export or import global sound settings, per-kind volume, success settings, project overrides, alert history, snooze state, or a full plugin settings bundle.

### New File: `RuleImportExportBundle.kt`
- DTO for schema version 1 JSON bundles
- Top-level fields: `schemaVersion`, `exportedAt`, `pluginVersion`, `customRules`, `exitCodeRules`
- Nested rule DTOs mirror `AlertSettings.CustomRuleState` and `AlertSettings.ExitCodeRuleState`

### New File: `RuleImportExportResult.kt`
- Import result value carrying valid custom rules, valid exit-code rules, validation warnings, and skipped-entry count
- Used by the settings UI to build the confirmation summary before replacing table model contents

### New File: `RuleImportExportService.kt`
- Pure helper for pretty JSON export and strict JSON import validation
- Rejects malformed top-level structure, unsupported schema versions, and unsupported top-level fields
- Validates allowed custom rule targets and allowed error kinds
- Validates exit-code sound overrides against bundled sound ids
- Preserves rule ordering and ids when present
- Preserves invalid regex text but reports it so users can edit it before runtime use
- Applies existing custom rule limits: `MAX_RULES` and `MAX_PATTERN_LENGTH`

### `ErrorSoundConfigurable` — Import / Export UI
- Adds **Export Rules…** and **Import Rules…** controls near the rule sections
- Export serializes the current rule table-model state, including unsaved edits
- Import reads a local JSON file, validates it, shows a confirmation summary, then replaces only the two rule table models
- Imported changes follow normal settings semantics: Apply persists; Reset discards imported-but-not-applied changes
- Export asks before overwriting an existing file

### Safety Boundaries
- Local file import/export only
- No network
- No telemetry
- No permanent storage outside the selected export file
- No execution of imported content
- No direct terminal plugin imports

---

## 1.1.11 — Alert History Panel (Phase 3 Roadmap)

### Scope
Phase 3 adds a user-facing **Alert History** section to the Error Monitor. It records accepted alert events in memory and makes the Phase 2 explanation plumbing visible without changing runtime classification, dispatch gates, playback selection, or terminal architecture.

### New File: `AlertHistoryService.kt`
- Application-level service storing recent accepted alerts in memory only
- Retains a bounded newest-first history of the latest 100 entries
- `record(project, kind, soundOverride, explanation)` is called only after dispatcher gates accept an alert
- `snapshot()` returns a stable newest-first list for UI rendering
- `clear()` removes all entries
- Publishes an application message-bus topic after record/clear so open Error Monitor panels refresh

### `AlertDispatcher` — accepted-alert recording point
- Gate order is now: `SnoozeState` -> `AlertMonitoring` -> `AlertEventGate` -> `AlertHistoryService` -> `ErrorSoundPlayer` -> optional visual notification
- Snoozed, disabled, deduplicated, or otherwise suppressed attempts are not stored in this release
- History receives the existing `AlertMatchExplanation` object and does not participate in playback decisions

### `ErrorSoundToolWindowFactory` — Alert History UI
- Adds a read-only Alert History table to the Error Monitor tool window
- Columns: Time, Source, Kind, Cause, Context
- Context may include project/config/command, exit code, matched rule id/pattern, and sound override status
- Adds a Clear History action
- Refreshes from `AlertHistoryService.TOPIC` on the application message bus

### Marketplace metadata
- Plugin version is `1.1.11`
- Marketplace change notes and feature description include Alert History as a shipped feature

---

## 1.1.10 — Rule Match Explanation (Phase 2 Roadmap)

### Scope
Phase 2 added internal/runtime-facing explanation plumbing so the plugin can record why an alert classification was produced. It now feeds Alert History and, as of 1.1.16, the Show alert details notification action.

### New File: `AlertMatchExplanation.kt`
- Immutable explanation model with source, cause, final kind, optional custom-rule metadata, exit code, context, sound override, and suppression flag
- Causes include custom regex rule, built-in classifier, terminal exit-code rule, terminal exit-code suppression, success fallback, no match, and duration-threshold suppression
- Includes `summary()` for compact diagnostic logging

### New File: `ClassificationExplanationFactory.kt`
- Pure helper for constructing consistent `AlertMatchExplanation` objects near classification time
- Covers custom regex matches, built-in matches, terminal exit-code mappings/suppression, terminal fallback, success fallback, no-match, and Run/Debug duration-threshold suppression

### Classification plumbing
- `CustomRuleEngine` now exposes `CustomRuleMatch` and `explainLineText` / `explainFullOutput` / `explainExitCodeAndText`; existing `match*` methods still return `ErrorKind?`
- `ErrorClassifier.detectWithExplanation()` returns `BuiltInClassificationResult(kind, cause)` while `detect()` preserves the original `ErrorKind` API
- `TerminalClassifyResult` now carries optional `TerminalExitCodeRuleMatch` metadata
- Run/Debug, Console, and Terminal detection paths create explanations before calling `AlertDispatcher`
- `AlertDispatcher.tryAlert()` accepts an optional explanation and logs gate decisions without changing gate order or playback behavior

---

## 1.1.9 — Rule Testing Sandbox (Phase 1 Roadmap)

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
- Updated project overview limitations to reflect the then-current project-level `enabled` override
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

## 1.1.3 — Snooze / Mute

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

## 1.1.3 — Minimum Process Duration Threshold

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

## 1.1.2 — Success Sounds

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
*Last updated from code scan: 2026-05-27*
