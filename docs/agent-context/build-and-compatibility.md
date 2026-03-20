# Build & Compatibility — Error Sound Alert

## Current Build Stack

| Component | Version | Source |
|---|---|---|
| Gradle | 9.0 | `gradle-wrapper.properties` |
| Kotlin | 2.3.10 | `build.gradle.kts` plugin declaration |
| Kotlin API/Language Version | 2.0 | `KotlinCompile` compilerOptions |
| Java toolchain | 21 | `kotlin { jvmToolchain(21) }` |
| JVM target | 21 | `compilerOptions { jvmTarget = JVM_21 }` |
| IntelliJ Platform Gradle Plugin | 2.12.0 | `build.gradle.kts` plugins block |
| Target platform | IC 2024.3 | `intellijPlatform { create("IC", "2024.3") }` |
| `sinceBuild` | 243 | `pluginConfiguration { ideaVersion { sinceBuild = "243" } }` |
| `untilBuild` | unset (open-ended) | `untilBuild = provider { null }` |

## Why 2024.3+ Baseline

The Kotlin 2.x stdlib bundled with the plugin requires IntelliJ 2024.3 (build 243) or later. Earlier IDE versions bundle Kotlin 1.x stdlib, which is binary-incompatible. This is explicitly documented in the 1.1.0 changelog.

## gradle.properties Notes

```properties
kotlin.stdlib.default.dependency=false    # Platform bundles its own stdlib — do NOT add it
org.gradle.configuration-cache=true       # Config cache enabled
org.gradle.caching=true                   # Build caching enabled
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
```

**Critical:** `kotlin.stdlib.default.dependency=false` must remain `false`. The IntelliJ Platform bundles its own Kotlin stdlib. Adding it as a dependency would cause classloader conflicts.

## Signing & Publishing

- Certificate chain, private key, and password read from environment variables
- Publish token also from environment variable
- Not hardcoded anywhere in the repo

## Terminal Plugin Dependency

```xml
<!-- plugin.xml -->
<depends optional="true" config-file="terminal-features.xml">org.jetbrains.plugins.terminal</depends>
```

The terminal plugin is an **optional** dependency. When present, `terminal-features.xml` registers `AlertOnTerminalCommandListener` as a `backgroundPostStartupActivity`. The listener class itself uses only reflection — no compile-time imports from the terminal plugin.

## Kotlin Compiler Options

```kotlin
compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
    apiVersion.set(KotlinVersion.KOTLIN_2_0)
    languageVersion.set(KotlinVersion.KOTLIN_2_0)
    freeCompilerArgs.add("-Xjvm-default=all")
}
```

- `-Xjvm-default=all` generates default method implementations in interfaces (needed for IntelliJ API compatibility)

## Bundled Plugin Dependencies

```kotlin
bundledPlugins(listOf("org.jetbrains.plugins.terminal"))
```

This makes the terminal plugin available at compile time (for interface definition discovery), but the plugin is declared `optional` in `plugin.xml`.

## Compatibility Caveats for Future Upgrades

1. **Raising Kotlin version** — check that the target IDE bundles a compatible stdlib. Keep `apiVersion`/`languageVersion` at or below the stdlib version the oldest supported IDE bundles.
2. **Raising `sinceBuild`** — update README, Marketplace description, and changelog.
3. **Setting `untilBuild`** — currently open-ended for forward compatibility. Only set if a specific incompatibility is discovered.
4. **Gradle upgrades** — IntelliJ Platform Gradle Plugin 2.x requires Gradle 8.2+. Current version (9.0) is well above.
5. **Java toolchain** — raising above 21 may require verifying IDE compatibility.
6. **Terminal reflection** — internal APIs change between IDE versions. The reflection code in `AlertOnTerminalCommandListener` is the most fragile point.

---
*Last updated from code scan: 2026-03-18*
