# Detection & Alert Flow Reference — Error Sound Alert

Quick lookup for the end-to-end detection and alert flow.

## Detection Sources

```
┌─────────────────────────────────────┐
│ 1. ExecutionListener                │  Process exit + output analysis
│    AlertOnErrorExecutionListener    │  Key: "exec:{handlerHash}:{kind}"
│                                     │  NONE + exitCode 0 → SUCCESS
├─────────────────────────────────────┤
│ 2. ConsoleFilterProvider            │  Per-line pattern matching
│    ErrorConsoleFilterProvider       │  Key: "console:{projectHash}:{kind}"
├─────────────────────────────────────┤
│ 3. Terminal (reflection)            │  Command exit code analysis
│    AlertOnTerminalCommandListener   │  Key: "terminal:{projectHash}:{cmd}:{exitCode}:{kind}"
└─────────────────────────────────────┘
```

## Routing Chain

```
Detection Source
       │
       ▼
AlertDispatcher.tryAlert(key, settings, kind)
       │
       ├── 1. SnoozeState.isSnoozed()
       │       └── short-circuits if snoozed (transient, no settings needed)
       │
       ├── 2. AlertMonitoring.shouldMonitor(settings, kind)
       │       ├── settings.enabled == true?
       │       └── isKindEnabled(settings, kind)?
       │
       ├── 3. AlertEventGate.shouldPlay(key)
       │       ├── per-key cooldown: 4s
       │       └── global cooldown: 2s
       │
       └── 4. ErrorSoundPlayer.play(settings, kind)
               ├── debounce: 250ms (last-resort)
               ├── isErrorKindEnabled(settings, kind)
               └── resolveBuiltInSoundId(settings, kind) → play audio
```

## Error Classification

| Priority | ErrorKind | Example Patterns |
|---|---|---|
| 1 | CONFIGURATION | `"could not resolve placeholder"`, `"beancreationexception"` |
| 2 | COMPILATION | `"compilation failed"`, `"cannot find symbol"`, `"error:"` |
| 3 | TEST_FAILURE | `"tests failed"`, `"assertionerror"` |
| 4 | NETWORK | `"connection refused"`, `"unknownhostexception"` |
| 5 | EXCEPTION | `"exception"`, `"caused by:"`, `"stacktrace"` |
| 6 | GENERIC | exitCode ≠ 0, `"failed"`, `"error"` |

Terminal-specific: `detectTerminal()` only checks exit code (0 = NONE, non-zero = GENERIC).

Execution listener: after classification, if kind is NONE and exit code is 0, converts to SUCCESS.

## Sound Resolution

```
useGlobalBuiltInSound == true?
  → builtInSoundId (global)
  → else per-kind soundId

soundSource == CUSTOM?
  → customSoundPath → file → play
  → else builtInSoundId → resource → play

Fallback chain:
  custom file → built-in WAV → 880Hz tone → system beep
```

## Deduplication Keys

| Source | Key Format |
|---|---|
| Execution | `"exec:{System.identityHashCode(handler)}:{errorKind}"` |
| Console | `"console:{project.locationHash}:{errorKind}"` |
| Terminal | `"terminal:{project.locationHash}:{command.trim()}:{exitCode}:{errorKind}"` |

---
*Last updated from code scan: 2026-03-23*
