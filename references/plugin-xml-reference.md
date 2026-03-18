# plugin.xml Reference — Error Sound Alert

Quick lookup for plugin manifest configuration.

## Identity

```xml
<id>com.drostwades.errorsound</id>
<name>Error Sound Alert</name>
<vendor email="jkjitendra725@gmail.com" url="https://github.com/jkjitendra">jkjitendra</vendor>
```

## Dependencies

```xml
<depends>com.intellij.modules.platform</depends>
<depends optional="true" config-file="terminal-features.xml">org.jetbrains.plugins.terminal</depends>
```

- `com.intellij.modules.platform` — required, makes plugin available in all IntelliJ-based IDEs
- `org.jetbrains.plugins.terminal` — optional, loads `terminal-features.xml` only when terminal plugin is present

## Extension Points Registered

### In `plugin.xml`

| Extension | Type | Class | Purpose |
|---|---|---|---|
| `applicationConfigurable` | Settings panel | `ErrorSoundConfigurable` | Settings → Tools → Error Sound Alert |
| `consoleFilterProvider` | Console scanner | `ErrorConsoleFilterProvider` | Scans console lines for error patterns |
| `toolWindow` | Sidebar panel | `ErrorSoundToolWindowFactory` | Error Monitor (right sidebar) |

### In `terminal-features.xml`

| Extension | Type | Class | Purpose |
|---|---|---|---|
| `backgroundPostStartupActivity` | Startup activity | `AlertOnTerminalCommandListener` | Terminal command monitoring |

## Project Listeners

```xml
<projectListeners>
    <listener class="com.drostwades.errorsound.AlertOnErrorExecutionListener"
              topic="com.intellij.execution.ExecutionListener"/>
</projectListeners>
```

## Tool Window Configuration

```xml
<toolWindow
    id="Error Monitor"
    icon="/icons/errorMonitor.svg"
    anchor="right"
    secondary="true"
    factoryClass="com.drostwades.errorsound.ErrorSoundToolWindowFactory"/>
```

---
*Last updated from code scan: 2026-03-18*
