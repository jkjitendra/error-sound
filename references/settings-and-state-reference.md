# Settings & State Reference — Error Sound Alert

Quick lookup for `AlertSettings.State` fields and their usage.

## State Class: `AlertSettings.State`

**Persistence:** `@State(name = "ErrorSoundAlertSettings", storages = [Storage("errorSoundAlert.xml")])`
**Service level:** `@Service(Service.Level.APP)` — application-wide, not per-project

## Fields

### Master Controls

| Field | Type | Default | Used By |
|---|---|---|---|
| `enabled` | Boolean | `true` | `AlertMonitoring`, Tool Window |

### Monitoring Flags (per-kind toggles)

| Field | Type | Default | ErrorKind |
|---|---|---|---|
| `monitorConfiguration` | Boolean | `true` | CONFIGURATION |
| `monitorCompilation` | Boolean | `true` | COMPILATION |
| `monitorTestFailure` | Boolean | `true` | TEST_FAILURE |
| `monitorNetwork` | Boolean | `true` | NETWORK |
| `monitorException` | Boolean | `true` | EXCEPTION |
| `monitorGeneric` | Boolean | `true` | GENERIC |
| `monitorSuccess` | Boolean | `false` | SUCCESS |

### Sound Configuration

| Field | Type | Default | Description |
|---|---|---|---|
| `soundSource` | String | `"BUNDLED"` | `BUNDLED` or `CUSTOM` |
| `builtInSoundId` | String | `"boom"` | Global built-in sound |
| `useGlobalBuiltInSound` | Boolean | `true` | One sound for all kinds |
| `customSoundPath` | String | `""` | Path to custom audio file |

### Per-Kind Sound Mapping

| Kind | Enabled Field | Sound ID Field | Default Sound |
|---|---|---|---|
| Configuration | `configurationSoundEnabled` | `configurationSoundId` | `"huh"` |
| Compilation | `compilationSoundEnabled` | `compilationSoundId` | `"punch"` |
| Test Failure | `testFailureSoundEnabled` | `testFailureSoundId` | `"dog_laughing_meme"` |
| Network | `networkSoundEnabled` | `networkSoundId` | `"yooo"` |
| Exception | `exceptionSoundEnabled` | `exceptionSoundId` | `"boom"` |
| Generic | `genericSoundEnabled` | `genericSoundId` | `"boom"` |
| Success | `successSoundEnabled` | `successSoundId` | `"yeah_boy"` |

### Playback

| Field | Type | Default | Range |
|---|---|---|---|
| `volumePercent` | Int | `80` | 0–100 |
| `alertDurationSeconds` | Int | `3` | 1–10 |
| `useActualSoundDuration` | Boolean | `false` | Disabled by default. When `true`, file-based playback uses the selected clip's actual duration and ignores `alertDurationSeconds` |
| `minProcessDurationSeconds` | Int | `0` | 0–300. Alerts suppressed if process completes faster. Run/Debug only. |

### Rule Collections

| Field | Type | Default | Description |
|---|---|---|---|
| `customRules` | `MutableList<CustomRuleState>` | Empty list | User-defined regex rules evaluated before built-in classification |
| `suppressionRules` | `MutableList<SuppressionRuleState>` | Empty list | User-defined regex rules that silence matching contexts before alert dispatch |
| `exitCodeRules` | `MutableList<ExitCodeRuleState>` | 130 suppress, 127/137/143 GENERIC | Terminal exit-code mappings, sound overrides, or suppression |

`CustomRuleState` fields:
- `id: String`
- `enabled: Boolean`
- `pattern: String`
- `matchTarget: String` — `LINE_TEXT`, `FULL_OUTPUT`, or `EXIT_CODE_AND_TEXT`
- `kind: String` — allowed user-facing error kind

`SuppressionRuleState` fields:
- `id: String`
- `enabled: Boolean`
- `pattern: String`
- `matchTarget: String` — `LINE_TEXT`, `FULL_OUTPUT`, or `EXIT_CODE_AND_TEXT`
- `description: String`

`ExitCodeRuleState` fields:
- `exitCode: Int`
- `enabled: Boolean`
- `kind: String` — allowed user-facing error kind
- `soundId: String?` — bundled sound override, or `null` for default resolution
- `suppress: Boolean`

## Validation (in `loadState`)

