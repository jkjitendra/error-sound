# Feature Registry - Error Sound Alert

Current user-facing feature inventory for the shipped plugin state.

Internal runtime plumbing that is not directly user-visible is intentionally omitted from the feature list. For example, Rule Match Explanation is documented in `architecture.md`, `code-map.md`, and `recent-changes.md` rather than listed here as an available user-facing feature.

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

Adds an Error Monitor sidebar for quick operational control. Users can toggle global monitoring, enable or disable individual error kinds, apply presets, manage opt-in project profile overrides, use snooze controls, and open the full settings page. The Project Profile, Error Types, and Success sections are collapsible to keep the right-side tool window compact.

**How to enable/use:** Open View -> Tool Windows -> Error Monitor.

**Example usage:** Choose the Build Only preset to monitor CONFIGURATION, COMPILATION, and TEST_FAILURE while suppressing runtime categories.

**Notes/limitations:** Most monitoring controls mutate global application settings directly. Project profile controls mutate workspace-scoped project state and only selected override groups affect the resolved settings for that project.

---

## Alert History

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.11 |
| Relevant classes/files | `AlertHistoryService.kt`, `AlertDispatcher.kt`, `ErrorSoundToolWindowFactory.kt`, `AlertMatchExplanation.kt` |

Shows recent accepted alerts in the Error Monitor so users can see what fired and why after the sound plays. Entries are derived from the runtime explanation object and include source, final kind, cause, and compact context. The history is in-memory only, newest first, bounded to the latest 100 entries, and clearable from the panel. It is intended for local troubleshooting and does not persist console output or send telemetry.

**How to enable/use:** Open View -> Tool Windows -> Error Monitor and use the Alert History section. Alerts appear after they pass snooze, monitoring, and deduplication gates.

**Example usage:** A terminal command exits `137` and matches an exit-code rule; Alert History shows Terminal, GENERIC, terminal exit-code rule, the exit code, command context, and any rule/sound override details available.

**Notes/limitations:** Suppressed attempts are not recorded in this release, including snoozed alerts, disabled kinds, and deduplicated events. The table is read-only and does not yet include actions such as opening the related console or rerunning a process.

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

Shows optional IDE balloon notifications alongside sound alerts. Notifications can be enabled separately for error and success alerts. They are emitted after the same dispatch gates as audio, so normal snooze, monitoring, deduplication, history, and suppression behavior still applies.

**How to enable/use:** Enable visual notifications in Settings / Preferences -> Tools -> Error Sound Alert.

**Example usage:** Turn on error notifications to see a balloon with the classified kind when a process fails.

**Notes/limitations:** Notifications are off by default. Suppressed alerts do not show notifications.

---

## Actionable Notification Actions

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.16 |
| Relevant classes/files | `AlertDispatcher.kt`, `AlertMatchExplanation.kt`, `AlertMonitoring.kt`, `ErrorSoundToolWindowFactory.kt`, `SnoozeState.kt` |

Adds useful actions to visual alert notifications after an alert has already passed the existing dispatcher gates. Available actions are Open Settings, Open Error Monitor, Mute 1 hr, Disable this kind / Disable success alerts, and Show alert details when explanation data exists. The feature helps users inspect and control alerts without changing detection, playback, history recording, or suppression behavior.

**How to enable/use:** Enable visual notifications in Settings / Preferences -> Tools -> Error Sound Alert. Trigger an alert, then use the actions shown on the notification balloon.

**Example usage:** A custom regex rule triggers a COMPILATION alert. Click **Show alert details** to see source, kind, cause, exit code if present, command/config if present, rule id/pattern, match target, sound override if present, and a short summary.

**Notes/limitations:** Alert details are capped and do not show full console output. No extra alert-detail persistence, telemetry, network calls, file writes, terminal reflection changes, sound playback changes, or Alert History behavior changes are introduced. **Disable this kind** updates the underlying monitoring setting immediately; if the Error Monitor tool window is already open, it may need refresh/reopen to visually reflect the changed checkbox state.

---

## Diagnostics / Self-Test

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.17 |
| Relevant classes/files | `ErrorSoundDiagnosticsService.kt`, `ErrorSoundConfigurable.kt`, `ErrorSoundPlayer.kt`, `plugin.xml` |

