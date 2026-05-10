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
| `minProcessDurationSeconds` | Int | `0` | 0–300. Alerts suppressed if process completes faster. Run/Debug only. |

### Rule Collections

| Field | Type | Default | Description |
|---|---|---|---|
| `customRules` | `MutableList<CustomRuleState>` | Empty list | User-defined regex rules evaluated before built-in classification |
| `exitCodeRules` | `MutableList<ExitCodeRuleState>` | 130 suppress, 127/137/143 GENERIC | Terminal exit-code mappings, sound overrides, or suppression |

`CustomRuleState` fields:
- `id: String`
- `enabled: Boolean`
- `pattern: String`
- `matchTarget: String` — `LINE_TEXT`, `FULL_OUTPUT`, or `EXIT_CODE_AND_TEXT`
- `kind: String` — allowed user-facing error kind

`ExitCodeRuleState` fields:
- `exitCode: Int`
- `enabled: Boolean`
- `kind: String` — allowed user-facing error kind
- `soundId: String?` — bundled sound override, or `null` for default resolution
- `suppress: Boolean`

## Validation (in `loadState`)

- `volumePercent` clamped to 0–100
- `alertDurationSeconds` clamped to 1–10
- All sound IDs normalized via `BuiltInSounds.findByIdOrDefault()`
- Custom file ID (`__custom_file__`) preserved as-is
- Custom rules are normalized to existing limits: first `CustomRuleEngine.MAX_RULES`, patterns trimmed/truncated to `MAX_PATTERN_LENGTH`, blank ids regenerated, unsupported targets default to `LINE_TEXT`, unsupported kinds default to `GENERIC`
- Exit-code rule kinds are normalized to allowed error kinds, and blank or custom-file sound ids become `null`

## SoundSource Enum

```kotlin
enum class SoundSource { BUNDLED, CUSTOM }
```

## Rule Import / Export JSON

Phase 4 import/export is a rules-only local JSON bundle handled by `RuleImportExportBundle` and `RuleImportExportService`. It covers exactly:
- `customRules`
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
  "schemaVersion": 1,
  "exportedAt": "2026-05-02T00:00:00Z",
  "pluginVersion": "1.1.13",
  "customRules": [
    {
      "id": "8e2d8f2f-4d8b-46cc-8f22-82a904f1d6aa",
      "enabled": true,
      "pattern": "lint failed",
      "matchTarget": "LINE_TEXT",
      "kind": "COMPILATION"
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

### Import Rules

- `schemaVersion` must be `1`
- Top-level JSON must be an object
- `customRules` and `exitCodeRules` sections may be missing; missing sections import as empty lists
- Unknown top-level fields are rejected to avoid importing full settings bundles accidentally
- Rule ordering is preserved
- Rule ids are preserved when present; missing or blank custom rule ids are regenerated with a validation note
- Unsupported `matchTarget`, `kind`, and bundled sound ids cause that row to be skipped with a user-facing warning
- Invalid regex text is preserved and reported; runtime continues to skip invalid regex rules until the user edits them
- Imported table changes are not persisted until Apply is clicked
- Reset discards imported-but-not-applied table changes

### Export Rules

- Export reads the current settings UI table-model state, including unsaved edits
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
*Last updated from code scan: 2026-05-10*
