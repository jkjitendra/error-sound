# Settings and State Reference — Error Sound Alert

Complete reference for all persisted and transient state in the plugin.

---

## Persisted State (`AlertSettings.State`)

Stored in `errorSoundAlert.xml`. Loaded and normalized by `AlertSettings.loadState()`.

### Master toggle
| Field | Type | Default | Notes |
|---|---|---|---|
| `enabled` | Boolean | `true` | Master enable/disable for all alerts |

### Per-kind monitor flags (sidebar panel)
| Field | Type | Default |
|---|---|---|
| `monitorConfiguration` | Boolean | `true` |
| `monitorCompilation` | Boolean | `true` |
| `monitorTestFailure` | Boolean | `true` |
| `monitorNetwork` | Boolean | `true` |
| `monitorException` | Boolean | `true` |
| `monitorGeneric` | Boolean | `true` |
| `monitorSuccess` | Boolean | `false` |

### Sound settings
| Field | Type | Default | Notes |
|---|---|---|---|
| `volumePercent` | Int | `80` | 0–100; clamped in `loadState()` |
| `configurationVolumePercent` | Int? | `null` | Per-kind volume override; null = inherit `volumePercent` |
| `compilationVolumePercent` | Int? | `null` | Same; clamped to 0–100 when non-null |
| `testFailureVolumePercent` | Int? | `null` | Same |
| `networkVolumePercent` | Int? | `null` | Same |
| `exceptionVolumePercent` | Int? | `null` | Same |
| `genericVolumePercent` | Int? | `null` | Same |
| `successVolumePercent` | Int? | `null` | Same |
| `soundSource` | String | `BUNDLED` | `SoundSource.BUNDLED` or `CUSTOM` |
| `builtInSoundId` | String | `"boom"` | Global sound ID; normalized against `BuiltInSounds` |
| `useGlobalBuiltInSound` | Boolean | `true` | One sound for all error kinds |
| `{kind}SoundEnabled` | Boolean | varies | Per-kind enable; Success default `false` |
| `{kind}SoundId` | String | varies | Per-kind sound ID; normalized in `loadState()` |
| `customSoundPath` | String | `""` | Absolute path to custom audio file |
| `alertDurationSeconds` | Int | `3` | 1–10; clamped |

### Process duration threshold
| Field | Type | Default | Notes |
|---|---|---|---|
| `minProcessDurationSeconds` | Int | `0` | 0–300; clamped. Applies to Run/Debug only |

### Visual notifications
| Field | Type | Default | Notes |
|---|---|---|---|
| `showVisualNotification` | Boolean | `false` | Master balloon toggle |
| `visualNotificationOnError` | Boolean | `true` | Show balloon for error alerts |
| `visualNotificationOnSuccess` | Boolean | `true` | Show balloon for success alerts |

### Custom regex rules (Phase 5)
| Field | Type | Default | Notes |
|---|---|---|---|
| `customRules` | `MutableList<CustomRuleState>` | `mutableListOf()` | Evaluated before built-in classification |

#### `CustomRuleState` fields
| Field | Type | Default | Normalization |
|---|---|---|---|
| `id` | String | `UUID.randomUUID()` | Blank ID regenerated in `loadState()` |
| `enabled` | Boolean | `true` | — |
| `pattern` | String | `""` | Trimmed and capped to 500 chars |
| `matchTarget` | String | `LINE_TEXT` | Normalized to valid `MatchTarget` name, default LINE_TEXT |
| `kind` | String | `GENERIC` | Normalized to allowed kind name; NONE and SUCCESS rejected, default GENERIC |

#### `MatchTarget` enum
| Value | Supported paths |
|---|---|
| `LINE_TEXT` | Run/Debug chunks, Console filter |
| `FULL_OUTPUT` | Run/Debug final buffered output only |
| `EXIT_CODE_AND_TEXT` | Run/Debug final output, Terminal |

#### Allowed kinds for custom rules
CONFIGURATION, COMPILATION, TEST_FAILURE, NETWORK, EXCEPTION, GENERIC.
NONE and SUCCESS are explicitly excluded.

#### Rule limits
- Max 100 rules (excess silently dropped at `loadState()` normalization)
- Max 500 chars per pattern (excess truncated at normalization)

---

## Project-Level State (`ProjectAlertSettings.State`) — Phase 7

Stored in `WORKSPACE_FILE` (workspace-scoped, not shared across clones). Loaded by `ProjectAlertSettings.loadState()`.

| Field | Type | Default | Notes |
|---|---|---|---|
| `useOverride` | Boolean | `false` | Whether the project-level `enabled` override is active |
| `enabledOverride` | Boolean | `true` | The override value; ignored when `useOverride == false` |

**Effective nullable override (via `effectiveEnabledOverride(): Boolean?`):**
- `useOverride == false` → returns `null` (inherit global `enabled`)
- `useOverride == true` → returns `enabledOverride`

**Phase 7 scope:** Only `enabled` may be overridden per project. All other settings come from `AlertSettings.State`.

---

## Resolved Settings (`ResolvedSettingsResolver`) — Phase 7

Not persisted. Computed on-demand via `resolve()`.

| Output field | Resolution logic |
|---|---|
| `enabled` | `ProjectAlertSettings.effectiveEnabledOverride() ?: AlertSettings.state.enabled` |
| All other fields | Copied unchanged from `AlertSettings.state` |

Usage: all three detection paths call `ResolvedSettingsResolver.getInstance(project).resolve()` at dispatch time.

---

## Transient State (`SnoozeState`)

Not persisted. Resets on IDE restart.

| Field | Type | Notes |
|---|---|---|
| `snoozeUntilEpochMillis` | AtomicLong | `0` = not snoozed |

**Methods:** `isSnoozed()`, `snooze(minutes)`, `resume()`, `statusLabel()`
**Bus topic:** `SnoozeState.TOPIC` — application-level; notifies subscribers on snooze/resume.

---

## Runtime Cache (`AlertSettings`)

| Field | Type | Notes |
|---|---|---|
| `compiledRuleEngine` | `CustomRuleEngine?` (`@Volatile`) | Lazily built from `customRules`; invalidated by `loadState()` |

Obtain via `AlertSettings.getInstance().getCompiledRuleEngine()`.

---

## Settings Normalization Summary

`AlertSettings.loadState()` applies the following normalizations before storing the state:

1. `volumePercent` clamped to 0–100
2. `{kind}VolumePercent` fields (`configurationVolumePercent` … `successVolumePercent`) — each clamped to 0–100 when **non-null**; `null` is preserved unchanged (Phase 8)
3. All `*SoundId` fields normalized via `normalizeSoundId()` (falls back to `BuiltInSounds.default.id`)
4. `alertDurationSeconds` clamped to 1–10
5. `minProcessDurationSeconds` clamped to 0–300
6. `customRules` list:
   - Truncated to first 100 entries
   - Each rule: pattern trimmed + capped to 500 chars; `matchTarget` normalized; `kind` normalized; blank `id` regenerated
7. `compiledRuleEngine` cache invalidated after every normalization

---
*Last updated: 2026-04-12*
