# Maintenance Rules — Error Sound Alert

Rules for keeping the agent context layer in sync with the codebase.

---

## When a New Feature Is Added

| Action | Files to Update |
|---|---|
| New class added | `docs/agent-context/code-map.md` — add a section |
| New detection path | `docs/agent-context/architecture.md` — add path + update routing flow |
| New settings field | `references/settings-and-state-reference.md`, `docs/agent-context/code-map.md` (AlertSettings section) |
| New extension point | `references/plugin-xml-reference.md`, `docs/agent-context/architecture.md` |
| New built-in sound | `docs/agent-context/code-map.md` (BuiltInSounds section) |
| New Error Monitor preset | `docs/agent-context/code-map.md` (ErrorSoundToolWindowFactory section) |
| Changelog-worthy change | `docs/agent-context/recent-changes.md` |
| Feature completed from future-work | Remove from `docs/agent-context/future-work.md` |

## When Build Baseline Changes

| Action | Files to Update |
|---|---|
| Gradle version change | `AGENTS.md`, `docs/agent-context/build-and-compatibility.md`, `references/build-reference.md` |
| Kotlin version change | Same as Gradle + check `apiVersion`/`languageVersion` notes |
| Java toolchain change | Same as Gradle |
| IntelliJ Platform Plugin version change | Same as Gradle |
| `sinceBuild` / `untilBuild` change | `AGENTS.md`, `docs/agent-context/build-and-compatibility.md`, `docs/agent-context/project-overview.md`, `references/build-reference.md`, README |
| Target platform change | Same as sinceBuild |

## When a Core Class Is Added or Removed

1. Add/remove section in `docs/agent-context/code-map.md`
2. If it affects the detection → dispatch → playback flow: update `docs/agent-context/architecture.md`
3. If it's a new extension point: update `references/plugin-xml-reference.md`
4. If it touches settings: update `references/settings-and-state-reference.md`
5. Add a note in `docs/agent-context/recent-changes.md`

## Doc Alignment Requirements

These documents must stay aligned with each other:

| Source of Truth | Must Match |
|---|---|
| `build.gradle.kts` description | README.md features section |
| `build.gradle.kts` changeNotes | `docs/agent-context/recent-changes.md` |
| `plugin.xml` extensions | `references/plugin-xml-reference.md` |
| `AlertSettings.State` fields | `references/settings-and-state-reference.md` |
| `AGENTS.md` baseline table | `docs/agent-context/build-and-compatibility.md` |

## "Last Updated" Notes

Every context file includes a `*Last updated from code scan: YYYY-MM-DD*` footer. Update this date when you modify the file.

## General Principles

1. **Do not invent facts** — only document what is verifiable in code.
2. **Keep files skimmable** — use tables, code blocks, and short paragraphs.
3. **Mark uncertainty** — use "Needs verification" for anything unclear.
4. **Don't duplicate** — reference other context files instead of repeating content.

---
*Last updated from code scan: 2026-03-18*