Adds a Settings-only diagnostics surface for local verification. It shows applied monitoring, snooze, notification, sound, play-once, rule count, Alert History, rule preset, import/export schema, and terminal integration status. It also provides safe self-tests for GENERIC error sound, SUCCESS sound, and a real IntelliJ Platform visual notification.

**How to enable/use:** Open Settings / Preferences -> Tools -> Error Sound Alert -> Diagnostics / Self-Test. Click Refresh Diagnostics, Test error sound, Test success sound, or Test visual notification.

**Example usage:** After installing the plugin, open Diagnostics / Self-Test and click **Test visual notification** to confirm the IDE shows a balloon from the existing **Error Sound Alert** notification group without needing to trigger a real build failure.

**Notes/limitations:** Diagnostics / Self-Test is not part of the Error Monitor tool window. Sound self-tests use preview playback and respect Play Once Sound Duration where applicable. The visual notification test uses `NotificationGroupManager`, `NotificationType.INFORMATION`, active project fallback, and EDT delivery; notification placement is controlled by the IntelliJ Platform. Self-tests do not call `AlertDispatcher`, mutate settings, write Alert History entries, write files, create persistent diagnostic logs, use network calls, send telemetry, or change terminal reflection logic.

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

## Ignore / Suppression Rules

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.15 |
| Relevant classes/files | `SuppressionRuleEngine.kt`, `AlertSettings.kt`, `ErrorSoundConfigurable.kt`, `AlertOnErrorExecutionListener.kt`, `ErrorConsoleFilterProvider.kt`, `AlertOnTerminalCommandListener.kt`, `RuleImportExportService.kt` |

Lets users silence known noisy false positives before an alert is dispatched. Suppression rules are local regex rules with LINE_TEXT, FULL_OUTPUT, or EXIT_CODE_AND_TEXT targets. A matching enabled suppression rule wins over custom regex classification and built-in classification, and returns before `AlertDispatcher.tryAlert()`.

**How to enable/use:** Open Settings / Preferences -> Tools -> Error Sound Alert, add a row under Suppression Rules, choose the target, enter the pattern and optional description, then click Apply.

**Example usage:** Add a LINE_TEXT suppression rule for `Known harmless lint warning` so a noisy linter message does not play sound even if another rule or broad built-in pattern would classify it as COMPILATION.

**Notes/limitations:** Run/Debug supports LINE_TEXT, FULL_OUTPUT, and EXIT_CODE_AND_TEXT where applicable; Console supports LINE_TEXT only; Terminal supports EXIT_CODE_AND_TEXT only. Suppressed matches do not play sound, show visual notifications, or enter Alert History. Invalid regex text is preserved for editing and skipped safely at runtime. Suppression rules do not change sound, volume, play-once duration, project profiles, history persistence, telemetry, network behavior, or remote rule downloads.

---

## Rule Testing Sandbox

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.9 |
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

## Rule Import / Export

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.12 |
| Relevant classes/files | `RuleImportExportBundle.kt`, `RuleImportExportResult.kt`, `RuleImportExportService.kt`, `ErrorSoundConfigurable.kt` |

Lets users export and import rule presets as local JSON. The schema version 2 bundle covers Custom Regex Rules, Suppression Rules, and Terminal Exit-Code Rules, preserving ordering and ids when present. Schema version 1 files remain import-compatible for older exports without suppression rules. It is not a full settings export and deliberately excludes global sound settings, per-kind volume, success settings, project profiles/overrides, alert history, snooze state, and any runtime data.

**How to enable/use:** Open Settings / Preferences -> Tools -> Error Sound Alert and use **Export Rules…** or **Import Rules…** near the rule sections.

**Example usage:** Configure custom regex rules for a team linter, suppression rules for harmless noisy messages, and terminal exit-code rules for common shell failures, export them to JSON, then import that file in another IDE. The imported table changes become persistent only after Apply.

**Notes/limitations:** Import validates JSON strictly, shows a confirmation summary, and replaces only the rule table models. Reset discards imported-but-not-applied changes. Import/export uses local files only; no network, telemetry, or execution of imported content is involved.

---

## Rule Presets

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.13 |
| Relevant classes/files | `RulePresetBundle.kt`, `RulePresetApplyResult.kt`, `RulePresetService.kt`, `ErrorSoundConfigurable.kt`, `AlertSettings.kt` |

