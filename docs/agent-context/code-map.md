# Code Map — Error Sound Alert

Class-by-class reference for the `com.drostwades.errorsound` package.

---

## AlertDispatcher

**File:** `AlertDispatcher.kt` (20 lines)
**Purpose:** Single choke-point between the three detection paths and the audio player.

| Method | Signature | Description |
|---|---|---|
| `tryAlert` | `(key: String, settings: State, kind: ErrorKind)` | Routes through `AlertMonitoring` → `AlertEventGate` → `ErrorSoundPlayer` |

- **Inputs:** deduplication key, current settings state, detected error kind
- **Outputs:** none (fire-and-forget side effect)
- **Side effects:** may trigger audio playback
- **Risk:** LOW — thin routing layer, but every detection path depends on it

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

**File:** `AlertOnErrorExecutionListener.kt` (79 lines)
**Purpose:** Listens to Run/Debug process lifecycle. Captures output, classifies errors, dispatches alerts on process termination.

| Method | Signature | Description |
|---|---|---|
| `processStarted` | `(executorId, env, handler)` | Attaches `ProcessListener` to capture output |

**Internal logic:**
- Output buffer capped at 1M characters
- `ErrorClassifier.detect()` runs on each chunk + final buffer
- Priority system: CONFIGURATION > COMPILATION > TEST_FAILURE > NETWORK > EXCEPTION > GENERIC > NONE
- If final kind is NONE and exitCode == 0, converts to SUCCESS
- Dedup key: `"exec:{handlerIdentityHash}:{errorKind}"`
- Routes through `AlertDispatcher.tryAlert()`

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
| `handleCommandFinished` | private | Extracts command+exitCode, classifies, dispatches via `AlertDispatcher` |
| `extractCommandAndExitCode` | private | Reflection-based extraction from event objects |
| `getShellIntegration` | private | 4-strategy fallback to get shell integration from a view |

- **Risk:** HIGH — 591 lines of reflection. Most likely file to break with IDE updates.
- **See:** `docs/agent-context/terminal-integration.md` for detailed breakdown.

---

## AlertSettings

**File:** `AlertSettings.kt` (79 lines)
**Purpose:** Persistent application-level settings. Stored in `errorSoundAlert.xml`.

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
- `customSoundPath` — absolute path to custom audio file

**Validation:** `loadState()` normalizes sound IDs and clamps numeric values.

- **Risk:** LOW — standard `PersistentStateComponent` pattern

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

**File:** `ErrorConsoleFilterProvider.kt` (50 lines)
**Purpose:** Registered as `consoleFilterProvider` extension. Provides an `ErrorDetectionFilter` that matches error patterns in every console line.

**ErrorDetectionFilter patterns:** Exception, Error, FATAL, `Caused by:`, stack trace lines, BUILD FAILED, FAILURE:, Tests failed, compilation failed, command not found.

**Dedup key format:** `"console:{project.locationHash}:{errorKind}"`

- **Side effects:** Calls `AlertDispatcher.tryAlert()` — returns `null` filter result (no text modification)
- **Risk:** LOW-MEDIUM — false positives possible for benign lines containing "error"

---

## ErrorClassifier / ErrorKind

**File:** `ErrorKind.kt` (74 lines)
**Purpose:** Error classification logic.

**ErrorKind enum:** NONE, CONFIGURATION, COMPILATION, TEST_FAILURE, NETWORK, EXCEPTION, GENERIC, SUCCESS

| Method | Description |
|---|---|
| `ErrorClassifier.detect(outputText, exitCode)` | Full-text classification against hardcoded string patterns |
| `ErrorClassifier.detectTerminal(command, exitCode)` | Terminal-only: returns GENERIC for exitCode ≠ 0, NONE for 0 |

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

**File:** `ErrorSoundConfigurable.kt` (456 lines)
**Purpose:** Settings UI panel registered under **Settings → Tools → Error Sound Alert**.

**Key behaviors:**
- Implements `Configurable` interface (`createComponent`, `isModified`, `apply`, `reset`, `disposeUIResources`)
- Preview plays sound immediately on combo selection (suppressed during programmatic updates via `suppressPreview` flag)
- Global mode syncs all per-kind combos to the global selection
- Custom file path refreshes all combo models to include/exclude custom option
- `apply()` stops any active preview before saving

- **Risk:** LOW — UI-only, no business logic side effects beyond settings persistence

---

## ErrorSoundPlayer

**File:** `ErrorSoundPlayer.kt` (311 lines)
**Purpose:** Audio playback engine. Handles both real alerts and settings preview.

| Method | Description |
|---|---|
| `play(settings, kind)` | Main alert path — 250ms debounce, executor-submitted |
| `previewBuiltIn(id, vol, dur)` | Preview a built-in sound |
| `previewCustom(path, vol, dur)` | Preview a custom file |
| `stopPreview()` | Cancel active preview |

**Internals:**
- Single-threaded bounded executor for alerts
- Preview uses daemon threads with token-based cancellation
- `playClipLooping()` — opens `Clip`, loops until duration expires, closes
- Volume: `FloatControl.MASTER_GAIN` with dB scaling (`20 * log10(linear)`)
- Tone fallback: generates 880 Hz WAV in-memory

- **Risk:** LOW-MEDIUM — clip resources must be closed properly; thread interruption handling for preview

---

## ErrorSoundToolWindowFactory

**File:** `ErrorSoundToolWindowFactory.kt` (357 lines)
**Purpose:** Error Monitor sidebar panel. Provides quick toggles for error monitoring categories.

**Features:**
- Master enable/disable checkbox
- Per-kind checkboxes with descriptions
- Quick actions: Select All, Clear All
- Presets: All, Build Only, Runtime Only
- "Open sound settings" button → opens `ErrorSoundConfigurable`

**Behavior:** Directly mutates `AlertSettings.state` fields (monitoring flags) on checkbox changes — no apply/reset cycle.

- **Risk:** LOW — UI-only

---
*Last updated from code scan: 2026-03-19*
