---
name: repo-context
description: How to scan this repo, update context files, and avoid breaking changes
---

# Repo Context Skill

Instructions for agents working in the Error Sound Alert IntelliJ plugin repository.

## First Steps for Any New Agent

1. **Read `AGENTS.md`** at the repo root — contains safe editing rules, sensitive files, and verification commands.
2. **Read `docs/agent-context/project-overview.md`** — understand what the plugin does.
3. **Read `docs/agent-context/architecture.md`** — understand the detection → dispatch → playback flow.
4. **Read `docs/agent-context/code-map.md`** — class-by-class reference.

## How to Scan the Repo

1. Start with `AGENTS.md` (root)
2. Read `build.gradle.kts` for versions and compatibility
3. Read `src/main/resources/META-INF/plugin.xml` for extension points
4. Read `src/main/resources/META-INF/terminal-features.xml` for terminal integration
5. Scan Kotlin sources in `src/main/kotlin/com/drostwades/errorsound/`
6. Check `docs/agent-context/` for existing documentation

## Key Files to Read First

| File | Why |
|---|---|
| `AGENTS.md` | Safety rules, baseline, verification commands |
| `AlertDispatcher.kt` | Central routing — all detection paths converge here |
| `AlertEventGate.kt` | Deduplication logic — must understand before changing alert behavior |
| `AlertOnTerminalCommandListener.kt` | Most complex/fragile file — 591 lines of reflection |
| `build.gradle.kts` | Build config, versions, sinceBuild/untilBuild |
| `plugin.xml` | Extension point registrations |

## How to Update Context Files

Follow `docs/agent-context/maintenance-rules.md` for the full update matrix. Quick summary:

- New class → update `code-map.md`
- Architecture change → update `architecture.md`
- Build config change → update `build-and-compatibility.md` + `AGENTS.md` baseline table
- Terminal change → update `terminal-integration.md`
- Any significant change → update `recent-changes.md`
- Update the `*Last updated from code scan: YYYY-MM-DD*` footer in each modified file

## Avoiding Behavior Changes

1. Read `AGENTS.md` "Safe Editing Rules" before making any changes
2. Do NOT modify `AlertDispatcher`, `AlertEventGate`, or `AlertMonitoring` without understanding the full deduplication flow
3. Do NOT add direct imports from `org.jetbrains.plugins.terminal`
4. Do NOT lower `sinceBuild` below 243
5. Keep `kotlin.stdlib.default.dependency=false`

## Verification After Changes

Always run:

```bash
./gradlew buildPlugin
./gradlew verifyPlugin
```

See `docs/agent-context/verification-checklist.md` for detailed per-area checks.

---
*Last updated from code scan: 2026-03-18*