Lets users add bundled rule sets for common stacks without replacing existing rules. Available preset bundles are Java / Spring Boot, Gradle / Maven, Node.js / npm / pnpm, Python / pytest, Docker / Kubernetes, and Frontend test runners (Jest / Vitest / Cypress / Playwright). Presets append only Custom Regex Rules and conservative Terminal Exit-Code Rules to the current settings table models.

**How to enable/use:** Open Settings / Preferences -> Tools -> Error Sound Alert. Choose a Rule Presets entry, review its description, click **Add Preset Rules**, then confirm the summary.

**Example usage:** Choose Docker / Kubernetes and click **Add Preset Rules** to append rules for `Error response from daemon`, `ImagePullBackOff`, `CrashLoopBackOff`, `OOMKilled`, and common terminal exit codes not already present.

**Notes/limitations:** Preset additions are not persisted until Apply; Reset discards unapplied additions. Duplicate preset custom rule ids and existing terminal exit codes are skipped, while user-created rules are preserved. Presets do not modify sound settings, volume settings, success settings, project profiles/overrides, alert history, snooze state, or full profiles/settings bundles. Presets are bundled locally: no network, telemetry, remote preset downloads, or script execution.

---

## Full Per-Project Profiles

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.18 expanded full-profile behavior; enabled-only override introduced in 1.1.7 |
| Relevant classes/files | `ProjectAlertSettings.kt`, `ProjectProfilePanel.kt`, `ResolvedSettingsResolver.kt`, `ErrorSoundToolWindowFactory.kt`, `ErrorSoundDiagnosticsService.kt`, `plugin.xml` |

Allows one project workspace to opt into selected profile overrides while preserving global defaults for everything else. Supported override groups include master enabled, per-kind monitoring, built-in/global and per-kind sound behavior, success sound, global/per-kind volume, alert duration, play-once mode, visual notifications, and minimum process duration. `ResolvedSettingsResolver` applies selected overrides at dispatch time and returns an effective settings copy without mutating global or project state.

**How to enable/use:** Open View -> Tool Windows -> Error Monitor, expand **Project Profile**, enable **Use project profile overrides**, then enable the specific override groups to replace global values for that project. Use **Copy current global settings** to seed the profile or **Reset project overrides** to return the project to inheritance.

**Example usage:** Keep monitoring globally enabled, but open a noisy scratch project, enable project profile overrides, disable GENERIC monitoring, lower volume, and turn off visual notifications only for that workspace.

**Notes/limitations:** Project profile storage is workspace-scoped via `WORKSPACE_FILE`; it is not a repo-shared profile file. Older enabled-only workspace overrides are preserved and migrated into the new master profile behavior. Custom regex rules, suppression rules, terminal exit-code rules, rule presets, rule import/export, Alert History, and terminal integration remain global/application-level in Phase 9.

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

## Play Once Sound Duration

| Field | Value |
|---|---|
| Status | Available |
| Version introduced | 1.1.14 |
| Relevant classes/files | `AlertSettings.kt`, `ErrorSoundConfigurable.kt`, `ErrorSoundPlayer.kt`, `build.gradle.kts` |

Adds **Use actual sound file duration (play once)** for users who want a selected built-in or custom clip to play exactly once instead of looping/restarting until the configured alert duration expires. The option is disabled by default, so existing configured-duration playback remains unchanged. When enabled, file-based playback ignores `alertDurationSeconds`; the selected clip starts once and playback waits for the actual clip length or stop/close event.

**How to enable/use:** Open Settings / Preferences -> Tools -> Error Sound Alert, enable **Use actual sound file duration (play once)**, then click Apply. The duration slider/value label are disabled while the option is selected.

**Example usage:** Use a custom WAV that already includes a full notification phrase or longer effect, enable play-once mode, and preview it from settings; the clip plays once instead of being restarted to fill the configured duration.

**Notes/limitations:** Preview follows play-once mode where practical. The checkbox change is not persisted until Apply; Reset discards unapplied changes. This feature was ported cleanly from the idea in external PR #32; the PR branch was not merged directly. The seven proposed new sounds from PR #32 were not shipped in 1.1.14 because the files were not present and licensing was not confirmed.

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
*Last updated from code scan: 2026-05-17*
