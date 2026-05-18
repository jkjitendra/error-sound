# Error Sound Alert

> An IntelliJ Platform plugin that plays an audio alert the moment a Run/Debug process exits with an error, a recognisable error pattern appears in console output, or a terminal command fails — so you can stay focused and only look up when something goes wrong.

[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains-Marketplace-orange?logo=jetbrains)](https://plugins.jetbrains.com/plugin/com.drostwades.errorsound)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build: Gradle](https://img.shields.io/badge/Build-Gradle-green?logo=gradle)](build.gradle.kts)

---

## Overview

**Error Sound Alert** listens to every Run/Debug process, console output, and Terminal command in your IDE. The moment a process terminates with a non-zero exit code, a recognisable error pattern appears in console output, or a terminal command fails, it plays a short audio alert. No more staring at the progress bar — just listen for the sound.

---

## Features

| Feature | Details |
|---|---|
| Smart error detection | Classifies errors as Configuration, Compilation, Test Failure, Network, Exception, or Generic |
| Error Monitor panel | Handy sidebar tool window to quickly toggle active error categories with presets and collapsible sections |
| Alert History | Read-only Error Monitor table showing recent accepted alerts with source, kind, cause, and context |
| Terminal support | Monitors both Run/Debug processes and built-in Terminal commands |
| Custom regex rules | Define LINE_TEXT, FULL_OUTPUT, and EXIT_CODE_AND_TEXT patterns that run before built-in classification |
| Suppression rules | Silence known noisy false positives with local regex rules before alert dispatch |
| Rule Testing Sandbox | Paste sample output and see which custom rule or built-in classifier would match |
| Terminal exit-code rules | Map terminal exit codes to error kinds, optional built-in sound overrides, or suppression |
| Rule import/export | Export and import custom regex rules, suppression rules, and terminal exit-code rules as local JSON |
| Rule presets | Add bundled custom regex and terminal exit-code rule bundles for common stacks |
| Diagnostics / Self-Test | Inspect applied status and run safe sound/notification checks from Settings |
| Success sounds | Optional alert when a Run/Debug process completes successfully |
| Visual notifications | Optional balloon notifications with actions for settings, Error Monitor, mute, kind disabling, and alert details |
| Snooze / mute | Temporarily silence alerts for 15 minutes or 1 hour from the Error Monitor panel |
| Full per-project profiles | Opt-in workspace-scoped project overrides for monitoring, sounds, volume, duration, notifications, and process-duration threshold |
| 7 built-in sounds | Boom, Faaa, Huh, Punch, Yeah Boy, Yooo, Dog Laughing |
| Custom audio support | Point to any local WAV / AIFF / AU file |
| Per-kind sounds | Assign a different sound to each error category |
| Global mode | Use one sound for all error types |
| Volume control | Global 0 – 100% volume plus optional per-kind volume overrides |
| Alert duration | 1 – 10 seconds, with automatic clip looping |
| Play once sound duration | Optional mode to play the selected sound once using its actual clip length |
| Minimum duration threshold | Skip Run/Debug alerts for processes that finish faster than a configured threshold |
| Instant preview | Hear any sound immediately from the settings panel |
| Cross-platform | macOS, Windows, Linux (JVM audio backend) |

---

## Supported IDEs

All IntelliJ-based IDEs — IntelliJ IDEA, PyCharm, WebStorm, GoLand, Rider, and more.

**Compatibility:** Build `243` (2024.3)+

---

## Installation

### From JetBrains Marketplace _(recommended)_

1. Open **Settings / Preferences → Plugins → Marketplace**
2. Search for **Error Sound Alert**
3. Click **Install** and restart the IDE

### From disk (manual)

1. [Build the plugin](#build) or download the ZIP from the [Releases](https://github.com/jkjitendra/error-sound/releases) page
2. Open **Settings / Preferences → Plugins → ⚙ → Install Plugin from Disk...**
3. Select the `error-sound-<version>.zip` from `build/distributions/`
4. Restart the IDE

---

## Configuration

Open **Settings / Preferences → Tools → Error Sound Alert** to configure audio specifics.

### Error Monitor Tool Window

A handy sidebar panel (View → Tool Windows → Error Monitor) to quickly turn on or off monitoring for specific error types without opening settings. It also includes snooze controls, full per-project profile overrides, and an Alert History table. The Project Profile, Error Types, and Success sections are collapsible so the right-side tool window stays compact. Supports one-click presets:
- **All**: Enable all error types
- **Build Only**: Focus on Configuration, Compilation, and Test Failures
- **Runtime Only**: Focus on Network, Exception, and Generic errors

The Alert History section shows recent accepted alerts in newest-first order. It is read-only, clearable, in-memory only, and bounded to the latest 100 entries. Rows show **Time**, **Source**, **Kind**, **Cause**, and **Context**; context may include project/config/command, exit code, matched rule id/pattern, or sound override status.

### Full Per-Project Profiles

Use **Error Monitor → Project Profile** to enable opt-in project profile overrides for the current workspace. When **Use project profile overrides** is unchecked, the project inherits global settings. When it is checked, only the selected override groups replace global values; any unselected fields continue inheriting global settings.

Supported project override groups include master monitoring, per-kind monitoring toggles, built-in/global sound behavior, per-kind and success sound selections, global and per-kind volume, alert duration, **Use actual sound file duration (play once)**, visual notification settings, and the minimum process duration threshold. Project profiles are stored in JetBrains workspace state and are not exported by rule import/export.

Use **Copy current global settings** to seed a project profile from the current global configuration, or **Reset project overrides** to return the project to global inheritance. The initial enabled-only project override from earlier versions is preserved and migrated into the new master profile behavior.

Phase 9 keeps rules and operational history global: custom regex rules, suppression rules, terminal exit-code rules, rule presets, rule import/export, Alert History, and terminal integration are not project-scoped.

### Audio Settings

| Option | Description |
|---|---|
| Enable / Disable | Master toggle for all alerts |
| Sound source | **Built-in** (bundled WAV) or **Custom** (local file path) |
| Global sound mode | One sound for all error types |
| Per-kind sounds | Individual sound per error category |
| Per-kind volume | Optional independent volume for each error/success kind |
| Custom file path | Absolute path to a WAV / AIFF / AU file |
| Volume | 0 – 100% |
| Alert duration | 1 – 10 seconds; used by default to loop/restart short clips until time expires |
| Use actual sound file duration (play once) | Disabled by default. When enabled, the selected file-based sound starts once, ignores the configured alert duration, and disables the duration slider/value label |
| Minimum process duration | Suppress Run/Debug alerts for short-lived processes |
| Visual notifications | Show optional balloon notifications for errors and/or successes, with actionable controls |
| Custom regex rules | Add user-defined regex rules with LINE_TEXT, FULL_OUTPUT, or EXIT_CODE_AND_TEXT targets |
| Suppression rules | Add regex rules that silence matching Run/Debug, Console, or Terminal contexts before alerts are dispatched |
| Rule Testing Sandbox | Choose Source, Match Target, optional Exit Code, paste sample output, and click **Test Rules** |
| Exit-code rules | Map terminal exit codes to kinds, sound overrides, or suppression |
| Rule import/export | **Export Rules…** / **Import Rules…** for custom regex, suppression, and terminal exit-code rules only |
| Rule presets | Choose a bundled preset and click **Add Preset Rules** to append rules to the current tables |
| Diagnostics / Self-Test | Review applied settings/status and test error sound, success sound, or visual notification behavior |

The **Use actual sound file duration (play once)** checkbox is useful when a bundled or custom clip already has the exact length you want. The checkbox follows normal settings behavior: it is not persisted until **Apply** is clicked, and **Reset** discards unapplied checkbox changes. Preview follows the same mode where practical: play once when enabled, configured-duration looping when disabled.

### Diagnostics / Self-Test

Use **Diagnostics / Self-Test** in **Settings / Preferences → Tools → Error Sound Alert** to verify the plugin locally without causing a real build or test failure. Diagnostics are settings-only and are not shown in the Error Monitor tool window.

The summary reads existing applied state and status, including monitoring, snooze, visual notification settings, sound source/selected sound, global volume, alert duration, play-once mode, custom regex rule count, suppression rule count, terminal exit-code rule count, Alert History count, rule preset availability, rule import/export schema support, and terminal integration status.

Available self-tests:
- **Test error sound**
- **Test success sound**
- **Test visual notification**

Sound self-tests use the existing preview playback path and respect **Use actual sound file duration (play once)** where applicable. **Test visual notification** sends a real IntelliJ Platform balloon notification through the existing **Error Sound Alert** notification group, uses an active project fallback when Settings has no direct project context, and does not show a modal OK dialog on the normal success path. Notification placement is controlled by the IntelliJ Platform.

Diagnostics and self-tests do not mutate settings, write Alert History entries, write files, create persistent diagnostic logs, use network access, send telemetry, or touch terminal reflection logic.

### Visual Notifications

When visual notifications are enabled, accepted alerts show a balloon after the same dispatcher gates as sound playback. Suppressed alerts do not show notifications.

Available actions:
- **Open Settings**
- **Open Error Monitor**
- **Mute 1 hr**
- **Disable this kind** / **Disable success alerts**
- **Show alert details** when explanation data exists

Alert details are shown in a lightweight dialog with capped fields: source, kind, cause, exit code, command/config, rule id/pattern, match target, sound override, and a short summary when available. The dialog does not show full console output, persist extra details, write files, use network access, or send telemetry.

The **Disable this kind** action updates the underlying monitoring flag immediately. If the Error Monitor tool window is already open, it may need refresh/reopen to visually reflect the changed checkbox state.

### Suppression Rules

Use **Suppression Rules** in **Settings / Preferences → Tools → Error Sound Alert** to silence known harmless output before an alert is dispatched. Suppression wins over both custom regex classification and built-in classification.

Targets:
- **LINE_TEXT**: Run/Debug chunks and Console lines
- **FULL_OUTPUT**: Run/Debug final buffered output
- **EXIT_CODE_AND_TEXT**: Run/Debug final output plus exit code, and Terminal command context

Example: add a LINE_TEXT suppression rule for `Known harmless lint warning` to prevent a noisy linter message from playing a sound even if a custom regex or built-in pattern would classify it as a failure.

Suppression rules are saved only after **Apply**. **Reset** discards unapplied suppression-rule changes. Invalid regex text is preserved for editing and skipped safely at runtime. Suppressed matches do not call `AlertDispatcher`, play sound, show visual notifications, or enter Alert History. Suppression rules are local only: no network, telemetry, remote downloads, or script execution.

### Rule Presets

Use **Rule Presets** in **Settings / Preferences → Tools → Error Sound Alert** to add bundled rules for common stacks:
- Java / Spring Boot
- Gradle / Maven
- Node.js / npm / pnpm
- Python / pytest
- Docker / Kubernetes
- Frontend test runners (Jest / Vitest / Cypress / Playwright)

Presets append only Custom Regex Rules and conservative Terminal Exit-Code Rules. They do **not** modify sound settings, volume settings, success settings, project profiles/overrides, alert history, snooze state, or full profiles/settings bundles.

Choose a preset, review its description, then click **Add Preset Rules**. The confirmation summary shows how many custom regex and exit-code rules will be added and which duplicates will be skipped. Duplicate custom rule IDs are skipped, existing terminal exit codes are skipped, and user-created rules are preserved. Preset additions are written only to the current settings table models; click **Apply** to persist them, or **Reset** to discard unapplied preset additions. Presets are bundled locally: no network, telemetry, remote preset downloads, or script execution.

### Rule Import / Export

Use **Export Rules…** and **Import Rules…** in **Settings / Preferences → Tools → Error Sound Alert** to move rules between IDEs or share rule presets. The JSON bundle covers only:
- Custom Regex Rules
- Suppression Rules
- Terminal Exit-Code Rules

It does **not** include global sound settings, per-kind volume, success settings, project profiles/overrides, alert history, snooze state, or a full plugin settings export.

Export uses the current rule tables exactly as shown, including unsaved edits. Import validates the JSON, shows a confirmation summary, and replaces only the rule tables. Exports use schema version 2 with `customRules`, `suppressionRules`, and `exitCodeRules`; schema version 1 files remain import-compatible. Imported changes follow the normal settings workflow: click **Apply** to persist them, or **Reset** to discard imported-but-not-applied changes. Import/export uses local files only; there is no network or telemetry.

### Rule Testing Sandbox

Open **Settings / Preferences → Tools → Error Sound Alert → Rule Testing Sandbox** to test custom regex rules before applying them. Choose **Source**, **Match Target**, optionally set an **Exit Code**, paste sample output, and click **Test Rules**.

The sandbox shows whether a custom rule matched, which rule matched, the resulting `ErrorKind`, whether the built-in classifier would match if no custom rule did, regex validation errors, and a clear no-match message. It is a settings-side evaluation tool only; it does not trigger alerts or participate in runtime detection and dispatch.

### Error categories detected

| Category | Example triggers |
|---|---|
| **Configuration** | `Could not resolve placeholder`, `BeanCreationException`, `IllegalStateException` |
| **Compilation** | `Compilation failed`, `Cannot find symbol`, `error:` |
| **Test Failure** | `Tests failed`, `AssertionError`, `There were failing tests` |
| **Network** | `Connection refused`, `UnknownHostException`, `SocketTimeoutException` |
| **Exception** | `Exception`, `Caused by:`, `StackTrace` |
| **Generic** | Non-zero exit code or unclassified `error` / `failed` output |

---

## Build

**Prerequisites:** JDK 21+

```bash
# Clone the repository
git clone https://github.com/jkjitendra/error-sound.git
cd error-sound

# Build the plugin ZIP
./gradlew buildPlugin
```

Output: `build/distributions/error-sound-<version>.zip`

---

## How it works

1. The plugin registers process output listeners (`ExecutionListener`, console filters) and terminal command listeners.
2. As a process runs or a terminal command executes, its output is scanned in real time for known error patterns.
3. The project-aware resolver layers any selected workspace-scoped project profile overrides over global settings without mutating global or project state.
4. Suppression rules run first for the supported source/target context. A matching enabled suppression rule stops the alert before dispatch.
5. On process termination (or console line match, or terminal command completion), the accepted detection path creates an internal explanation object, builds a stable deduplication key, and calls **`AlertDispatcher.tryAlert`**.
6. `AlertDispatcher` checks snooze, **`AlertMonitoring`** (is this error category enabled?), and **`AlertEventGate`** (is this a duplicate within the cooldown window?).
7. If all gates pass, `AlertHistoryService` records an in-memory history entry, then `ErrorSoundPlayer` plays the configured sound asynchronously.
8. By default, if the clip is shorter than the alert duration, it loops/restarts until time expires. If **Use actual sound file duration (play once)** is enabled, the selected clip starts once and the configured alert duration is ignored for file-based playback.
9. Fallback chain: custom file → built-in WAV → generated 880 Hz tone → system beep.

Rule match explanations power Alert History cause/context details and the optional Show alert details notification action.

---

## Project Structure

```
src/main/kotlin/com/drostwades/errorsound/
├── AlertDispatcher.kt                # Single choke-point: routes all alerts through monitoring + gate + player
├── AlertEventGate.kt                 # Deduplication gate — per-source and global cooldowns
├── AlertHistoryService.kt            # In-memory accepted-alert history for the Error Monitor
├── AlertMatchExplanation.kt          # Internal explanation model for alert classifications
├── AlertMonitoring.kt                # Centralized rule gate for error filtering
├── AlertOnErrorExecutionListener.kt  # Process lifecycle listener
├── AlertOnTerminalCommandListener.kt # Terminal command lifecycle listener
├── AlertSettings.kt                  # Persistent settings state
├── BuiltInSounds.kt                  # Built-in sound registry
├── ClassificationExplanationFactory.kt # Explanation helper factory
├── CustomRuleEngine.kt               # Compiled custom regex rule matching
├── ErrorConsoleFilterProvider.kt     # Log analyzer for error spotting
├── ErrorKind.kt                      # Error enum + classifier
├── ErrorSoundConfigurable.kt         # Main settings UI panel
├── ErrorSoundDiagnosticsService.kt   # Settings-side diagnostics snapshot and self-test helpers
├── ErrorSoundPlayer.kt               # Audio playback engine
├── ErrorSoundToolWindowFactory.kt    # Error Monitor sidebar panel
├── ProjectAlertSettings.kt           # Workspace-scoped per-project profile override state
├── ProjectProfilePanel.kt            # Error Monitor project profile controls
├── ResolvedSettingsResolver.kt       # Effective global/project settings resolver
├── RuleImportExportBundle.kt         # Rules-only JSON export DTO
├── RuleImportExportResult.kt         # Rule import validation result
├── RuleImportExportService.kt        # Rules-only JSON import/export validation helper
├── RulePresetApplyResult.kt          # Rule preset duplicate/append summary
├── RulePresetBundle.kt               # Built-in rule preset data model
├── RulePresetService.kt              # Bundled rule preset definitions and append planner
├── RuleTestService.kt                # Settings-side rule sandbox evaluator
├── SuppressionRuleEngine.kt          # Compiled suppression regex rule matching
└── SnoozeState.kt                    # Transient mute state

src/main/resources/
├── META-INF/plugin.xml               # Plugin manifest
└── audios/                           # Bundled WAV files
```

---

## License

This project is licensed under the [MIT License](LICENSE).

---

## Author

**Drost Wades** — [github.com/jkjitendra](https://github.com/jkjitendra)
