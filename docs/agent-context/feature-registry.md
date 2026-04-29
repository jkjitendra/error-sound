# Feature Registry - Error Sound Alert

Current user-facing feature inventory for the shipped plugin state.

---

## Run/Debug Failure Alerts

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.0.0 |
| Relevant classes/files | `AlertOnErrorExecutionListener.kt`, `ErrorKind.kt`, `AlertDispatcher.kt`, `plugin.xml` |

Plays an alert when a Run/Debug process finishes with an error or classified failure output. Output is captured while the process runs, then final classification is resolved when the process terminates. All alerts route through `AlertDispatcher`, so snooze, monitoring flags, deduplication, playback, and optional notification behavior apply consistently.

**How to enable/use:** Install the plugin and keep monitoring enabled in Settings / Preferences -> Tools -> Error Sound Alert or the Error Monitor tool window.

**Example usage:** Run a Gradle task or application configuration that exits non-zero; the configured error sound plays after the process ends.

**Notes/limitations:** Run/Debug output buffering is capped at 1M characters. Success alerts and duration thresholds are Run/Debug-only features.

---

## Console Output Detection

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.0.3 |
| Relevant classes/files | `ErrorConsoleFilterProvider.kt`, `ErrorKind.kt`, `CustomRuleEngine.kt`, `plugin.xml` |

Scans console lines from IntelliJ `ConsoleView` instances and alerts when error-like patterns appear. Custom LINE_TEXT rules run before the built-in console pattern. The filter returns no text decorations; it only performs alert side effects through the shared dispatch path.

**How to enable/use:** Keep monitoring enabled and print matching output in a Run, Debug, Test, Gradle, or other console view.

**Example usage:** A test runner prints `AssertionError`; the console filter classifies it as TEST_FAILURE and dispatches an alert.

**Notes/limitations:** FULL_OUTPUT and EXIT_CODE_AND_TEXT custom rules do not run in the console filter path. Broad terms such as `error` can still produce false positives.

---

## Terminal Command Failure Alerts

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.0.3 |
| Relevant classes/files | `AlertOnTerminalCommandListener.kt`, `ErrorKind.kt`, `AlertDispatcher.kt`, `terminal-features.xml` |

Monitors built-in terminal command completion and alerts when a command exits with a non-zero code. The listener attaches through reflection to Classic/Block and Reworked terminal internals. Terminal alerts still route through project resolution, monitoring gates, deduplication, and audio playback.

**How to enable/use:** Use the IDE built-in Terminal with the terminal plugin installed; run a command that exits non-zero.

**Example usage:** Run `false` or an invalid command in Terminal; the command completion is classified as GENERIC unless an exit-code rule changes the result.

**Notes/limitations:** Terminal output is not parsed for built-in classification; only command and exit code are used. The integration is reflection-heavy and may need updates when terminal internals change.

---

## Error Monitor Tool Window

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.0.4 |
| Relevant classes/files | `ErrorSoundToolWindowFactory.kt`, `AlertMonitoring.kt`, `SnoozeState.kt`, `plugin.xml` |

Adds an Error Monitor sidebar for quick operational control. Users can toggle global monitoring, enable or disable individual error kinds, apply presets, and open the full settings page. The panel also exposes project enabled override and snooze controls.

**How to enable/use:** Open View -> Tool Windows -> Error Monitor.

**Example usage:** Choose the Build Only preset to monitor CONFIGURATION, COMPILATION, and TEST_FAILURE while suppressing runtime categories.

**Notes/limitations:** Most controls mutate global application settings directly. Project profile controls currently affect only the master enabled flag.

---

## Success Sounds

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.2 |
| Relevant classes/files | `AlertOnErrorExecutionListener.kt`, `AlertSettings.kt`, `ErrorSoundPlayer.kt`, `ErrorSoundToolWindowFactory.kt` |

Optionally plays a distinct alert when a Run/Debug process completes successfully. Success is produced only when no error kind is detected and the process exit code is zero. Success monitoring and success sound selection are disabled by default.

**How to enable/use:** Enable success monitoring in Error Monitor, then configure the success sound in Settings / Preferences -> Tools -> Error Sound Alert.

**Example usage:** Run a long build configuration that exits `0`; if success monitoring is enabled, the configured SUCCESS sound plays.