- `volumePercent` clamped to 0–100
- `alertDurationSeconds` clamped to 1–10
- `useActualSoundDuration` preserved as a Boolean setting; default `false` keeps existing configured-duration looping behavior
- All sound IDs normalized via `BuiltInSounds.findByIdOrDefault()`
- Custom file ID (`__custom_file__`) preserved as-is
- Custom rules are normalized to existing limits: first `CustomRuleEngine.MAX_RULES`, patterns trimmed/truncated to `MAX_PATTERN_LENGTH`, blank ids regenerated, unsupported targets default to `LINE_TEXT`, unsupported kinds default to `GENERIC`
- Suppression rules are normalized to existing limits: first `SuppressionRuleEngine.MAX_RULES`, patterns trimmed/truncated to `MAX_PATTERN_LENGTH`, blank ids regenerated, unsupported targets default to `LINE_TEXT`, and descriptions trimmed/truncated to `MAX_DESCRIPTION_LENGTH`
- Exit-code rule kinds are normalized to allowed error kinds, and blank or custom-file sound ids become `null`

## SoundSource Enum

```kotlin
enum class SoundSource { BUNDLED, CUSTOM }
```

## Play Once Sound Duration

`useActualSoundDuration` controls playback duration behavior only. It does not alter sound selection, global volume, per-kind volume, success settings, custom rules, exit-code rules, alert history, visual notifications, snooze state, or project profiles.

Behavior:
- Default `false`: existing behavior remains active. Short file-based clips loop/restart until `alertDurationSeconds` expires.
- `true`: selected file-based sounds start once and use their actual clip length; the configured alert duration is ignored for playback.
- In settings, **Use actual sound file duration (play once)** disables the alert duration slider/value label while selected.
- Preview follows the same play-once vs configured-duration behavior where practical.
- The checkbox follows standard settings semantics: changes are not persisted until Apply, and Reset discards unapplied changes.

The 1.1.14 implementation ports the feature idea from external PR #32 into the current architecture without merging the PR branch directly. The seven proposed new sounds from that PR were not shipped in 1.1.14 because audio files and licensing were not confirmed.

## Actionable Notification Actions

Phase 7 adds no new persisted settings for notification actions. It reuses existing state:
- `showVisualNotification`
- `visualNotificationOnError`
- `visualNotificationOnSuccess`
- per-kind monitoring flags such as `monitorCompilation`
- `monitorSuccess`

The **Disable this kind** / **Disable success alerts** notification action mutates the existing monitoring flag for the alert kind immediately via `AlertMonitoring.setKindEnabled(...)`. No separate notification-action preference, alert-detail storage, file output, network behavior, or telemetry state is added.

Alert detail dialogs are generated from the in-memory `AlertMatchExplanation` passed to `AlertDispatcher`. Detail fields are capped and may include source, kind, cause, exit code, command/config, rule id/pattern, match target, sound override, and short summary. Full console output is not shown. Existing Alert History behavior is unchanged.

## Diagnostics / Self-Test

Phase 8 diagnostics add no new persisted settings. Diagnostics read existing applied settings and runtime status from services such as `AlertSettings`, `SnoozeState`, `AlertHistoryService`, and bundled rule helpers.

Diagnostics / Self-Test is available only in Settings / Preferences -> Tools -> Error Sound Alert. It is not part of the Error Monitor tool window.

The diagnostics summary may show:
- monitoring enabled/disabled
- repo profile present/absent/invalid state
- repo profile schema, profile name, warning count, and effective precedence
- snooze active/inactive
- notification settings
- sound source and selected sound
- global volume
- alert duration
- `useActualSoundDuration` / play-once state
- custom regex rule count
- suppression rule count
- terminal exit-code rule count
- Alert History count
- rule preset availability
- rule import/export schema support
- terminal integration status

Self-test actions:
- **Test error sound** uses preview playback for a GENERIC error sound
- **Test success sound** uses preview playback for the SUCCESS sound when enabled
- **Test visual notification** sends a real IntelliJ Platform balloon notification through the existing `Error Sound Alert` notification group

Test visual notification uses `NotificationGroupManager`, `NotificationType.INFORMATION`, active project fallback, and EDT delivery. The normal success path does not show a modal OK dialog; failure paths may show a warning/error dialog.

Diagnostics and self-tests do not mutate settings, write Alert History entries, write files, create persistent diagnostic logs, call `AlertDispatcher`, use network/telemetry, or change terminal reflection behavior. Sound self-tests respect Play Once Sound Duration where applicable.

## Project Profile State: `ProjectAlertSettings.State`

**Persistence:** `@State(name = "ErrorSoundProjectAlertSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])`
**Service level:** `@Service(Service.Level.PROJECT)` — workspace-scoped project state

Project profiles are opt-in workspace overrides layered over global and repo profile settings. When `useProfileOverrides` is `false`, the project inherits the repo profile when present, otherwise global settings. When it is `true`, only selected override groups replace lower-priority values; unselected groups continue inheriting repo/global settings.

### Project Profile Fields

