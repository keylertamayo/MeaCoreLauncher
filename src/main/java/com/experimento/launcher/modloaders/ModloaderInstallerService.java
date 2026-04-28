package com.experimento.launcher.modloaders;

import com.experimento.launcher.mojang.HttpFiles;
import com.experimento.launcher.service.JavaRuntimeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.function.Consumer;

public class ModloaderInstallerService {
    private static final ObjectMapper M = new ObjectMapper();

    /**
     * Instala Forge (legacy - 1.12.2 hasta 1.20.1).
     * Usa el JRE portátil del launcher si está disponible.
     */
    public static void installForge(String mcVersion, Path launcherDir, Consumer<String> logger) throws Exception {
        installForge(mcVersion, launcherDir, logger, null);
    }

    public static void installForge(String mcVersion, Path launcherDir, Consumer<String> logger, JavaRuntimeService runtime) throws Exception {
        logger.accept("[Forge] Resolviendo última versión de Forge para " + mcVersion + "...");

        byte[] promoBytes = HttpFiles.getBytes("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json");
        JsonNode promos = M.readTree(promoBytes);

        JsonNode promosObj = promos.path("promos");
        String forgeLatest = promosObj.path(mcVersion + "-recommended").asText(null);
        if (forgeLatest == null) {
            forgeLatest = promosObj.path(mcVersion + "-latest").asText(null);
        }

        if (forgeLatest == null) {
            throw new Exception("No se encontró Forge para la versión: " + mcVersion + ". Prueba con NeoForge para versiones 1.20.2+.");
        }

        String fullVersion = mcVersion + "-" + forgeLatest;
        logger.accept("[Forge] Versión elegida: " + fullVersion);

        String jarUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + fullVersion + "/forge-" + fullVersion + "-installer.jar";

        Path tempInstaller = Files.createTempFile("forge-installer-", ".jar");
        logger.accept("[Forge] Descargando instalador...");
        HttpFiles.downloadIfHashMismatch(jarUrl, tempInstaller, null);

        Path fakeProfile = launcherDir.resolve("launcher_profiles.json");
        if (!Files.exists(fakeProfile)) {
            Files.writeString(fakeProfile, "{ \"profiles\": {} }");
        }

        int requiredJava = resolveJavaVersionForMinecraft(mcVersion);
        String javaExe = resolveJavaExecutable(runtime, requiredJava);
        logger.accept("[Forge] Usando Java " + requiredJava + " para Minecraft " + mcVersion);
        logger.accept("[Forge] Inyectando Forge. ¡Paciencia, esto puede tomar unos minutos!");
        ProcessBuilder pb = new ProcessBuilder(javaExe, "-jar", tempInstaller.toAbsolutePath().toString(), "--installClient", launcherDir.toAbsolutePath().toString());
        pb.directory(tempInstaller.getParent().toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (Scanner s = new Scanner(p.getInputStream())) {
            while (s.hasNextLine()) {
                String line = s.nextLine();
                if (line.contains("Downloading") || line.contains("Extracting") || line.contains("Successfully") || line.contains("Installing")) {
                    logger.accept("[Forge-Bot] " + line);
                } else if (line.toLowerCase().contains("error") || line.toLowerCase().contains("exception") || line.contains("Failed")) {
                    logger.accept("[Forge-Error] " + line);
                }
            }
        }
        int exitCode = p.waitFor();
        Files.deleteIfExists(tempInstaller);

        if (exitCode != 0) {
            throw new Exception("El instalador de Forge falló (código " + exitCode + "). Asegúrate de instalar primero la versión vanilla y de tener acceso a internet.");
        }
        logger.accept("[Forge] ✅ Instalación de Forge completada.");
    }

    /**
     * Instala NeoForge (sucesor moderno de Forge para 1.20.2+).
     * Es el modloader recomendado para modpacks modernos como ATM9, Sky Odyssey, etc.
     */
    public static void installNeoForge(String mcVersion, Path launcherDir, Consumer<String> logger) throws Exception {
        installNeoForge(mcVersion, launcherDir, logger, null);
    }

    public static void installNeoForge(String mcVersion, Path launcherDir, Consumer<String> logger, JavaRuntimeService runtime) throws Exception {
        logger.accept("[NeoForge] Resolviendo última versión de NeoForge para Minecraft " + mcVersion + "...");

        String mcVersionShort = mcVersion.startsWith("1.") ? mcVersion.substring(2) : mcVersion;

        byte[] mavenMeta;
        try {
            mavenMeta = HttpFiles.getBytes(
                "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml");
        } catch (Exception e) {
            throw new Exception("No se pudo acceder a los servidores de NeoForge. Verifica tu conexión a internet.");
        }

        String metaStr = new String(mavenMeta);
        String[] lines = metaStr.split("\n");
        String latestVersion = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("<version>") && trimmed.contains(mcVersionShort + ".")) {
                latestVersion = trimmed.replace("<version>", "").replace("</version>", "").trim();
            }
        }

