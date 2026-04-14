# MeaCore Launcher

## Overview
MeaCore Launcher is a high-performance, open-source Minecraft launcher built with Java 21 and JavaFX 21. It is a desktop GUI application targeting Linux (Ubuntu/Debian) and Windows.

## Key Features
- Isolated Profile Management: each profile has its own data folder
- Smart Auto-Optimization: analyzes system RAM and injects optimized JVM arguments
- Modding Integration: built-in Forge/Fabric support and Modrinth modpack integration
- System Integration: optimized for GNOME on Linux, uses OSHI for hardware telemetry

## Architecture
- **Language**: Java 21
- **UI Framework**: JavaFX 21
- **Build System**: Gradle 8.10.2 (Kotlin DSL)
- **Build Config**: `build.gradle.kts`, `settings.gradle.kts`
- **Entry Point**: `src/main/java/com/experimento/launcher/Main.java`
- **Main App**: `src/main/java/com/experimento/launcher/LauncherApp.java`

## Project Structure
```
src/main/java/com/experimento/launcher/
  Main.java             - Entry point (JavaFX bridge)
  LauncherApp.java      - Main JavaFX application and UI logic
  model/                - Data structures (LauncherProfile, ServerEntry)
  mojang/               - Mojang API interaction, version resolving, game launch
  service/              - Backend services (AutoOptimizerService, JavaRuntimeService, HardwareProbe)
  store/                - Modrinth modpack integration
  util/                 - Helper classes (hashing, UUIDs)
src/main/resources/     - CSS, icons, version.properties
website/                - Project landing page (Netlify hosted)
instances/              - Default directory for Minecraft instances
```

## Dependencies
- **JavaFX 21**: GUI framework
- **Jackson**: JSON processing
- **OSHI + JNA**: Hardware diagnostics
- **Adventure NBT**: Minecraft NBT data format
- **SLF4J**: Logging
- **JUnit 5**: Testing

## Running
This is a desktop GUI application. The workflow uses VNC output to display the JavaFX window.

Command: `./gradlew run --no-daemon`

The Gradle toolchain resolver automatically downloads JDK 21 if not present on the system.

## Notes
- The system Java is GraalVM CE 19.0.2; Gradle's toolchain resolver downloads JDK 21 automatically
- JavaFX requires a graphical display; in Replit it runs via VNC
- GTK WM_CLASS properties are set for Linux GNOME integration
