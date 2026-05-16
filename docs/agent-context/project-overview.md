# Project Overview — Error Sound Alert

## What the Plugin Does

Error Sound Alert is an IntelliJ Platform plugin that plays an audio alert when an error is detected during development. It monitors three independent sources and classifies errors into six categories.

## Detection Paths

1. **Run/Debug process exit** — `AlertOnErrorExecutionListener` via `ExecutionListener` extension point. Captures process output in real time and evaluates exit code on termination.
2. **Console output scanning** — `ErrorConsoleFilterProvider` via `consoleFilterProvider` extension point. Matches error patterns per console line across all `ConsoleView` instances (Run, Debug, Test, Gradle, Terminal).
3. **Terminal command completion** — `AlertOnTerminalCommandListener` via `backgroundPostStartupActivity` extension point (in `terminal-features.xml`). Uses reflection to hook into both Classic/Block and Reworked 2025 terminal engines.

## Supported IDE Baseline

- All IntelliJ-based IDEs (IDEA, PyCharm, WebStorm, GoLand, Rider, CLion, DataGrip, etc.)
- Minimum: 2024.3 (build 243)
- Maximum: unbounded (open `untilBuild`)

## Main Feature Set

| Feature | Description |
|---|---|
| Error classification | Configuration, Compilation, Test Failure, Network, Exception, Generic |
| Success sounds | Optional alert on successful process completion (exit code 0, off by default) |
| Sound options | 7 built-in WAV files + custom file (WAV/AIFF/AU) |
| Sound modes | Global (one sound for all) or per-error-type mapping |
| Volume / Duration | Global 0–100% volume, optional per-kind volume overrides, 1–10 second alert duration with clip looping by default, plus optional play-once mode using the actual sound file duration |
| Instant preview | Hear sounds from the settings panel before applying |
| Error Monitor panel | Sidebar tool window for quick-toggling error types with presets (All, Build Only, Runtime Only) |
| Alert History | Error Monitor read-only table of recent accepted alerts, newest first, in-memory only |
| Terminal support | Monitors both Classic/Block and Reworked terminal engines via reflection |
| Custom regex rules | User-defined LINE_TEXT, FULL_OUTPUT, and EXIT_CODE_AND_TEXT regex rules evaluated before built-in classification |
| Suppression rules | Local regex rules that silence known noisy false positives before alert dispatch |
| Rule Testing Sandbox | Settings-side tool for pasting sample output and explaining custom rule and built-in classifier results |
| Terminal exit-code rules | Map terminal exit codes to error kinds, optional built-in sound overrides, or suppression |
| Rule import/export | Local JSON import/export for custom regex, suppression, and terminal exit-code rules only |
| Rule presets | Bundled local rule bundles for Java/Spring Boot, Gradle/Maven, Node/npm/pnpm, Python/pytest, Docker/Kubernetes, and frontend test runners (Jest/Vitest/Cypress/Playwright) |
| Play Once Sound Duration | Optional `Use actual sound file duration (play once)` mode for built-in/custom file playback; disabled by default |
| Visual notifications | Optional balloon notifications for error and success alerts, with actions to open settings, open Error Monitor, mute, disable the current kind, and show capped alert details |
| Snooze / mute | Temporarily silence all alerts from the Error Monitor sidebar |
| Project-level profile | Per-project override for the master `enabled` flag; all other settings remain global |
| Deduplication | `AlertEventGate` prevents overlapping alerts with per-key (4s) and global (2s) cooldowns |

## Internal Runtime Plumbing

| Capability | Description |
|---|---|
| Rule match explanation | Detection paths create `AlertMatchExplanation` objects near classification time and pass them through `AlertDispatcher` for diagnostics, Alert History context, and notification details |
| Accepted-alert history | `AlertHistoryService` stores accepted alert events after dispatcher gates pass and refreshes the Error Monitor through the application message bus |

## Current Known Limitations