        if (latestVersion == null) {
            throw new Exception("No se encontró NeoForge para Minecraft " + mcVersion + ". NeoForge soporta 1.20.2 en adelante. Para versiones anteriores, usa Forge.");
        }

        logger.accept("[NeoForge] Versión seleccionada: " + latestVersion);

        String installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" +
                latestVersion + "/neoforge-" + latestVersion + "-installer.jar";

        Path tempInstaller = Files.createTempFile("neoforge-installer-", ".jar");
        logger.accept("[NeoForge] Descargando instalador NeoForge " + latestVersion + "...");
        HttpFiles.downloadIfHashMismatch(installerUrl, tempInstaller, null);

        Path fakeProfile = launcherDir.resolve("launcher_profiles.json");
        if (!Files.exists(fakeProfile)) {
            Files.writeString(fakeProfile, "{ \"profiles\": {} }");
        }

        int requiredJava = resolveJavaVersionForMinecraft(mcVersion);
        String javaExe = resolveJavaExecutable(runtime, requiredJava);
        logger.accept("[NeoForge] Usando Java " + requiredJava + " para Minecraft " + mcVersion);
        logger.accept("[NeoForge] Inyectando NeoForge. Puede tardar 2-5 minutos...");
        ProcessBuilder pb = new ProcessBuilder(
                javaExe, "-jar",
                tempInstaller.toAbsolutePath().toString(),
                "--installClient",
                launcherDir.toAbsolutePath().toString());
        pb.directory(tempInstaller.getParent().toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (Scanner s = new Scanner(p.getInputStream())) {
            while (s.hasNextLine()) {
                String line = s.nextLine();
                logger.accept("[NeoForge-Bot] " + line);
            }
        }
        int exitCode = p.waitFor();
        Files.deleteIfExists(tempInstaller);

        if (exitCode != 0) {
            throw new Exception("El instalador de NeoForge falló (código " + exitCode + "). Instala primero la versión vanilla.");
        }
        logger.accept("[NeoForge] ✅ NeoForge instalado correctamente. Recarga la lista de versiones.");
    }

    /**
     * Instala Fabric resolviendo dinámicamente la última versión del instalador.
     */
    public static void installFabric(String mcVersion, Path launcherDir, Consumer<String> logger) throws Exception {
        installFabric(mcVersion, launcherDir, logger, null);
    }

