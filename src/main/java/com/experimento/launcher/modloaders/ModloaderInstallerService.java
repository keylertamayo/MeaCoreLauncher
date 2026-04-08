package com.experimento.launcher.modloaders;

import com.experimento.launcher.mojang.HttpFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.function.Consumer;

public class ModloaderInstallerService {
    private static final ObjectMapper M = new ObjectMapper();

    public static void installForge(String mcVersion, Path launcherDir, Consumer<String> logger) throws Exception {
        logger.accept("[Forge] Resolviendo última versión de Forge para " + mcVersion + "...");
        
        // 1. Obtener json de promociones
        byte[] promoBytes = HttpFiles.getBytes("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json");
        JsonNode promos = M.readTree(promoBytes);
        
        JsonNode promosObj = promos.path("promos");
        String forgeLatest = promosObj.path(mcVersion + "-latest").asText(null);
        if (forgeLatest == null) {
            // Intento con "recommended" si no hay latest
            forgeLatest = promosObj.path(mcVersion + "-recommended").asText(null);
        }
        
        if (forgeLatest == null) {
            throw new Exception("No se encontró Forge para la versión: " + mcVersion);
        }
        
        String fullVersion = mcVersion + "-" + forgeLatest;
        logger.accept("[Forge] Versión elegida: " + fullVersion);
        
        // 2. Construir URL de descarga
        String jarUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + fullVersion + "/forge-" + fullVersion + "-installer.jar";
        
        // 3. Descargar temporalmente el instalador
        Path tempInstaller = Files.createTempFile("forge-installer-", ".jar");
        logger.accept("[Forge] Descargando instalador...");
        HttpFiles.downloadIfHashMismatch(jarUrl, tempInstaller, null);
        
        // 4. Asegurar que exista un launcher_profiles.json artificial porque la validación de Forge es muy estricta
        Path fakeProfile = launcherDir.resolve("launcher_profiles.json");
        if (!Files.exists(fakeProfile)) {
            Files.writeString(fakeProfile, "{ \"profiles\": {} }");
        }

        // 5. Ejecutar el instalador en modo headless
        logger.accept("[Forge] Inyectando Forge en el perfil. ¡Paciencia, esto puede tomar unos minutos!");
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", tempInstaller.toAbsolutePath().toString(), "--installClient", launcherDir.toAbsolutePath().toString());
        pb.directory(tempInstaller.getParent().toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        
        try (Scanner s = new Scanner(p.getInputStream())) {
            while (s.hasNextLine()) {
                String line = s.nextLine();
                if (line.contains("Downloading") || line.contains("Extracting") || line.contains("Successfully")) {
                    logger.accept("[Forge-Bot] " + line);
                } else if (line.toLowerCase().contains("error") || line.toLowerCase().contains("exception") || line.contains("Failed")) {
                    logger.accept("[Forge-Error] " + line);
                }
            }
        }
        int exitCode = p.waitFor();
        Files.deleteIfExists(tempInstaller);
        
        if (exitCode != 0) {
            throw new Exception("El instalador de Forge falló con código " + exitCode + ". Asegúrate de descargar e iniciar la versión vanilla primero.");
        }
        logger.accept("[Forge] Instalación completada con éxito.");
    }

    public static void installFabric(String mcVersion, Path launcherDir, Consumer<String> logger) throws Exception {
        logger.accept("[Fabric] Descargando el último instalador de Fabric...");
        
        String jarUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar"; 
        // 1.0.1 es una versión sólida, pero idealmente resolvemos la última. Por ahora hardcodeamos el instalador que es universal.
        
        Path tempInstaller = Files.createTempFile("fabric-installer-", ".jar");
        HttpFiles.downloadIfHashMismatch(jarUrl, tempInstaller, null);
        
        logger.accept("[Fabric] Inyectando Fabric para la versión " + mcVersion);
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", tempInstaller.toAbsolutePath().toString(), "client", "-mcversion", mcVersion, "-dir", launcherDir.toAbsolutePath().toString(), "-noprofile");
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
        logger.accept("[Fabric] Instalación completada con éxito.");
    }
}