**Notes/limitations:** Success sounds do not currently trigger from console-only detection or terminal command completion.

---

## Minimum Process Duration Threshold

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.3 |
| Relevant classes/files | `AlertOnErrorExecutionListener.kt`, `AlertSettings.kt`, `ErrorSoundConfigurable.kt` |

Suppresses Run/Debug alerts for processes that finish faster than a configured number of seconds. This reduces noise from very short commands or expected quick failures. A value of `0` disables the threshold.

**How to enable/use:** Set the minimum process duration in Settings / Preferences -> Tools -> Error Sound Alert.

**Example usage:** Set the threshold to `10` seconds so only longer-running Run/Debug processes can produce alerts.

**Notes/limitations:** Applies only to the Run/Debug execution listener path, not console filter or terminal alerts.

---

## Snooze / Mute

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.3 |
| Relevant classes/files | `SnoozeState.kt`, `AlertDispatcher.kt`, `ErrorSoundToolWindowFactory.kt` |

Temporarily silences all alerts before monitoring, deduplication, or playback gates run. Snooze state is transient and resets on IDE restart. The tool window updates through the application message bus when snooze starts or resumes.

**How to enable/use:** Use the Error Monitor tool window actions to mute for 15 minutes, mute for 1 hour, or resume.

**Example usage:** Mute alerts for 1 hour during a meeting, then resume alerts afterward from the same sidebar.

**Notes/limitations:** Snooze is not persisted and intentionally affects all alert kinds.

---

## Visual Notifications

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.4 |
| Relevant classes/files | `AlertDispatcher.kt`, `AlertSettings.kt`, `ErrorSoundConfigurable.kt`, `plugin.xml` |

Shows optional IDE balloon notifications alongside sound alerts. Notifications can be enabled separately for error and success alerts. They are emitted after the same dispatch gates as audio, so normal snooze, monitoring, and deduplication behavior still applies.

**How to enable/use:** Enable visual notifications in Settings / Preferences -> Tools -> Error Sound Alert.

**Example usage:** Turn on error notifications to see a balloon with the classified kind when a process fails.

**Notes/limitations:** Notifications are off by default. Current actions are limited to opening settings and muting for 1 hour.

---

## Custom Regex Rules

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.5 |
| Relevant classes/files | `CustomRuleEngine.kt`, `AlertSettings.kt`, `ErrorSoundConfigurable.kt`, `AlertOnErrorExecutionListener.kt`, `ErrorConsoleFilterProvider.kt`, `AlertOnTerminalCommandListener.kt` |

Lets users define regex patterns mapped to supported error kinds. Rules are evaluated before built-in classification, and the first matching compiled rule for the relevant target wins. Match targets control where the rule runs: LINE_TEXT, FULL_OUTPUT, or EXIT_CODE_AND_TEXT.

**How to enable/use:** Add rules in Settings / Preferences -> Tools -> Error Sound Alert under Custom Regex Rules.

**Example usage:** Add a LINE_TEXT rule for `lint failed` mapped to COMPILATION so linter output gets a compilation alert.

**Notes/limitations:** Invalid regex patterns are highlighted in settings and skipped at runtime. NONE and SUCCESS are not valid custom rule result kinds.

---

## Rule Testing Sandbox

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | TBD (next release after 1.1.8) |
| Relevant classes/files | `RuleTestService.kt`, `ErrorSoundConfigurable.kt`, `CustomRuleEngine.kt`, `ErrorKind.kt` |

Lets users paste sample output and evaluate custom regex behavior from the settings UI. The sandbox reports whether a custom rule matched, which rule matched, the resulting `ErrorKind`, built-in classifier fallback, regex validation errors, and no-match messages. It uses the current in-memory custom rule table, so unsaved edits can be tested before Apply. This is an explanation tool only and never enters runtime detection or dispatch.

**How to enable/use:** Open Settings / Preferences -> Tools -> Error Sound Alert -> Rule Testing Sandbox. Choose Source, Match Target, optional Exit Code, paste sample output, then click Test Rules.

**Example usage:** Add a LINE_TEXT rule for `lint failed`, paste a failing linter line, choose Source = Console and Match Target = LINE_TEXT, then click Test Rules to confirm the matched rule and resulting COMPILATION kind.

