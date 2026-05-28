# AGENTS.md — Error Sound Alert Plugin

> Top-level agent operating instructions for the Error Sound Alert IntelliJ plugin.

## Repo Purpose

IntelliJ Platform plugin that plays an audio alert when a Run/Debug process, console output, or terminal command fails. Package: `com.drostwades.errorsound`. Published on JetBrains Marketplace.

## What This Plugin Does

1. Listens to Run/Debug process exits via `ExecutionListener`.
2. Scans console output for error patterns via `ConsoleFilterProvider`.
3. Monitors terminal command completions via reflection-based listener on Classic/Block and Reworked terminal internals (optional dependency on `org.jetbrains.plugins.terminal`).
4. Checks user suppression rules before dispatch, then routes accepted errors through `AlertDispatcher → SnoozeState → AlertMonitoring → AlertEventGate → AlertHistoryService → ErrorSoundPlayer`.
5. Plays a WAV sound (built-in or custom) with configurable volume, configured duration looping, or the optional actual sound file duration play-once mode.
6. Records accepted alerts in an in-memory Error Monitor history after snooze, monitoring, and deduplication gates accept the event.
7. Resolves effective settings via `ResolvedSettingsResolver` using the workspace-scoped Profile Merge Policy: default global application settings → optional repo-shared `.error-sound-alert.json` profile → workspace project profile overrides, with alternate policies for ignoring repo profiles, making repo profiles win, or using global settings only.
8. Provides bundled local Rule Presets that append Custom Regex Rules and conservative Terminal Exit-Code Rules in the settings UI.
9. Provides local Ignore / Suppression Rules that silence known noisy false positives before alert dispatch.
10. Adds actionable visual notification controls for opening settings, opening Error Monitor, muting, disabling the current kind, and viewing capped alert details.
11. Provides a Settings-only Diagnostics / Self-Test section for local applied-status checks, sound self-tests, and a real IDE balloon notification test.
12. Supports an optional team-shared repo profile file at `project.basePath/.error-sound-alert.json` for safe shared profile defaults.
13. Lets users choose the project/workspace Profile Merge Policy from Error Monitor → Project Profile.
14. Applies optional Run/Debug-only per-run-configuration overrides after global/repo/project settings resolution.

## Minimum Build Baseline

| Component | Version |
|---|---|
| Gradle | 9.0 |
| Kotlin | 2.3.10 (apiVersion/languageVersion = 2.0) |
| Java toolchain | 21 |
| IntelliJ Platform Gradle Plugin | 2.12.0 |
| Target platform | IC 2024.3 |
| `sinceBuild` | 243 |
| `untilBuild` | unset (open-ended) |
| **Plugin version** | **1.1.22** |

## Completed Phases

- Phase 1 — Success Sounds
- Phase 2 — Execution Time Threshold
- Phase 3 — Snooze / Mute
- Phase 4 — Visual Alert Companion
- Phase 5 — Custom Regex Rules
- Phase 6 — Exit-Code-Specific Terminal Sounds
- Phase 7 — Project-Level Profiles (initial per-project `enabled` override)
- Phase 8 — Per-Kind Volume
- Phase 1 Roadmap — Rule Testing Sandbox
- Phase 2 Roadmap — Rule Match Explanation (internal runtime plumbing)
- Phase 3 Roadmap — Alert History Panel
- Phase 4 Roadmap — Rule Import/Export
- Phase 5 Roadmap — Preset Bundles
- PR #32 Integration — Play Once Sound Duration
- Phase 6 Roadmap — Ignore / Suppression Rules
- Phase 7 Roadmap — Actionable Notification Actions v1
- Phase 8 Roadmap — Diagnostics / Self-Test
- Phase 9 Roadmap — Full Per-Project Profiles
- Phase 10 Roadmap — Team-Shared Repo Profile File
- Compatibility Fix — Marketplace Verifier API Usage
- Phase 11 Roadmap — Profile Merge Policy UI
- Phase 12 Roadmap — Per-Run-Configuration Overrides

## Safe Editing Rules

1. **Do NOT** change `AlertDispatcher`, `AlertEventGate`, or `AlertMonitoring` without understanding the deduplication flow.
2. **Do NOT** add direct imports from `org.jetbrains.plugins.terminal` — terminal integration uses reflection only.
3. **Do NOT** lower `sinceBuild` below 243 — Kotlin 2.x stdlib requires 2024.3+.
4. **Do NOT** set `untilBuild` without understanding forward-compatibility impact.
5. Keep `kotlin.stdlib.default.dependency=false` in `gradle.properties` — the platform bundles its own stdlib.
6. Always run `./gradlew buildPlugin` after changes.
7. Keep README, Marketplace description (`build.gradle.kts`), and plugin.xml description aligned.
8. When adding a new project service, register it in `plugin.xml` under `<extensions>` as a `<projectService>`.

## Sensitive Files / Areas

| File | Risk |
|---|---|
| `AlertOnTerminalCommandListener.kt` | **HIGH** — 591 lines of reflection-heavy code. Breaks with internal terminal API changes. |
| `AlertEventGate.kt` | **MEDIUM** — Deduplication gate. Wrong tuning causes duplicate or swallowed alerts. |
| `plugin.xml` / `terminal-features.xml` | **MEDIUM** — Extension point registrations. Misregistration = silent failure. |
| `build.gradle.kts` | **MEDIUM** — Platform plugin config, sinceBuild/untilBuild, signing, verification. |
| `ErrorSoundPlayer.kt` | **LOW-MEDIUM** — Audio thread management. Clip leaks if not closed properly. |
| `ProjectAlertSettings.kt` | **LOW** — Workspace-scoped persistent state; changing storage path would lose saved overrides. |
| `ResolvedSettingsResolver.kt` | **LOW-MEDIUM** — Applies the selected profile merge policy across global, repo profile, and workspace profile state for all detection paths without mutating stored settings. |
| `RunConfigurationOverrideEngine.kt` | **LOW-MEDIUM** — Applies Run/Debug-only run-configuration overrides to a run-specific effective settings copy; invalid regex must remain safe. |
| `RepoProfileService.kt` | **LOW-MEDIUM** — Reads untrusted local repo JSON; must stay local-only, read-only, and fail safe. |

## Required Verification Commands

```bash
# Build the plugin ZIP
./gradlew buildPlugin

# Clean build
./gradlew clean buildPlugin
```

## How to Update Docs When Code Changes

1. Update `docs/agent-context/code-map.md` when adding, removing, or changing a class.
2. Update `docs/agent-context/architecture.md` if the detection → dispatch → playback flow changes.
3. Update `docs/agent-context/build-and-compatibility.md` after any Gradle/Kotlin/Java/platform version change.
4. Update `docs/agent-context/terminal-integration.md` after any terminal reflection logic change.
5. Update `docs/agent-context/recent-changes.md` with a summary of the change made.
6. Update `references/` files if settings state, plugin.xml, or detection flow changes.
7. See `docs/agent-context/maintenance-rules.md` for the full update matrix.

---
*Last updated from code scan: 2026-05-27*
