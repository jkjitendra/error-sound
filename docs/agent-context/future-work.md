# Future Work — Error Sound Alert

Suggested features and safe extension points for future development.

---

## 1. Visual Notifications — DONE (v1.1.4)

Implemented in Phase 4. See `recent-changes.md` for the full engineering summary.

---

## 2. Custom Regex Error Rules — DONE (v1.1.5)

Implemented in Phase 5. See `recent-changes.md` for the full engineering summary.

---

## 3. Exit-Code-Specific Terminal Sounds — DONE (v1.1.6)

Implemented in Phase 6. See `recent-changes.md` for the full engineering summary.

---

## 4. Per-Project Settings — PARTIALLY DONE (v1.1.7, Phase 7)

**Phase 7 scope (implemented):**
- Per-project override for the master `enabled` flag only
- `ProjectAlertSettings` (project service, workspace storage) + `ResolvedSettingsResolver` (merge layer)
- Project Profile UI section in the Error Monitor tool window
- All three detection paths use `ResolvedSettingsResolver.resolve()` at dispatch time

**Remaining future scope (Phase 8+):**
- Field-by-field project overrides (per-kind flags, sounds, custom rules, exit-code rules)
- Consider a dedicated project-level configurable screen for a richer UI

**Risks for next field-by-field expansion:** Medium — must handle partial merge of structured state carefully and avoid state explosion in the project-level settings model.

---
*Last updated from code scan: 2026-04-11*
