# Build Reference — Error Sound Alert

Quick lookup for build configuration.

## Versions

| Component | Version | File |
|---|---|---|
| Gradle | 9.0 | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | 2.3.10 | `build.gradle.kts` |
| Kotlin API/Language | 2.0 | `build.gradle.kts` compilerOptions |
| Java toolchain | 21 | `build.gradle.kts` |
| IntelliJ Platform Gradle Plugin | 2.12.0 | `build.gradle.kts` |
| Target platform | IC 2024.3 | `build.gradle.kts` |
| sinceBuild | 243 | `build.gradle.kts` |
| untilBuild | unset | `build.gradle.kts` |
| Plugin version | 1.1.1 | `build.gradle.kts` |

## Key Commands

```bash
./gradlew buildPlugin         # Build plugin ZIP
./gradlew clean buildPlugin   # Clean build
```

## gradle.properties

```properties
kotlin.stdlib.default.dependency=false
org.gradle.configuration-cache=true
org.gradle.caching=true
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
```

## Compiler Options

```kotlin
jvmTarget = JVM_21
apiVersion = KOTLIN_2_0
languageVersion = KOTLIN_2_0
freeCompilerArgs = ["-Xjvm-default=all"]
```

## Output

- Plugin ZIP: `build/distributions/error-sound-<version>.zip`

---
*Last updated from code scan: 2026-03-18*
