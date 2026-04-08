# Future Work — Error Sound Alert

Suggested features and safe extension points for future development.

---

## 1. Visual Notifications — DONE (v1.1.4)

Implemented in Phase 4. See `recent-changes.md` for the full engineering summary.

---

## 2. Custom Regex Error Rules — DONE (v1.1.5)

Implemented in Phase 5. See `recent-changes.md` for the full engineering summary.

---

## 3. Exit-Code-Specific Terminal Sounds

**Description:** Map specific terminal exit codes (e.g., 137 = OOM, 130 = SIGINT) to distinct sounds.

**Where it belongs:**
- Extend `ErrorClassifier.detectTerminal()` with exit-code-to-kind mapping
- Or add a user-configurable exit code → sound map to `AlertSettings.State`
- Update terminal handler in `AlertOnTerminalCommandListener.handleCommandFinished()`

**Files changed:** `ErrorKind.kt`, `AlertOnTerminalCommandListener.kt`, `AlertSettings.kt`, possibly `ErrorSoundConfigurable.kt`

**Risks:** Low — exit code mapping is straightforward. Watch for SIGINT (130) which is user-initiated and should probably not alert.

---

## 4. Per-Project Settings

**Description:** Override global settings per-project (e.g., loud sounds for critical projects, muted for noisy ones).

**Where it belongs:**
- Create a `Service.Level.PROJECT` version of `AlertSettings`
- Modify `AlertDispatcher` and listeners to check project-level settings first, fall back to app-level
- Add project-level configurable or tool window override

**Files changed:** New `ProjectAlertSettings.kt`, `AlertDispatcher.kt`, `AlertOnErrorExecutionListener.kt`, `ErrorConsoleFilterProvider.kt`, `AlertOnTerminalCommandListener.kt`

**Risks:** High — significant architectural change. Must handle settings merge carefully. Consider incremental approach (project-level enable/disable first).

---
*Last updated from code scan: 2026-04-07*
