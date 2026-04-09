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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class LauncherFacade {

    private final LauncherDirectories dirs;
    private final ProfileStore profiles;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JavaRuntimeService runtimeService;

    public LauncherFacade(LauncherDirectories dirs) {
        this.dirs = dirs;
        this.profiles = new ProfileStore(dirs.launcherData());
        this.runtimeService = new JavaRuntimeService(dirs.launcherData());
    }

    public LauncherDirectories directories() {
        return dirs;
    }

    public ProfileStore profiles() {
        return profiles;
    }

    public JavaRuntimeService runtime() {
        return runtimeService;
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
        List<ManifestVersionEntry> mojangVersions = MojangVersionResolver.versionEntriesFromManifest(manifest);
        
        List<ManifestVersionEntry> allVersions = new java.util.ArrayList<>();
        if (Files.isDirectory(dirs.versionsDir())) {
            try (var stream = Files.list(dirs.versionsDir())) {
                stream.filter(Files::isDirectory).forEach(p -> {
                    String id = p.getFileName().toString();
                    if (Files.isRegularFile(p.resolve(id + ".json"))) {
                        if (mojangVersions.stream().noneMatch(v -> v.id().equals(id))) {
                            String type = "híbrido/custom";
                            try {
                                JsonNode localVer = mapper.readTree(p.resolve(id + ".json").toFile());
                                if (localVer.has("inheritsFrom")) {
                                    String inherits = localVer.get("inheritsFrom").asText().toLowerCase();
                                    if (id.toLowerCase().contains("forge") || inherits.contains("forge")) type = "forge";
                                    else if (id.toLowerCase().contains("fabric") || inherits.contains("fabric")) type = "fabric";
                                }
                            } catch (Exception ignored) {}
                            allVersions.add(new ManifestVersionEntry(id, type));
                        }
                    }
                });
            } catch (Exception ignored) {}
        }
        
        // Orden alfabético simple para las versiones custom al principio
        allVersions.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        allVersions.addAll(mojangVersions);
        
        return allVersions;
    }

    public void prepareInstance(LauncherProfile p, long ramMiB, Consumer<String> log) throws Exception {
        dirs.ensureBaseDirs();
        Path gameDir = gameDirFor(p);
        AutoOptimizerService.applyOptionsTxt(gameDir, p, ramMiB);
        ServersDatService.writeServers(gameDir, p.servers);
        log.accept("[LAUNCHER] Servidores sincronizados (" + (p.servers != null ? p.servers.size() : 0) + ")");
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
        
        // Fixes de compatibilidad de módulos para Java 17+
        jvm.add("-Djdk.module.illegalAccess.silent=true");
        jvm.add("-XX:+IgnoreUnrecognizedVMOptions");
        jvm.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        
        String effectiveJava = p.javaExecutable;
        
        // Detección inteligente de la versión de Java requerida
        if (effectiveJava == null || effectiveJava.isBlank()) {
            int requiredVer = getRequiredJavaVersion(merged, versionId);
            if (requiredVer == 8 || requiredVer == 17) {
                Path portable = runtimeService.getExecutable(requiredVer);
                if (portable != null) {
                    effectiveJava = portable.toAbsolutePath().toString();
                }
            }
        }

        var launcher = new GameLauncher(dirs.librariesDir(), dirs.assetsDir(), dirs.versionsDir());
        return launcher.buildCommand(
                merged,
                versionId,
                gameDir,
                p.username,
                p.offlineUuid,
                jvm,
                effectiveJava,
                LaunchFeatures.defaults());
    }

    private void enforceVersionIsolation(Path profileDir, String currentVersion, Consumer<String> log) {
        if (currentVersion == null || currentVersion.isBlank()) return;
        try {
            Path versionFile = profileDir.resolve(".meacore_version");
            Path modsDir = profileDir.resolve("mods");
            
            // Si el archivo de versión no existe pero hay mods, significa que venimos de una versión 
            // del launcher que no trackeaba aislamiento, o el usuario los puso a mano.
            // Backup preventivo radical.
            boolean versionMissing = !Files.exists(versionFile);
            String lastVersion = Files.exists(versionFile) ? Files.readString(versionFile).trim() : null;
            
            boolean versionChanged = lastVersion != null && !lastVersion.equals(currentVersion);
            boolean hasMods = Files.exists(modsDir) && isDirectoryNotEmpty(modsDir);

            if ((versionChanged || (versionMissing && hasMods))) {
                String reason = versionChanged ? "cambio de versión (" + lastVersion + " -> " + currentVersion + ")" 
                                               : "detección de mods sin trackear";
                
                log.accept("[LAUNCHER] 🛡️ Protector de Instancia: Detectado " + reason);
                log.accept("[LAUNCHER] Resguardando carpeta 'mods' para evitar crasheos...");
                
                String suffix = (lastVersion != null ? lastVersion : "untracked").replace(".", "_");
                Path backupDir = profileDir.resolve("mods_backup_" + suffix + "_" + System.currentTimeMillis());
                
                if (Files.exists(modsDir)) {
                    Files.move(modsDir, backupDir);
                    log.accept("[LAUNCHER] ✅ Mods antiguos movidos a: " + backupDir.getFileName());
                }
                Files.createDirectories(modsDir);
            }
            
            // Validación extra de "intrusos": Si hay un mod de v1.20 en una v1.12
            validateModsForVersion(modsDir, currentVersion, log);

            Files.writeString(versionFile, currentVersion);
        } catch (Exception e) {
            log.accept("[LAUNCHER] ⚠️ Error en aislamiento de mods: " + e.getMessage());
        }
    }

    private void validateModsForVersion(Path modsDir, String mcVersion, Consumer<String> log) {
        if (!Files.exists(modsDir)) return;
        try (var stream = Files.list(modsDir)) {
            stream.filter(p -> p.toString().toLowerCase().endsWith(".jar")).forEach(p -> {
                String name = p.getFileName().toString().toLowerCase();
                // Reglas simples pero efectivas basadas en nombres comunes
                boolean incompatible = false;
                if (mcVersion.contains("1.12") && (name.contains("1.18") || name.contains("1.19") || name.contains("1.20") || name.contains("1.21"))) {
                    incompatible = true;
                } else if (mcVersion.contains("1.20") && (name.contains("1.12") || name.contains("1.8"))) {
                    incompatible = true;
                }
                
                if (incompatible) {
                    try {
                        Path incompatibleDir = modsDir.resolve("incompatible_detectado");
                        Files.createDirectories(incompatibleDir);
                        Files.move(p, incompatibleDir.resolve(p.getFileName()));
                        log.accept("[LAUNCHER] 🚫 Mod incompatible detectado y movido: " + p.getFileName());
                    } catch (IOException ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    private boolean isDirectoryNotEmpty(Path path) throws IOException {
        if (!Files.isDirectory(path)) return false;
        try (var stream = Files.list(path)) {
            return stream.findAny().isPresent();
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
        }
    }

    private int getRequiredJavaVersion(JsonNode merged, String versionId) {
        if (merged.has("javaVersion")) {
            return merged.get("javaVersion").path("majorVersion").asInt(0);
        }
        
        String mainClass = merged.path("mainClass").asText("").toLowerCase();
        if (mainClass.contains("launchwrapper") || mainClass.contains("net.minecraft.launchwrapper.launch") || versionId.contains("1.12.2")) {
            return 8;
        }

        // Fallback para versiones que requieren Java 17 por compatibilidad de mods (1.17 - 1.20.4)
        if (versionId.contains("1.17") || versionId.contains("1.18") || versionId.contains("1.19") || versionId.contains("1.20.1") || versionId.contains("1.20.2") || versionId.contains("1.20.4")) {
            return 17;
        }
        
        return 0; // Usar el del sistema
    }

    public Process startGame(LauncherProfile p, long ramMiB, Consumer<String> log) throws Exception {
        Path profileDir = gameDirFor(p);
        enforceVersionIsolation(profileDir, p.lastVersionId, log);

        // Alerta informativa sobre RAM total (sin bloqueos ni límites forzados)
        try {
            var hw = SystemInfoService.getInfo();
            long totalMiB = hw.totalRamBytes() / (1024 * 1024);
            long availableMiB = hw.availableRamBytes() / (1024 * 1024);
            
            if (ramMiB > totalMiB) {
                log.accept("[LAUNCHER] ADVERTENCIA: Asignados " + ramMiB + "MB. RAM Total: " + totalMiB + "MB. (Posible crasheo)");
            }
            if (availableMiB < 1024) {
                log.accept("[LAUNCHER] CRÍTICO: Tienes solo " + availableMiB + "MB libres. MeaCore recomienda cerrar aplicaciones para evitar cierres inesperados.");
            } else if (ramMiB > availableMiB) {
                log.accept("[LAUNCHER] OJO: Tienes solo " + availableMiB + "MB libres. Recomendamos cerrar otras apps.");
            }
        } catch (Exception ignored) {}

        Path versionJar = dirs.versionsDir().resolve(p.lastVersionId).resolve(p.lastVersionId + ".jar");
        if (!Files.exists(versionJar)) {
            log.accept("[LAUNCHER] ❌ La versión " + p.lastVersionId + " no está instalada. Haz clic en 'Instalar' primero.");
            throw new IllegalStateException("La versión " + p.lastVersionId + " no está instalada.");
        }

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
                    String prefix = "";
                    if (captured.contains("WARN")) prefix = "⚠️ ";
                    else if (captured.contains("ERROR") || captured.contains("FATAL") || captured.contains("Exception")) prefix = "❌ ";
                    else if (captured.contains("INFO")) prefix = "ℹ️ ";
                    
                    log.accept("[GAME] " + prefix + captured);
                }
            } catch (Exception e) {
                log.accept("[LAUNCHER] Error leyendo consola del juego: " + e.getMessage());
            }
        }).start();

        return process;
    }

    public void fullDeleteProfile(LauncherProfile p, List<LauncherProfile> allProfiles) throws Exception {
        // 1. Quitar de la lista y persistir en el JSON
        allProfiles.remove(p);
        profiles.save(allProfiles);

        // 2. Borrar permanentemente los archivos físicos si es una instancia aislada
        if (!p.useGlobalMinecraftFolder) {
            Path gameDir = gameDirFor(p);
            if (Files.exists(gameDir)) {
                deleteDirectoryRecursively(gameDir);
            }
        }
    }

    private void deleteDirectoryRecursively(Path path) throws Exception {
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .map(Path::toFile)
                  .forEach(java.io.File::delete);
        }
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