**Notes/limitations:** It does not play sounds, show notifications, call `AlertDispatcher`, or persist any sandbox input. Source/target applicability mirrors runtime behavior: Console evaluates LINE_TEXT only, Terminal evaluates EXIT_CODE_AND_TEXT only, and Run/Debug can evaluate all custom rule targets.

---

## Terminal Exit-Code Rules

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.6 |
| Relevant classes/files | `AlertSettings.kt`, `ErrorKind.kt`, `ErrorSoundConfigurable.kt`, `AlertOnTerminalCommandListener.kt`, `ErrorSoundPlayer.kt` |

Maps specific terminal exit codes to an error kind, an optional built-in sound override, or complete suppression. These rules run after custom EXIT_CODE_AND_TEXT regex rules and before the built-in terminal fallback. Default rules include suppression for exit code 130.

**How to enable/use:** Configure Exit-Code Rules in Settings / Preferences -> Tools -> Error Sound Alert.

**Example usage:** Map exit code `137` to GENERIC with a louder built-in sound to distinguish killed processes from normal failures.

**Notes/limitations:** Applies to terminal command completion only. Sound overrides change the selected built-in sound but do not bypass kind monitoring or per-kind sound enablement.

---

## Project-Level Enabled Override

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.7 |
| Relevant classes/files | `ProjectAlertSettings.kt`, `ResolvedSettingsResolver.kt`, `ErrorSoundToolWindowFactory.kt`, `plugin.xml` |

Allows one project to override the global master monitoring enabled state. The resolver applies the project override at dispatch time for Run/Debug, console, and terminal paths. This lets one workspace stay muted or active without changing the global setting for every IDE project.

**How to enable/use:** Open Error Monitor, enable "Use project override for monitoring enabled", then choose the project enabled state.

**Example usage:** Keep monitoring globally enabled, but disable alerts for a noisy scratch project.

**Notes/limitations:** Only the master `enabled` flag is project-scoped. Sounds, custom rules, exit-code rules, per-kind toggles, and volumes remain global.

---

## Per-Kind Volume

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.8 |
| Relevant classes/files | `AlertSettings.kt`, `ErrorSoundConfigurable.kt`, `ErrorSoundPlayer.kt` |

Adds optional volume overrides per error/success kind on top of the global volume. If a per-kind override is not set, playback falls back to the global volume. The policy applies to bundled sounds, custom file playback, generated-tone fallback, and terminal exit-code sound overrides.

**How to enable/use:** In Settings / Preferences -> Tools -> Error Sound Alert, enable Custom volume for the desired kind and set its slider.

**Example usage:** Set TEST_FAILURE to 100% and NETWORK to 40% while keeping the global volume at 80%.

**Notes/limitations:** Per-kind volume is global application state, not project-specific.

---

## Built-In And Custom Sound Configuration

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.0.0 |
| Relevant classes/files | `BuiltInSounds.kt`, `AlertSettings.kt`, `ErrorSoundConfigurable.kt`, `ErrorSoundPlayer.kt` |

Provides bundled WAV alerts and optional custom local audio files. Users can use one global built-in sound or map separate sounds to each error/success kind. Preview controls play sounds before applying settings.

**How to enable/use:** Configure Sound source, global/per-kind sound mode, and custom file path in Settings / Preferences -> Tools -> Error Sound Alert.

**Example usage:** Use one global sound for all alerts, or assign separate sounds to COMPILATION and TEST_FAILURE.

**Notes/limitations:** Custom file playback supports JVM audio formats used by the plugin path: WAV, AIFF, and AU.

---

## Alert Deduplication

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.1 |
| Relevant classes/files | `AlertEventGate.kt`, `AlertDispatcher.kt`, `ErrorSoundPlayer.kt` |

Prevents rapid duplicate alerts when multiple detection paths or repeated output lines report the same logical failure. The main gate uses per-key and global cooldowns, and audio playback has a small last-resort debounce. This keeps alert noise bounded without requiring user tuning.

**How to enable/use:** Enabled automatically for every alert path.

**Example usage:** A failed build may print many error lines, but repeated matching events within the cooldown window do not all play sounds.

**Notes/limitations:** Cooldown values are fixed in code and not currently configurable.

---
*Last updated from code scan: 2026-04-29*