- Terminal listener relies on reflection into private/internal terminal plugin APIs — may break with future IDE updates.
- `ErrorClassifier.detectTerminal()` only uses exit code (no output analysis for terminal commands).
- Success sounds only trigger from Run/Debug processes — not from terminal or console filter paths.
- Project-level profiles currently override only the master `enabled` flag; sounds, rules, per-kind toggles, and volumes remain application-level.
- Notification alert details are capped and do not expose full console output. They do not add persistence beyond existing Alert History behavior.
- Rule import/export is rules-only. It does not include global sound settings, per-kind volume, success settings, project overrides, alert history, snooze state, or a full plugin settings bundle.
- Rule presets append only Custom Regex Rules and conservative Terminal Exit-Code Rules. They do not modify sound settings, volume settings, success settings, project overrides, alert history, snooze state, or full profiles/settings bundles.
- Play Once Sound Duration affects only file-based playback duration behavior. It does not alter sound selection, volume, rules, history, notifications, project profiles, or any terminal integration behavior. The seven extra sounds proposed in external PR #32 were not shipped in 1.1.14 because files and licensing were not confirmed.
- Suppression rules affect only pre-dispatch alert eligibility. They do not change sounds, volume, play-once duration, project profiles, alert history persistence, telemetry, network behavior, or remote rule downloads.
- Alert history is in-memory only, bounded to 100 entries, and records only alerts accepted by snooze, monitoring, and deduplication gates. Snoozed, disabled, duplicate, or otherwise suppressed attempts are not recorded.
- Console filter can produce false positives for lines containing the word "error" or "exception" in benign contexts.

## User-Facing Behavior Summary

When enabled, the plugin runs silently in the background. The moment a process fails, an error pattern appears in console output, or a terminal command exits with a non-zero code, a short audio alert plays unless a suppression rule matches first. Users configure sounds, volume, duration behavior, custom rules, suppression rules, rule presets, terminal exit-code rules, rules-only import/export, and notifications via **Settings → Tools → Error Sound Alert**. The **Error Monitor** sidebar controls global monitoring, per-kind monitoring toggles, snooze, presets, the per-project enabled override, and a clearable in-memory Alert History table for recent accepted alerts.

Actionable visual notifications appear only after the existing dispatcher gates accept an alert. They keep **Open Settings** and **Mute 1 hr**, and add **Open Error Monitor**, **Disable this kind** / **Disable success alerts**, and **Show alert details** when explanation data exists. The actions do not change dispatcher gate order, sound playback, Alert History recording, suppression behavior, terminal reflection logic, telemetry, network behavior, or file writes. Disabling a kind updates the existing monitoring flag immediately; an already-open Error Monitor may need refresh/reopen to visually reflect the checkbox change.

Suppression rules in **Settings → Tools → Error Sound Alert** are local regex rules for known harmless output. They support LINE_TEXT, FULL_OUTPUT, and EXIT_CODE_AND_TEXT targets. Run/Debug supports LINE_TEXT chunks plus FULL_OUTPUT and EXIT_CODE_AND_TEXT final checks; Console supports LINE_TEXT only; Terminal supports EXIT_CODE_AND_TEXT only. Suppression wins over custom regex and built-in classification. A suppressed match does not call `AlertDispatcher`, play sound, show a visual notification, or enter Alert History. Suppression-rule edits are saved only after Apply; Reset discards unapplied changes.

Play Once Sound Duration in **Settings → Tools → Error Sound Alert** is disabled by default, so existing configured-duration looping remains the default behavior. When **Use actual sound file duration (play once)** is enabled, the selected built-in or custom file-based sound starts once and the configured alert duration is ignored. The settings UI disables the duration slider/value label while the option is selected. Preview follows the same mode where practical. The checkbox change is not saved until Apply is clicked; Reset discards unapplied changes.

Rule presets in **Settings → Tools → Error Sound Alert** are bundled locally. Users choose a preset bundle, review its description, click **Add Preset Rules**, confirm the summary, and the preset appends rules to the existing Custom Regex Rules and Terminal Exit-Code Rules table models. Duplicate preset custom rule IDs and existing terminal exit codes are skipped, while user-created rules are preserved. Preset additions are not saved until Apply is clicked; Reset discards unapplied preset additions. Presets use no network, telemetry, remote downloads, or script execution.

Rule import/export in **Settings → Tools → Error Sound Alert** uses local JSON files only. Export reads the current Custom Regex Rules, Suppression Rules, and Terminal Exit-Code Rules table models, including unsaved edits. Export writes schema version 2; schema version 1 remains import-compatible for older files without suppression rules. Import validates JSON strictly, shows a confirmation summary, and replaces only those rule table models. Imported changes are not saved until Apply is clicked; Reset discards imported-but-not-applied changes.

The Rule Testing Sandbox in **Settings → Tools → Error Sound Alert** is an explanation tool only. Users choose Source, Match Target, optional Exit Code, paste sample output, and click **Test Rules** to see whether a custom rule would match, which `ErrorKind` would result, and whether built-in classification would match if no custom rule did. It does not participate in runtime detection, dispatch, monitoring gates, deduplication, or playback.

---
*Last updated from code scan: 2026-05-16*
