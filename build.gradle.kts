import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.intellij.platform") version "2.12.0"
    kotlin("jvm") version "2.3.10"
}

group = "com.drostwades"
version = "1.1.10"

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
              <li><b>Custom regex rules:</b> define your own patterns mapped to error kinds, evaluated before built-in classification</li>
              <li><b>Rule Testing Sandbox:</b> paste sample output and see which custom rule or built-in classifier would match before applying rule changes</li>
              <li><b>Exit-code rules for terminal commands:</b> map specific exit codes to error kinds, optional per-code sound overrides, or suppress alerts entirely (e.g. silence Ctrl+C / exit&nbsp;130)</li>
              <li>Per-kind sounds, or one global sound for all alerts</li>
              <li><b>Success sounds:</b> optional alert on successful process completion (off by default)</li>
              <li>Configurable volume (0–100%) and alert duration (1–10 seconds)</li>
              <li>Minimum process duration threshold: skip alerts for processes that finish too quickly (Run/Debug only)</li>
              <li>Snooze / mute: temporarily silence all alerts for 15 minutes or 1 hour from the Error Monitor sidebar</li>
              <li><b>Visual notifications:</b> optional balloon notification alongside each sound alert, configurable per error/success (off by default)</li>
              <li>Instant preview from the settings panel</li>
              <li><b>Error Monitor</b> sidebar panel: enable/disable monitoring, filter by error type, and apply presets without leaving your editor</li>
              <li><b>Project-level profiles:</b> override the master monitoring enabled state per project from the Error Monitor sidebar; all other settings remain global</li>
              <li><b>Per-kind volume:</b> set an independent volume level for each error/success kind; falls back to the global volume when no override is set</li>
            </ul>
            <h3>Supported IDEs</h3>
            <p>All IntelliJ-based IDEs (IntelliJ IDEA, PyCharm, WebStorm, GoLand, etc.) version 2024.3+.</p>
        """.trimIndent()

        changeNotes = """
            <b>1.1.10</b>
            <ul>
              <li>New internal <b>Rule Match Explanation</b> plumbing records why each alert classification was produced</li>
              <li>Runtime explanations distinguish custom regex matches, built-in classifier matches, terminal exit-code rules, terminal exit-code suppression, success fallback, no-match, and duration-threshold suppression</li>
              <li>All alert sources now produce explanation objects near classification time and pass them through the existing dispatcher for future notification/history UI</li>
              <li>Alert dispatch gate order and playback behavior are unchanged</li>
            </ul>
            <b>1.1.9</b>
            <ul>
              <li>New <b>Rule Testing Sandbox</b> in Settings &rarr; Tools &rarr; Error Sound Alert for testing custom regex rules before applying changes</li>
              <li>Paste sample output, choose Source and Match Target, optionally set an Exit Code, then click <b>Test Rules</b></li>
              <li>Shows whether a custom rule matched, which rule matched, the resulting error kind, and whether built-in classification would match if no custom rule did</li>
              <li>Reports regex validation errors and clear no-match results without triggering runtime alerts, notifications, or playback</li>
            </ul>
            <b>1.1.8</b>
            <ul>
              <li>New <b>Per-Kind Volume</b>: set an independent volume override for each error/success kind in Settings → Tools → Error Sound Alert</li>
              <li>When no per-kind override is set the global volume continues to apply, preserving existing behaviour exactly</li>
              <li>Per-kind volume is independent of sound-source choice (bundled or custom file) and of the global built-in sound mode</li>
              <li>Per-kind sound preview now plays at that kind&#39;s effective volume (override if set, otherwise global)</li>
              <li>Exit-code rule sound overrides continue to use the resolved kind&rsquo;s volume policy</li>
            </ul>
            <b>1.1.7</b>
            <ul>
              <li>New <b>Project-Level Profiles</b>: override the master monitoring <b>enabled</b> flag per project from the Error Monitor sidebar</li>
              <li>When no project override is set, monitoring state is fully inherited from global application settings</li>
              <li>Project override is saved in workspace storage (per-workspace, not shared across clones)</li>
              <li>All other settings (sounds, per-kind flags, custom rules, exit-code rules) remain global in this release</li>
              <li>Status label in the Error Monitor panel now shows <code>(global)</code> or <code>(project override)</code> to clarify which setting is in effect</li>
            </ul>
            <b>1.1.6</b>
            <ul>
              <li>New <b>Exit-Code Rules</b> for terminal commands: map specific exit codes (e.g., 137&nbsp;=&nbsp;SIGKILL, 127&nbsp;=&nbsp;command not found) to error kinds, optional per-code sound overrides, and suppression (e.g., silence Ctrl+C / exit&nbsp;130 by default)</li>
              <li>Rules configurable in Settings &rarr; Tools &rarr; Error Sound Alert under "Exit-Code Rules"</li>
              <li>Applies to terminal path only; custom regex rules still take precedence</li>
            </ul>
            <b>1.1.5</b>
            <ul>
              <li>New <b>Custom Regex Rules</b>: define your own patterns in Settings &rarr; Tools &rarr; Error Sound Alert and map them to an error kind</li>
              <li>Custom rules are evaluated before built-in classification &mdash; first matching rule wins</li>
              <li>Three match targets: <b>LINE_TEXT</b> (per line in Run/Debug and Console), <b>FULL_OUTPUT</b> (Run/Debug final buffered output), <b>EXIT_CODE_AND_TEXT</b> (Run/Debug and Terminal, matches against exit code + text)</li>
              <li>Rules with invalid regex patterns are highlighted inline in the settings table and skipped at runtime without disrupting other rules</li>
              <li>Disabled rules are preserved and can be re-enabled without re-entering the pattern</li>
            </ul>
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
