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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

    /** Version folder names under {@code versions/} that contain {@code version.json} (installed). */
    public List<String> listInstalledVersionIds() throws Exception {
        dirs.ensureBaseDirs();
        Path vdir = dirs.versionsDir();
        if (!Files.isDirectory(vdir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(vdir)) {
            return stream
                    .filter(p -> Files.isRegularFile(p.resolve("version.json")))
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }
    }

    public void prepareInstance(LauncherProfile p, long ramMiB, Consumer<String> log) throws Exception {
        dirs.ensureBaseDirs();
        Path gameDir = gameDirFor(p);
        AutoOptimizerService.applyOptionsTxt(gameDir, p, ramMiB);
        ServersDatService.writeServers(gameDir, p.servers);
        log.accept("Instancia lista en: " + gameDir);
    }

    public List<String> buildLaunchCommand(LauncherProfile p, long ramMiB) throws Exception {
        Files.createDirectories(gameDirFor(p));
        String versionId = p.lastVersionId;
        Path mergedPath = dirs.versionsDir().resolve(versionId).resolve("version.json");
        if (!Files.isRegularFile(mergedPath)) {
            throw new IllegalStateException("Falta instalar la versión " + versionId + " (usa Instalar).");
        }
        JsonNode merged = mapper.readTree(Files.readAllBytes(mergedPath));
        if (!OfflineUuid.uuidMatchesUsername(p.username, p.offlineUuid)) {
            try {
                p.offlineUuid = OfflineUuid.toString(OfflineUuid.forUsername(p.username));
            } catch (Exception ignored) {
                p.username = "Player";
                p.offlineUuid = OfflineUuid.toString(OfflineUuid.forUsername("Player"));
            }
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
        prepareInstance(p, ramMiB, log);
        List<String> cmd = buildLaunchCommand(p, ramMiB);
        String joined = String.join(" ", cmd);
        log.accept(
                "Comando (inicio): "
                        + String.join(" ", cmd.subList(0, Math.min(6, cmd.size())))
                        + (cmd.size() > 6 ? " …" : ""));
        if (joined.length() > 16000) {
            log.accept("[Launcher] Comando completo (truncado): " + joined.substring(0, 16000) + "…");
        } else {
            log.accept("[Launcher] Comando completo: " + joined);
        }
        String javaBin = cmd.get(0);
        if (!"java".equals(javaBin) && !Files.exists(Path.of(javaBin))) {
            throw new IllegalStateException("Java no encontrado en: " + javaBin);
        }
        log.accept("[Launcher] Java: " + javaBin);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDirFor(p).toFile());
        log.accept("[Launcher] GameDir: " + pb.directory());
        log.accept("[Launcher] Versión: " + p.lastVersionId);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        proc.onExit()
                .thenAccept(
                        ended -> {
                            if (ended.exitValue() != 0) {
                                log.accept("[MC] Proceso terminó con código: " + ended.exitValue());
                            }
                        });

        Thread reader =
                new Thread(
                        () -> {
                            try (BufferedReader br =
                                    new BufferedReader(
                                            new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = br.readLine()) != null) {
                                    log.accept("[MC] " + line);
                                }
                            } catch (Exception ignored) {
                            }
                        },
                        "mc-log-reader");
        reader.setDaemon(true);
        reader.start();

        return proc;
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
