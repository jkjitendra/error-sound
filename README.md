# Error Sound Alert

> An IntelliJ Platform plugin that plays an audio alert the moment a Run/Debug process exits with an error — so you can stay focused and only look up when something goes wrong.

[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains-Marketplace-orange?logo=jetbrains)](https://plugins.jetbrains.com/plugin/com.drostwades.errorsound)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build: Gradle](https://img.shields.io/badge/Build-Gradle-green?logo=gradle)](build.gradle.kts)

---

## Overview

**Error Sound Alert** listens to every Run/Debug process in your IDE. The moment one terminates with a non-zero exit code or emits recognisable error output, it plays a short audio alert. No more staring at the progress bar — just listen for the sound.

---

## Features

| Feature | Details |
|---|---|
| Smart error detection | Classifies errors as Configuration, Compilation, Test Failure, Network, Exception, or Generic |
| 7 built-in sounds | Boom, Faaa, Huh, Punch, Yeah Boy, Yooo, Dog Laughing |
| Custom audio support | Point to any local WAV / AIFF / AU file |
| Per-kind sounds | Assign a different sound to each error category |
| Global mode | Use one sound for all error types |
| Volume control | 0 – 100% with dB-accurate scaling |
| Alert duration | 1 – 10 seconds, with automatic clip looping |
| Instant preview | Hear any sound immediately from the settings panel |
| Cross-platform | macOS, Windows, Linux (JVM audio backend) |

---

## Supported IDEs

All IntelliJ-based IDEs — IntelliJ IDEA, PyCharm, WebStorm, GoLand, Rider, and more.

**Compatibility:** Build `241` (2024.1) through `253.*` (2025.3)

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

Open **Settings / Preferences → Tools → Error Sound Alert**

| Option | Description |
|---|---|
| Enable / Disable | Master toggle for all alerts |
| Sound source | **Built-in** (bundled WAV) or **Custom** (local file path) |
| Global sound mode | One sound for all error types |
| Per-kind sounds | Individual sound + enable/disable per error category |
| Custom file path | Absolute path to a WAV / AIFF / AU file |
| Volume | 0 – 100% |
| Alert duration | 1 – 10 seconds |

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

**Prerequisites:** JDK 17+

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

1. The plugin registers an `ExecutionListener` via `<projectListeners>` in `plugin.xml`
2. When a Run/Debug process starts, a `ProcessAdapter` is attached to capture all output
3. Each output chunk is scanned in real time by `ErrorClassifier` for known error patterns
4. On process termination, the full buffered output and exit code are re-evaluated
5. If an error is detected and the plugin is enabled, `ErrorSoundPlayer` plays the configured sound asynchronously
6. If the clip is shorter than the alert duration, it loops until time expires
7. Fallback chain: custom file → built-in WAV → generated 880 Hz tone → system beep

---

## Project Structure

```
src/main/kotlin/com/drostwades/errorsound/
├── AlertOnErrorExecutionListener.kt  # Process lifecycle listener
├── AlertSettings.kt                  # Persistent settings state
├── BuiltInSounds.kt                  # Built-in sound registry
├── ErrorKind.kt                      # Error enum + classifier
├── ErrorSoundConfigurable.kt         # Settings UI panel
└── ErrorSoundPlayer.kt               # Audio playback engine

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
