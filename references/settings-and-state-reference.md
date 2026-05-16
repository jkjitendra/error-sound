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

## Rule Import / Export JSON

Rules-only local JSON import/export is handled by `RuleImportExportBundle` and `RuleImportExportService`. Schema version 2 covers exactly:
- `customRules`
- `suppressionRules`
- `exitCodeRules`

It does **not** cover:
- global sound settings
- per-kind volume
- success settings
- project overrides
- alert history
- snooze state
- full plugin settings bundles

### Top-Level Structure

```json
{
  "schemaVersion": 2,
  "exportedAt": "2026-05-02T00:00:00Z",
  "pluginVersion": "1.1.16",
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
- Presets do not modify sound settings, volume settings, success settings, project overrides, alert history, snooze state, or full profiles/settings bundles
- Presets are bundled locally; no network, telemetry, remote preset downloads, script execution, or file writes are involved

---
*Last updated from code scan: 2026-05-16*