    public static void installFabric(String mcVersion, Path launcherDir, Consumer<String> logger, JavaRuntimeService runtime) throws Exception {
        logger.accept("[Fabric] Resolviendo última versión del instalador Fabric...");

        String installerVersion = resolveLatestFabricInstaller(logger);
        String jarUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/" +
                installerVersion + "/fabric-installer-" + installerVersion + ".jar";

        Path tempInstaller = Files.createTempFile("fabric-installer-", ".jar");
        logger.accept("[Fabric] Descargando instalador Fabric " + installerVersion + "...");
        HttpFiles.downloadIfHashMismatch(jarUrl, tempInstaller, null);

        logger.accept("[Fabric] Inyectando Fabric para Minecraft " + mcVersion + "...");
        int requiredJava = resolveJavaVersionForMinecraft(mcVersion);
        String javaExe = resolveJavaExecutable(runtime, requiredJava);
        logger.accept("[Fabric] Usando Java " + requiredJava + " para Minecraft " + mcVersion);
        ProcessBuilder pb = new ProcessBuilder(
                javaExe, "-jar",
                tempInstaller.toAbsolutePath().toString(),
                "client",
                "-mcversion", mcVersion,
                "-dir", launcherDir.toAbsolutePath().toString(),
                "-noprofile");
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (Scanner s = new Scanner(p.getInputStream())) {
            while (s.hasNextLine()) {
                logger.accept("[Fabric-Bot] " + s.nextLine());
            }
        }
        int exitCode = p.waitFor();
        Files.deleteIfExists(tempInstaller);

        if (exitCode != 0) {
            throw new Exception("El instalador de Fabric falló. Asegúrate de instalar la versión vanilla primero.");
        }
        logger.accept("[Fabric] ✅ Fabric instalado correctamente.");
    }

    private static String resolveLatestFabricInstaller(Consumer<String> logger) {
        try {
            byte[] meta = HttpFiles.getBytes(
                "https://maven.fabricmc.net/net/fabricmc/fabric-installer/maven-metadata.xml");
            String metaStr = new String(meta);
            String[] lines = metaStr.split("\n");
            String latest = null;
            for (String line : lines) {
                String t = line.trim();
                if (t.startsWith("<latest>")) {
                    latest = t.replace("<latest>", "").replace("</latest>", "").trim();
                    break;
                }
                if (t.startsWith("<version>")) {
                    latest = t.replace("<version>", "").replace("</version>", "").trim();
                }
            }
            if (latest != null) {
                logger.accept("[Fabric] Instalador más reciente: " + latest);
                return latest;
            }
        } catch (Exception e) {
            logger.accept("[Fabric] No se pudo resolver versión dinámica, usando 1.0.1 como fallback.");
        }
        return "1.0.1";
    }

    /**
     * Determina la versión de Java requerida según la versión de Minecraft.
     * - Minecraft 1.8-1.16 → Java 8
     * - Minecraft 1.17-1.20.4 → Java 17
     * - Minecraft 1.20.5+ → Java 21
     */
    private static int resolveJavaVersionForMinecraft(String mcVersion) {
        if (mcVersion.contains("1.20.5") || mcVersion.contains("1.20.6") || mcVersion.contains("1.21")) {
            return 21;
        } else if (mcVersion.contains("1.17") || mcVersion.contains("1.18") || mcVersion.contains("1.19") ||
                   mcVersion.contains("1.20.1") || mcVersion.contains("1.20.2") || mcVersion.contains("1.20.3") ||
                   mcVersion.contains("1.20.4")) {
            return 17;
        } else {
            // 1.16.5 y anteriores usan Java 8
            return 8;
        }
    }

    private static String resolveJavaExecutable(JavaRuntimeService runtime, int preferredVersion) {
        if (runtime != null) {
            java.nio.file.Path exe = runtime.getExecutable(preferredVersion);
            if (exe != null) return exe.toAbsolutePath().toString();
            // Fallbacks: si no hay Java 21, usar 17. Si no hay 8, intentar 17.
            if (preferredVersion == 21) {
                exe = runtime.getExecutable(17);
                if (exe != null) return exe.toAbsolutePath().toString();
            } else if (preferredVersion == 8) {
                exe = runtime.getExecutable(17);
                if (exe != null) {
                    System.out.println("[Java] Advertencia: Java 8 no encontrado, usando Java 17 como fallback");
                    return exe.toAbsolutePath().toString();
                }
            }
        }
        String home = System.getProperty("java.home");
        if (home != null) {
            java.nio.file.Path bin = java.nio.file.Path.of(home, "bin", "java");
            if (java.nio.file.Files.exists(bin)) return bin.toString();
        }
        return "java";
    }
}
