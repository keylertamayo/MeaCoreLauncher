# MeaCore Launcher

## Overview
MeaCore Launcher is a high-performance, open-source Minecraft launcher built with Java 21 and JavaFX 21. It is a desktop GUI application targeting Linux (Ubuntu/Debian) and Windows, competitive with alternatives like MultiMC and Prism Launcher.

## Key Features
- **Isolated Profile Management**: each profile has its own data folder, mods, saves
- **Smart Auto-Optimization**: RAM-based JVM preset selection (LOW/BALANCED/HIGH) with Aikar's flags and ZGC for HIGH preset
- **NeoForge Support**: installs Forge (1.12.2–1.20.1), NeoForge (1.20.2+), and Fabric automatically
- **Performance Mods Auto-Installer**: one-click Sodium, Lithium, FerriteCore, ImmediatelyFast install via Modrinth
- **Crash Detector**: analyzes crash reports after game closes and shows diagnostic popup with specific solutions
- **Java 21 Support**: downloads Java 8, 17, or 21 portably; auto-detects required version per Minecraft version
- **RAM Monitor / Hardware Cache**: OSHI-based hardware detection with cached HardwareInfo to avoid repeated OS calls
- **Parallel Downloads**: GameFilesInstaller uses 16-thread pool for libraries and assets
- **Deep Clean**: strips non-ES/EN language assets from Minecraft (saves ~100MB per version)
- **Modrinth Integration**: browse, search, and install mods/modpacks with dynamic User-Agent

## Architecture
- **Language**: Java 21
- **UI Framework**: JavaFX 21
- **Build System**: Gradle 8.10.2 (Kotlin DSL)
- **Build Config**: `build.gradle.kts`, `settings.gradle.kts`
- **Entry Point**: `src/main/java/com/experimento/launcher/Main.java`
- **Main App**: `src/main/java/com/experimento/launcher/LauncherApp.java` (~1600 lines)
- **Launcher Facade**: `LauncherFacade.java` — central coordinator for all backend operations

## Service Layer (src/main/java/com/experimento/launcher/service/)
- `JvmPresetService` — JVM args by preset (LOW/BALANCED/HIGH); HIGH uses ZGC+ZGenerational; scales memory by RAM
- `AutoOptimizerService` — writes options.txt with 14 Minecraft performance settings
- `JavaRuntimeService` — downloads JRE 8/17/21 from Adoptium API with 65KB buffer and retry
- `SystemInfoService` — cached OSHI hardware info; refresh available separately
- `CrashReportService` — NEW: parses crash-reports/*.txt and returns typed diagnoses with solutions
- `PerformanceModsService` — NEW: installs Sodium/Lithium/FerriteCore/Indium via Modrinth API for Fabric/Forge/NeoForge
- `LauncherFacade` — coordinates installation, launch, runtime detection, profile management

## Modloaders (src/main/java/com/experimento/launcher/modloaders/)
- `ModloaderInstallerService` — Forge (1.12.2–1.20.1), NeoForge (1.20.2+, dynamic version), Fabric (dynamic installer)
  - All three accept optional `JavaRuntimeService` to use portable JRE

## Mojang Integration (src/main/java/com/experimento/launcher/mojang/)
- `GameFilesInstaller` — parallel 16-thread library+asset downloader, Deep Clean of assets
- `MojangVersionResolver` — resolves and merges version JSON
- `GameLauncher` — builds the full Java launch command
- `HttpFiles` — shared HTTP download utility

## Store Integration (src/main/java/com/experimento/launcher/store/)
- `ModrinthStoreClient` — Modrinth search + download URL resolver; User-Agent uses `LauncherMetadata.VERSION`
- `StoreDownloader` — orchestrates modpack ZIP installation and dependency detection

## Running the App
The `./gradlew run --no-daemon` command is configured as the primary workflow. Uses Gradle toolchain to download JDK 21 from Adoptium automatically. System Java is GraalVM 19.

## Deployment
Configured as VM deployment type (required for JavaFX desktop apps).
