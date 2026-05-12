# MeaCore Launcher — Agent Guide

## Tech stack
- Java 21 + JavaFX 21.0.5, Gradle (Groovy DSL), **non-modular** (no `module-info.java`)
- Jackson, OSHI, Adventure NBT, JNA, SLF4J, JUnit 5
- Static website in `website/` (plain HTML/CSS/JS)
- Replit/Node.js dev server at `server.js` (port 5000)

## Entrypoints
- **Launcher**: `com.experimento.launcher.Main` → reflectively calls `LauncherApp.main()` (JavaFX `Application`)
- **Website dev**: `node server.js` (serves `website/` on `0.0.0.0:5000`)

## Build & test commands
```bash
./gradlew build                # compile + test
./gradlew build -x test        # CI does this — tests may be stale
./gradlew run                  # runs the JavaFX app
./gradlew --stop               # reset daemon if toolchain errors
```

Only 2 tests exist: `ServersDatServiceTest`, `OfflineUuidTest`. Run via `./gradlew test`.

## CI / Release
- Tags `bat-*` or `v*` → GitHub Release with native installer (`.exe` jpackage on Windows, `.deb` on Linux)
- Branch `Estable` + `website/**` changes → Netlify deploy
- Supabase secrets (`SUPABASE_URL`, `SUPABASE_KEY`) injected as `secrets.properties` at build
- Version lives in `src/main/resources/version.properties`; CI strips `-alpha`/`-alfa` for jpackage
- Cloudflare Workers (`wrangler.jsonc`) serves `website/` as static assets with `nodejs_compat`

## Directory layout
```
src/main/java/com/experimento/launcher/
  Main.java              # entry (reflects into LauncherApp)
  LauncherApp.java       # monolithic JavaFX UI (all views in one class)
  LauncherMetadata.java  # VERSION reads from version.properties
  paths/LauncherDirectories.java  # data dirs under launcher-data/
  model/                 # LauncherProfile, ServerEntry, JvmPresetKind
  mojang/                # version resolver, game installer/launcher, NBT
  service/               # LauncherFacade + services (auto-opt, updates, crash reports, etc.)
  servers/               # servers.dat sync
  store/                 # Modrinth store client
  modloaders/            # Forge/Fabric/NeoForge installer
  agent/                 # LanguageFilterAgent (Java agent)
  util/                  # Hashing, OfflineUuid
src/main/resources/
  version.properties     # single source of truth for version
  META-INF/MANIFEST.MF   # Premain-Class: LanguageFilterAgent
  com/experimento/launcher/ui/meacore.css
src/test/java/com/experimento/launcher/
  servers/ServersDatServiceTest.java
  util/OfflineUuidTest.java
```

## Quirks & gotchas
- **Non-modular**: JVM needs `--add-opens=java.base/java.lang=ALL-UNNAMED` at runtime (already in CI jpackage config)
- **Java agent**: `LanguageFilterAgent` registered via MANIFEST.MF Premain-Class
- **Data dir**: `launcher-data/` is gitignored (created at runtime under `$PWD`); override via `-Dlauncher.root`
- **GTK WM_CLASS**: Linux-only system properties set in `Main.java` before any JavaFX class loads
- **Launcher stores profiles** as JSON in `launcher-data/profiles/`
- **Instance isolation**: Each profile has its own game dir; `useGlobalMinecraftFolder` flag for shared `~/.minecraft`
- **AutoUpdateService**: downloads installer from GitHub releases, writes a `.bat`/`.sh` that self-deletes (`del %~f0`)
- **LanguageFilterAgent**: strips non-EN/ES language files from Minecraft assets at class load time (via Java Instrumentation)

## Website
- Static site in `website/`, deployed to Netlify (from `Estable` branch) and Cloudflare Workers
- Local dev: `node server.js` (port 5000)
- No build step for the website itself

## VSCode hint
`java.configuration.updateBuildConfiguration` is set to `"interactive"` in `.vscode/settings.json`.
