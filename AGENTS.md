# AGENTS.md — Error Sound Alert Plugin

> Top-level agent operating instructions for the Error Sound Alert IntelliJ plugin.

## Repo Purpose

IntelliJ Platform plugin that plays an audio alert when a Run/Debug process, console output, or terminal command fails. Package: `com.drostwades.errorsound`. Published on JetBrains Marketplace.

## What This Plugin Does

1. Listens to Run/Debug process exits via `ExecutionListener`.
2. Scans console output for error patterns via `ConsoleFilterProvider`.
3. Monitors terminal command completions via reflection-based listener on Classic/Block and Reworked terminal internals (optional dependency on `org.jetbrains.plugins.terminal`).
4. Routes detected errors through `AlertDispatcher → AlertMonitoring → AlertEventGate → ErrorSoundPlayer`.
5. Plays a WAV sound (built-in or custom) with configurable volume and duration.

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

## Safe Editing Rules

1. **Do NOT** change `AlertDispatcher`, `AlertEventGate`, or `AlertMonitoring` without understanding the deduplication flow.
2. **Do NOT** add direct imports from `org.jetbrains.plugins.terminal` — terminal integration uses reflection only.
3. **Do NOT** lower `sinceBuild` below 243 — Kotlin 2.x stdlib requires 2024.3+.
4. **Do NOT** set `untilBuild` without understanding forward-compatibility impact.
5. Keep `kotlin.stdlib.default.dependency=false` in `gradle.properties` — the platform bundles its own stdlib.
6. Always run `./gradlew buildPlugin` after changes.
7. Keep README, Marketplace description (`build.gradle.kts`), and plugin.xml description aligned.

## Sensitive Files / Areas

| File | Risk |
|---|---|
| `AlertOnTerminalCommandListener.kt` | **HIGH** — 591 lines of reflection-heavy code. Breaks with internal terminal API changes. |
| `AlertEventGate.kt` | **MEDIUM** — Deduplication gate. Wrong tuning causes duplicate or swallowed alerts. |
| `plugin.xml` / `terminal-features.xml` | **MEDIUM** — Extension point registrations. Misregistration = silent failure. |
| `build.gradle.kts` | **MEDIUM** — Platform plugin config, sinceBuild/untilBuild, signing, verification. |
| `ErrorSoundPlayer.kt` | **LOW-MEDIUM** — Audio thread management. Clip leaks if not closed properly. |

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
*Last updated from code scan: 2026-03-18*