| Group | Enable Field | Value Fields |
|---|---|---|
| Profile master | `useProfileOverrides` | Enables/disables all project profile overrides |
| Master monitoring | `useOverride` | `enabledOverride` |
| Monitoring kinds | `useMonitoringOverrides` | `monitorConfigurationOverride`, `monitorCompilationOverride`, `monitorTestFailureOverride`, `monitorNetworkOverride`, `monitorExceptionOverride`, `monitorGenericOverride`, `monitorSuccessOverride` |
| Built-in sound behavior | `useSoundOverrides` | `useGlobalBuiltInSoundOverride`, `builtInSoundIdOverride`, per-kind sound enabled/id overrides, `successSoundEnabledOverride`, `successSoundIdOverride` |
| Volume | `useVolumeOverrides` | `volumePercentOverride`, nullable per-kind volume percent overrides |
| Duration / play once | `useDurationOverrides` | `alertDurationSecondsOverride`, `useActualSoundDurationOverride` |
| Visual notifications | `useVisualNotificationOverrides` | `showVisualNotificationOverride`, `visualNotificationOnErrorOverride`, `visualNotificationOnSuccessOverride` |
| Minimum process duration | `useMinProcessDurationOverride` | `minProcessDurationSecondsOverride` |

### Project Profile Resolution

`ResolvedSettingsResolver.resolve()` starts with a copy of global `AlertSettings.State`, applies a valid enabled repo profile if present, then applies selected project override groups, and returns an effective copy. It does not mutate global settings, repo profile data, or project workspace state.

Supported Phase 9 overrides:
- master enabled
- per-kind monitoring toggles
- built-in/global sound behavior
- per-kind sound enabled/id
- success sound enabled/id
- global volume
- per-kind volume overrides
- alert duration
- `useActualSoundDuration` / play once
- visual notification settings
- minimum process duration threshold

Project profile state does **not** include custom regex rules, suppression rules, terminal exit-code rules, rule presets, rule import/export data, Alert History, terminal integration state, merge-policy UI, or per-run-configuration overrides.

### Backward Compatibility

Legacy workspace files with only `useOverride` / `enabledOverride` still load. Normalization treats an existing enabled-only override as an active project profile master override so old per-project monitoring behavior is preserved.

### Project Profile Actions

- **Copy current global settings** seeds the project profile from current global settings and enables supported override groups.
- **Reset project overrides** clears project profile state back to inheritance defaults.
- Diagnostics can show active project profile override categories when an active project is available.

## Team-Shared Repo Profile File

**File name:** `.error-sound-alert.json`
**Location:** project root / `project.basePath`
**Service:** `RepoProfileService` (`@Service(Service.Level.PROJECT)`)

Phase 10 repo profiles add a read-only local profile layer between global settings and workspace project profile overrides:

```text
Global application settings -> repo-shared profile -> workspace project profile overrides
```

The plugin does not auto-create, edit, or write `.error-sound-alert.json`. It reads only the file directly under `project.basePath`; it does not intentionally scan parent directories, use network paths, execute content, send telemetry, or change terminal reflection behavior.

### Schema Version 1

Top-level structure:

```json
{
  "schemaVersion": 1,
  "profileName": "Team defaults",
  "enabled": true,
  "overrides": {
    "monitoring": {
      "enabled": true,
      "configuration": true,
      "compilation": true,
      "testFailure": true,
      "network": true,
      "exception": true,
      "generic": true,
      "success": false
    },
    "sound": {
      "useGlobalBuiltInSound": true,
      "globalBuiltInSoundId": "boom",
      "perKind": {
        "CONFIGURATION": { "enabled": true, "soundId": "boom" },
        "COMPILATION": { "enabled": true, "soundId": "punch" }
      },
      "success": { "enabled": false, "soundId": "yeah_boy" }
    },
    "volume": {
      "globalVolumePercent": 80,
      "perKind": {
        "CONFIGURATION": { "enabled": false, "volumePercent": 80 }
      }
    },
    "duration": {
      "alertDurationSeconds": 3,
      "useActualSoundDuration": false
    },
    "visualNotifications": {
      "showVisualNotification": true,
      "onError": true,
      "onSuccess": false
    },
    "minimumProcessDuration": {
      "seconds": 0
    }
  }
}
```

Supported override categories:
- master enabled
- per-kind monitoring toggles
- built-in/global sound behavior
- per-kind sound enabled/id
- success sound enabled/id
- global volume
- per-kind volume overrides
- alert duration
- `useActualSoundDuration` / play once
- visual notification settings
- minimum process duration threshold

Missing fields mean no repo override for that field.

### Validation And Fallback

- `schemaVersion` must be present and equal to `1`
- Missing file means no repo profile layer
- Invalid JSON, invalid schema, unreadable file, non-regular file, or oversized file returns an invalid result and falls back to global + workspace project overrides
- Unknown top-level or override fields are ignored with warnings
- Invalid enum/sound IDs are ignored with warnings
- Numeric values are clamped to supported ranges where applicable
- `enabled = false` disables the repo profile layer while preserving status visibility

