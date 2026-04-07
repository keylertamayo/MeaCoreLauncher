package com.experimento.launcher.service;

import com.experimento.launcher.mojang.GameFilesInstaller;
import com.experimento.launcher.mojang.GameLauncher;
import com.experimento.launcher.mojang.LaunchFeatures;
import com.experimento.launcher.mojang.ManifestVersionEntry;
import com.experimento.launcher.mojang.MojangVersionResolver;
import com.experimento.launcher.model.LauncherProfile;
import com.experimento.launcher.paths.LauncherDirectories;
import com.experimento.launcher.servers.ServersDatService;
import com.experimento.launcher.util.OfflineUuid;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class LauncherFacade {

    private final LauncherDirectories dirs;
    private final ProfileStore profiles;
    private final ObjectMapper mapper = new ObjectMapper();

    public LauncherFacade(LauncherDirectories dirs) {
        this.dirs = dirs;
        this.profiles = new ProfileStore(dirs.launcherData());
    }

    public LauncherDirectories directories() {
        return dirs;
    }

    public ProfileStore profiles() {
        return profiles;
    }

    public Path gameDirFor(LauncherProfile p) {
        if (p.useGlobalMinecraftFolder) {
            return Path.of(System.getProperty("user.home"), ".minecraft");
        }
        return dirs.instanceGameDir(p.instanceId);
    }

    public void installVersion(String versionId, Consumer<String> log) throws Exception {
        dirs.ensureBaseDirs();
        var resolver = new MojangVersionResolver(dirs.versionsDir());
        var installer =
                new GameFilesInstaller(dirs.librariesDir(), dirs.assetsDir(), dirs.versionsDir());
        installer.installVersion(versionId, resolver, log::accept);
    }

    public List<ManifestVersionEntry> fetchManifestVersions() throws Exception {
        dirs.ensureBaseDirs();
        var resolver = new MojangVersionResolver(dirs.versionsDir());
        JsonNode manifest = resolver.loadManifest();
        return MojangVersionResolver.versionEntriesFromManifest(manifest);
    }

    public void prepareInstance(LauncherProfile p, long ramMiB, Consumer<String> log) throws Exception {
        dirs.ensureBaseDirs();
        Path gameDir = gameDirFor(p);
        AutoOptimizerService.applyOptionsTxt(gameDir, p, ramMiB);
        ServersDatService.writeServers(gameDir, p.servers);
        log.accept("Instancia lista en: " + gameDir);
    }

    public List<String> buildLaunchCommand(LauncherProfile p, long ramMiB) throws Exception {
        String versionId = p.lastVersionId;
        Path mergedPath = dirs.versionsDir().resolve(versionId).resolve("version.json");
        if (!Files.isRegularFile(mergedPath)) {
            throw new IllegalStateException("Falta instalar la versión " + versionId + " (usa Instalar).");
        }
        JsonNode merged = mapper.readTree(Files.readAllBytes(mergedPath));
        if (!OfflineUuid.uuidMatchesUsername(p.username, p.offlineUuid)) {
            throw new IllegalStateException(
                    "El UUID offline no coincide con el nombre; renombrar puede romper datos en servidor.");
        }
        Path gameDir = gameDirFor(p);
        List<String> jvm = JvmPresetService.argsFor(p, ramMiB);
        var launcher = new GameLauncher(dirs.librariesDir(), dirs.assetsDir(), dirs.versionsDir());
        return launcher.buildCommand(
                merged,
                versionId,
                gameDir,
                p.username,
                p.offlineUuid,
                jvm,
                LaunchFeatures.defaults());
    }

    public Process startGame(LauncherProfile p, long ramMiB, Consumer<String> log) throws Exception {
        // Alerta informativa sobre RAM total (sin bloqueos ni límites forzados)
        try {
            var hw = SystemInfoService.getInfo();
            long totalMiB = hw.totalRamBytes() / (1024 * 1024);
            if (ramMiB > totalMiB) {
                log.accept("[LAUNCHER] ADVERTENCIA: Asignados " + ramMiB + "MB. RAM Total: " + totalMiB + "MB. (Posible crasheo)");
            }
        } catch (Exception ignored) {}

        prepareInstance(p, ramMiB, log);
        List<String> cmd = buildLaunchCommand(p, ramMiB);
        log.accept(String.join(" ", cmd.subList(0, Math.min(6, cmd.size()))) + " …");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDirFor(p).toFile());
        // Redirigir errorStream al inputStream para leer todo en un solo hilo
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // Hilo de lectura de consola del juego
        new Thread(() -> {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String captured = line;
                    log.accept("[GAME] " + captured);
                }
            } catch (Exception e) {
                log.accept("[LAUNCHER] Error leyendo consola del juego: " + e.getMessage());
            }
        }).start();

        return process;
    }

    /** Apply TLauncher JVM args as custom once (user can edit after). */
    public static void maybeImportTlauncherJvm(LauncherProfile p) {
        if (p.customJvmArgs != null && !p.customJvmArgs.isBlank()) {
            return;
        }
        String s = TlauncherConfigReader.suggestedJvmArgsFromTlauncher();
        if (!s.isBlank()) {
            p.customJvmArgs = s;
        }
    }
}
