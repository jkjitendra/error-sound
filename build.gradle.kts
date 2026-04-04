import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.intellij.platform") version "2.12.0"
    kotlin("jvm") version "2.3.10"
}

group = "com.drostwades"
version = "1.1.4"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        create("IC", "2024.3")

        bundledPlugins(listOf("org.jetbrains.plugins.terminal"))
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }

        description = """
            <p>
              Error Sound Alert plays an audio alert the moment a Run/Debug process exits with an error,
              a recognisable error pattern appears in console output, or a terminal command fails —
              so you can stay focused on other work and only look up when something goes wrong.
              Optionally, play a distinct sound when a Run/Debug process completes successfully.
            </p>
            <h3>Features</h3>
            <ul>
              <li>7 built-in alert sounds: Boom, Faaa, Huh, Punch, Yeah Boy, Yooo, Dog Laughing</li>
              <li>Supports custom audio files (WAV, AIFF, AU)</li>
              <li>Smart error classification: configuration, compilation, test failure, network, exception</li>
              <li>Per-kind sounds, or one global sound for all alerts</li>
              <li><b>Success sounds:</b> optional alert on successful process completion (off by default)</li>
              <li>Configurable volume (0–100%) and alert duration (1–10 seconds)</li>
              <li>Minimum process duration threshold: skip alerts for processes that finish too quickly (Run/Debug only)</li>
              <li>Snooze / mute: temporarily silence all alerts for 15 minutes or 1 hour from the Error Monitor sidebar</li>
              <li><b>Visual notifications:</b> optional balloon notification alongside each sound alert, configurable per error/success (off by default)</li>
              <li>Instant preview from the settings panel</li>
              <li><b>Error Monitor</b> sidebar panel: enable/disable monitoring, filter by error type, and apply presets without leaving your editor</li>
            </ul>
            <h3>Supported IDEs</h3>
            <p>All IntelliJ-based IDEs (IntelliJ IDEA, PyCharm, WebStorm, GoLand, etc.) version 2024.3+.</p>
        """.trimIndent()

        changeNotes = """
            <b>1.1.4</b>
            <ul>
              <li>New visual notification companion: shows a balloon notification alongside each sound alert (off by default)</li>
              <li>Configurable separately for errors and successes in Settings &rarr; Tools &rarr; Error Sound Alert</li>
              <li>Balloon includes quick actions: <b>Open Settings</b> and <b>Mute 1 hr</b></li>
              <li>Notification spam prevented by existing deduplication gate — no extra configuration needed</li>
            </ul>
            <b>1.1.3</b>
            <ul>
              <li>New Snooze / Mute feature: silence all alerts for 15 minutes or 1 hour directly from the Error Monitor sidebar</li>
              <li>Snooze resets automatically on IDE restart (transient, never persisted)</li>
              <li>New minimum process duration threshold: suppress alerts for processes that finish faster than a configured limit (Run/Debug only, 0&nbsp;= disabled)</li>
              <li>Threshold setting available in Settings &rarr; Tools &rarr; Error Sound Alert</li>
            </ul>
            <b>1.1.2</b>
              <ul>
                <li>Added optional success alerts for Run/Debug process completions (off by default)</li>
                <li>Added per-success sound selection in settings</li>
                <li>Added success monitoring toggle in the Error Monitor tool window</li>
              </ul>
            <b>1.1.1</b>
            <ul>
              <li>Fixed duplicate alert sounds by improving event deduplication and cooldown handling</li>
              <li>Improved console error detection and alert dispatch reliability</li>
              <li>Improved terminal command failure detection for newer IDE versions</li>
            </ul>
            <b>1.1.0</b> — <i>Breaking: requires IDE 2024.3+</i>
            <ul>
              <li>Migrated to IntelliJ Platform Gradle Plugin 2.12.0, Gradle 9.0, Kotlin 2.3.10, Java 21</li>
              <li>Raised minimum IDE version to 2024.3 (build 243) for Kotlin 2.x stdlib compatibility</li>
              <li>Removed upper IDE version cap for forward compatibility with future IDE releases</li>
            </ul>
            <b>1.0.4</b>
            <ul>
              <li>New <b>Error Monitor</b> sidebar tool window (right panel) with custom icon and dark theme support</li>
              <li>Master toggle to enable/disable monitoring and per-error-type filters (Configuration, Compilation, Test failure, Network, Exception, Generic)</li>
              <li>Quick actions: Select all, Clear all; Presets: All, Build only, Runtime only</li>
              <li>Live status indicator showing active/paused state and enabled type count</li>
              <li>Quick-access button to open sound settings from the sidebar</li>
              <li>Works across all IntelliJ Platform IDEs (IDEA, PyCharm, WebStorm, GoLand, CLion, DataGrip, etc.)</li>
            </ul>
            <b>1.0.3</b>
            <ul>
              <li>Console error detection: plays alert when exceptions, errors, build failures, or stack traces appear in any console output (Run/Debug, Test, Gradle, Terminal)</li>
              <li>Smart debounce: prevents repeated alerts when multiple error lines appear in quick succession</li>
              <li>Improved compatibility with IntelliJ 2025.x terminal</li>
            </ul>
            <b>1.0.2</b>
            <ul>
              <li>Add plugin icon for Marketplace and IDE plugin manager</li>
            </ul>
            <b>1.0.1</b>
            <ul>
              <li>Replace deprecated ProcessAdapter with ProcessListener interface</li>
              <li>Replace deprecated FileChooserDescriptorFactory.createSingleFileDescriptor() with direct FileChooserDescriptor constructor</li>
              <li>Replace scheduled-for-removal addBrowseFolderListener 4-arg overload with current 2-arg API</li>
            </ul>
            <b>1.0.0</b>
            <ul>
              <li>Initial release</li>
              <li>Alert on failed Run/Debug process exits</li>
              <li>Configurable audio source and volume</li>
            </ul>
        """.trimIndent()
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            apiVersion.set(KotlinVersion.KOTLIN_2_0)
            languageVersion.set(KotlinVersion.KOTLIN_2_0)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }
}
