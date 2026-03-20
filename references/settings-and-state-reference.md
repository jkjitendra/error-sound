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

## Validation (in `loadState`)

- `volumePercent` clamped to 0–100
- `alertDurationSeconds` clamped to 1–10
- All sound IDs normalized via `BuiltInSounds.findByIdOrDefault()`
- Custom file ID (`__custom_file__`) preserved as-is

## SoundSource Enum

```kotlin
enum class SoundSource { BUNDLED, CUSTOM }
```

---
*Last updated from code scan: 2026-03-19*
