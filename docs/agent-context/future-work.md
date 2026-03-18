# Future Work — Error Sound Alert

Suggested features and safe extension points for future development.

---

## 1. Success / Completion Sounds

**Description:** Play a distinct sound when a process exits with code 0, especially after a long build.

**Where it belongs:**
- Add `ErrorKind.SUCCESS` (or introduce a separate `AlertKind` enum)
- Modify `AlertOnErrorExecutionListener.processTerminated()` to handle exitCode == 0
- Add success sound settings to `AlertSettings.State`
- Add UI controls to `ErrorSoundConfigurable`

**Files changed:** `ErrorKind.kt`, `AlertOnErrorExecutionListener.kt`, `AlertSettings.kt`, `ErrorSoundConfigurable.kt`, `ErrorSoundToolWindowFactory.kt`

**Risks:** Must not trigger on every short process exit (e.g., lint checks). Needs a duration threshold.

---

## 2. Execution Duration Threshold / Focus Mode

**Description:** Only play alert if the process ran longer than `X` seconds (user configurable).

**Where it belongs:**
- Record start time in `AlertOnErrorExecutionListener.processStarted()`
- Compare elapsed time in `processTerminated()` before dispatching
- Add `minProcessDurationSeconds` field to `AlertSettings.State`
- Add slider/field to `ErrorSoundConfigurable`

**Files changed:** `AlertOnErrorExecutionListener.kt`, `AlertSettings.kt`, `ErrorSoundConfigurable.kt`

**Risks:** Low — straightforward timestamp comparison. Decide if this applies to console and terminal paths too.

---

## 3. Snooze / Mute / Do Not Disturb

**Description:** Temporary mute (e.g., "Mute for 1 hour") via toolbar button. Could also auto-mute when IDE is in Presentation Mode.

**Where it belongs:**
- Add a transient `mutedUntil` timestamp in `AlertSettings` (not persisted) or a new singleton
- Check `mutedUntil` in `AlertDispatcher.tryAlert()` or `AlertMonitoring.shouldMonitor()`
- Add mute button to `ErrorSoundToolWindowFactory`

**Files changed:** `AlertDispatcher.kt` or `AlertMonitoring.kt`, `AlertSettings.kt`, `ErrorSoundToolWindowFactory.kt`

**Risks:** Medium — must decide whether mute state survives IDE restart. Keep it transient to avoid accidental permanent muting.

---

## 4. Visual Notifications

**Description:** Pair audio alert with a brief IntelliJ notification (balloon/toast) showing what failed.

**Where it belongs:**
- Add notification logic in `AlertDispatcher.tryAlert()` after sound plays
- Use `NotificationGroupManager` with a registered notification group in `plugin.xml`
- Add settings toggle for visual notifications

**Files changed:** `AlertDispatcher.kt`, `plugin.xml`, `AlertSettings.kt`, `ErrorSoundConfigurable.kt`

**Risks:** Low — standard IntelliJ API. Watch for notification spam; respect `AlertEventGate` deduplication.

---

## 5. Custom Regex Error Rules

**Description:** Let users define custom error patterns in settings, with assigned ErrorKind or custom sounds.

**Where it belongs:**
- Add a list of `CustomRule(regex: String, kind: ErrorKind, soundId: String?)` to `AlertSettings.State`
- Evaluate custom rules first in `ErrorClassifier.detect()`
- Add table UI in `ErrorSoundConfigurable` for managing rules

**Files changed:** `ErrorClassifier.kt`, `AlertSettings.kt`, `ErrorSoundConfigurable.kt`

**Risks:** Medium — regex compilation errors need graceful handling. Performance impact if many rules are evaluated per console line.

---

## 6. Exit-Code-Specific Terminal Sounds

**Description:** Map specific terminal exit codes (e.g., 137 = OOM, 130 = SIGINT) to distinct sounds.

**Where it belongs:**
- Extend `ErrorClassifier.detectTerminal()` with exit-code-to-kind mapping
- Or add a user-configurable exit code → sound map to `AlertSettings.State`
- Update terminal handler in `AlertOnTerminalCommandListener.handleCommandFinished()`

**Files changed:** `ErrorKind.kt`, `AlertOnTerminalCommandListener.kt`, `AlertSettings.kt`, possibly `ErrorSoundConfigurable.kt`

**Risks:** Low — exit code mapping is straightforward. Watch for SIGINT (130) which is user-initiated and should probably not alert.

---

## 7. Per-Project Settings

**Description:** Override global settings per-project (e.g., loud sounds for critical projects, muted for noisy ones).

**Where it belongs:**
- Create a `Service.Level.PROJECT` version of `AlertSettings`
- Modify `AlertDispatcher` and listeners to check project-level settings first, fall back to app-level
- Add project-level configurable or tool window override

**Files changed:** New `ProjectAlertSettings.kt`, `AlertDispatcher.kt`, `AlertOnErrorExecutionListener.kt`, `ErrorConsoleFilterProvider.kt`, `AlertOnTerminalCommandListener.kt`

**Risks:** High — significant architectural change. Must handle settings merge carefully. Consider incremental approach (project-level enable/disable first).

---
*Last updated from code scan: 2026-03-18*
