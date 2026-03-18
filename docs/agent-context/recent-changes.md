# Recent Changes — Error Sound Alert

Engineering-significant changes to the codebase. Not a full changelog — focuses on architectural and compatibility changes.

---

## v1.1.1 — Deduplication & Dispatch Overhaul

### AlertDispatcher Introduction
- **New class** `AlertDispatcher.kt` — single choke-point between all three detection paths and `ErrorSoundPlayer`
- All detection sources now route through `AlertDispatcher.tryAlert()` instead of calling `ErrorSoundPlayer.play()` directly
- Ensures `AlertMonitoring` → `AlertEventGate` → `ErrorSoundPlayer` is wired in exactly one place

### AlertEventGate Introduction
- **New class** `AlertEventGate.kt` — central deduplication gate
- Per-key cooldown (4s) and global cooldown (2s) prevent duplicate sounds
- Map eviction at 512 entries (60s TTL) prevents memory leaks from terminal command keys
- Thread-safe via `@Synchronized`

### Reduced Audio-Player Debounce
- `ErrorSoundPlayer.DEBOUNCE_MS` reduced from a larger value to 250ms
- Now serves only as a last-resort guard — `AlertEventGate` owns real throttling

### Console Detection via AlertDispatcher
- `ErrorConsoleFilterProvider` now uses `AlertDispatcher.tryAlert()` with key `"console:{projectHash}:{errorKind}"`
- Previously called `ErrorSoundPlayer` directly

### Execution Listener via AlertDispatcher
- `AlertOnErrorExecutionListener` now routes through `AlertDispatcher.tryAlert()`
- Uses dedup key `"exec:{handlerIdentityHash}:{errorKind}"`

### Terminal Logging Cleanup
- Debug-level logging for expected situations (class not found, view not ready)
- Warn-level logging reserved for actual failures
- Reduces noise in IDE logs for users on newer IDE versions where some terminal classes don't exist

---

## v1.1.0 — Platform Migration

### IntelliJ Platform Gradle Plugin 2.x Migration
- Migrated from IntelliJ Gradle Plugin 1.x to IntelliJ Platform Gradle Plugin 2.12.0
- New DSL: `intellijPlatform { }` block replaces `intellij { }` block
- `pluginConfiguration`, `pluginVerification`, `signing`, `publishing` now nested under `intellijPlatform`

### Baseline Raise to 2024.3 / Build 243
- `sinceBuild` raised from 241 to 243
- Required because Kotlin 2.x stdlib is only bundled in 2024.3+
- Breaking change documented in changelog

### Java 21 / Kotlin 2.3.10 Setup
- Java toolchain raised to 21
- Kotlin compiler version raised to 2.3.10
- apiVersion/languageVersion set to 2.0 for compatibility
- `-Xjvm-default=all` compiler flag added

### Gradle 9.0
- Gradle wrapper updated to 9.0
- Configuration cache and build caching enabled

### Open untilBuild Policy
- `untilBuild = provider { null }` — no upper bound
- Forward-compatible with future IDE releases

---

## v1.0.4 — Error Monitor Tool Window
- Added **Error Monitor** sidebar panel (`ErrorSoundToolWindowFactory`)
- Per-kind toggle checkboxes with presets (All, Build Only, Runtime Only)
- `AlertMonitoring` object introduced for centralized monitoring rule checks

## v1.0.3 — Console Detection & Terminal
- Added `ErrorConsoleFilterProvider` for real-time console output scanning
- Improved terminal compatibility with 2025.x reworked terminal engine

---
*Last updated from code scan: 2026-03-18*
