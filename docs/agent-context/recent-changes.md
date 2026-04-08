# Recent Changes — Error Sound Alert

Engineering-significant changes to the codebase. Not a full changelog — focuses on architectural and compatibility changes.

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
*Last updated from code scan: 2026-04-07*