### Explicit Exclusions

Repo profiles do **not** include:
- custom regex rules
- suppression rules
- terminal exit-code rules
- rule presets
- rule import/export schema data
- Alert History
- custom audio file paths
- telemetry or network behavior
- script execution
- terminal reflection behavior
- automatic file creation/writes

## Rule Import / Export JSON

Rules-only local JSON import/export is handled by `RuleImportExportBundle` and `RuleImportExportService`. Schema version 2 covers exactly:
- `customRules`
- `suppressionRules`
- `exitCodeRules`

It does **not** cover:
- global sound settings
- per-kind volume
- success settings
- project profiles / project overrides
- alert history
- snooze state
- full plugin settings bundles

### Top-Level Structure

```json
{
  "schemaVersion": 2,
  "exportedAt": "2026-05-02T00:00:00Z",
  "pluginVersion": "1.1.19",
  "customRules": [
    {
      "id": "8e2d8f2f-4d8b-46cc-8f22-82a904f1d6aa",
      "enabled": true,
      "pattern": "lint failed",
      "matchTarget": "LINE_TEXT",
      "kind": "COMPILATION"
    }
  ],
  "suppressionRules": [
    {
      "id": "known-harmless-lint-warning",
      "enabled": true,
      "pattern": "Known harmless lint warning",
      "matchTarget": "LINE_TEXT",
      "description": "Noisy linter warning that should not alert"
    }
  ],
  "exitCodeRules": [
    {
      "exitCode": 137,
      "enabled": true,
      "kind": "GENERIC",
      "soundId": "boom",
      "suppress": false
    }
  ]
}
```

### Suppression Rule Runtime Semantics

- Suppression wins over custom regex classification and built-in classification
- Run/Debug supports LINE_TEXT chunks, FULL_OUTPUT final buffer checks, and EXIT_CODE_AND_TEXT final output plus exit-code checks
- Console supports LINE_TEXT only
- Terminal supports EXIT_CODE_AND_TEXT only against command context plus exit code
- Matching enabled suppression rules return before `AlertDispatcher.tryAlert()`
- Suppressed alerts do not play sound, show visual notifications, or enter Alert History
- Suppression rules do not alter sound selection, volume, play-once duration, project profiles, alert history persistence, telemetry, network behavior, or remote rule downloads

### Import Rules

- `schemaVersion` must be `1` or `2`
- Schema version `1` remains import-compatible and simply imports no suppression rules unless the section is present
- Top-level JSON must be an object
- `customRules`, `suppressionRules`, and `exitCodeRules` sections may be missing; missing sections import as empty lists
- Unknown top-level fields are rejected to avoid importing full settings bundles accidentally
- Rule ordering is preserved
- Rule ids are preserved when present; missing or blank custom rule ids are regenerated with a validation note
- Unsupported `matchTarget`, `kind`, and bundled sound ids cause that row to be skipped with a user-facing warning
- Invalid regex text is preserved and reported; runtime continues to skip invalid custom/suppression regex rules until the user edits them
- Imported table changes are not persisted until Apply is clicked
- Reset discards imported-but-not-applied table changes

### Export Rules

- Export reads the current settings UI table-model state, including unsaved edits
- Export writes `schemaVersion = 2`
- Export writes pretty-printed JSON to a user-selected local file
- Existing export files are not overwritten silently; the UI asks for confirmation
- Export does not write any permanent storage outside the selected file
- Import/export does not use network, telemetry, or imported-content execution

## Rule Presets

Phase 5 presets do not add any new persisted state. `RulePresetService` supplies bundled local preset data and `ErrorSoundConfigurable` appends accepted preset rows to the existing settings UI table models before Apply.

Presets populate only:
- `customRules`
- `exitCodeRules`

Available preset bundles:
- Java / Spring Boot
- Gradle / Maven
- Node.js / npm / pnpm
- Python / pytest
- Docker / Kubernetes
- Frontend test runners (Jest / Vitest / Cypress / Playwright)

Preset behavior:
- Adds rows to the current table models only; no settings are persisted until Apply
- Reset discards preset additions that have not been applied
- Duplicate custom rule IDs are skipped
- Existing terminal exit codes are skipped
- Existing user-created rules are preserved and preset rules are appended after them
- Presets do not modify sound settings, volume settings, success settings, project profiles/overrides, alert history, snooze state, or full profiles/settings bundles
- Presets are bundled locally; no network, telemetry, remote preset downloads, script execution, or file writes are involved

---
*Last updated from code scan: 2026-05-18*
