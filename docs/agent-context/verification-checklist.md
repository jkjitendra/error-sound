# Verification Checklist — Error Sound Alert

Quick-reference checklist for verifying changes. Run these after any modification.

---

## Build Commands

```bash
# Standard build
./gradlew buildPlugin

# Verify compatibility against recommended IDEs
./gradlew verifyPlugin

# Clean build (use after dependency/config changes)
./gradlew clean buildPlugin
```

## What to Test After Touching…

### Build Config (`build.gradle.kts`, `gradle.properties`, `settings.gradle.kts`)
- [ ] `./gradlew clean buildPlugin` succeeds
- [ ] `./gradlew verifyPlugin` passes
- [ ] Check `sinceBuild` / `untilBuild` are correct
- [ ] Verify `kotlin.stdlib.default.dependency=false` is still in `gradle.properties`
- [ ] If version changed: update README, changelog, Marketplace description

### Settings UI (`ErrorSoundConfigurable.kt`)
- [ ] `./gradlew buildPlugin` succeeds
- [ ] Open Settings → Tools → Error Sound Alert — panel renders correctly
- [ ] Toggle all controls — `isModified()` returns `true`
- [ ] Apply → settings persist (check `errorSoundAlert.xml` in IDE config)
- [ ] Reset → reverts to saved values
- [ ] Global mode toggle: per-kind combos sync to global sound
- [ ] Custom file path: preview button enables/disables correctly
- [ ] Sound preview plays when selecting combo items

### Console Detection (`ErrorConsoleFilterProvider.kt`, `ErrorKind.kt`)
- [ ] `./gradlew buildPlugin` succeeds
- [ ] Run a failing build (e.g., syntax error) — sound plays
- [ ] Run a passing build — no sound
- [ ] Check dedup: rapid consecutive errors don't produce overlapping sounds
- [ ] Verify no false positives for benign "error" mentions

### Execution Listener (`AlertOnErrorExecutionListener.kt`)
- [ ] `./gradlew buildPlugin` succeeds
- [ ] Run a failing Run configuration — sound plays
- [ ] Run a successful Run configuration — no sound
- [ ] Test with a process that produces >1M chars output — no crash
- [ ] Error classification: check that the correct `ErrorKind` is detected

### Terminal Listener (`AlertOnTerminalCommandListener.kt`)
- [ ] `./gradlew buildPlugin` succeeds
- [ ] `./gradlew verifyPlugin` passes (critical for reflection-based code)
- [ ] Open terminal, run `false` — sound plays
- [ ] Run `echo hello` — no sound
- [ ] Open multiple terminal tabs — each gets alerts
- [ ] Close and reopen terminal — alerts still work
- [ ] Check IDE log for unexpected warnings (debug-level logging is OK)
- [ ] Test on 2024.3 (Block terminal) if possible
- [ ] Test on 2025.x (Reworked terminal) if possible
- [ ] Test without terminal plugin installed — plugin loads without error

### Audio Playback (`ErrorSoundPlayer.kt`)
- [ ] `./gradlew buildPlugin` succeeds
- [ ] Built-in sounds play correctly at various volumes
- [ ] Custom file plays if path is valid
- [ ] Invalid custom file path → fallback to generated tone or system beep
- [ ] Alert duration: sound loops correctly for the configured duration
- [ ] Preview: plays and stops cleanly
- [ ] Concurrent alerts: no clip leaks or thread issues

### Deduplication (`AlertDispatcher.kt`, `AlertEventGate.kt`, `AlertMonitoring.kt`)
- [ ] `./gradlew buildPlugin` succeeds
- [ ] Same error within 4s → no duplicate sound (per-key cooldown)
- [ ] Any error within 2s of another → no duplicate (global cooldown)
- [ ] Different errors after cooldown → sound plays normally
- [ ] Error Monitor toggle off → error kind does not trigger alert
- [ ] Master disable → no alerts at all

### Tool Window (`ErrorSoundToolWindowFactory.kt`)
- [ ] `./gradlew buildPlugin` succeeds
- [ ] Error Monitor panel opens from View → Tool Windows → Error Monitor
- [ ] Master toggle works — disables/enables all checkboxes
- [ ] Per-kind checkboxes update settings immediately
- [ ] Presets (All / Build Only / Runtime Only) set correct checkboxes
- [ ] "Open sound settings" button navigates to settings panel

---
*Last updated from code scan: 2026-03-18*
