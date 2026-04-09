package com.experimento.launcher.store;

import com.experimento.launcher.mojang.HttpFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class StoreDownloader {

    private static final ObjectMapper M = new ObjectMapper();

    public static void install(StoreItem item, Path gameDir, String mcVersion, Consumer<String> progress) throws Exception {
        Files.createDirectories(gameDir);

        progress.accept("Obteniendo URL de descarga para " + item.title() + "...");
        
        String loader = "";
        if (item.category() == StoreCategory.MOD || item.category() == StoreCategory.MODPACK) {
            loader = "forge"; // Idealmente esto se lee del perfil actual o se pide, por defecto probamos forge
        }

        String url = ModrinthStoreClient.getDownloadUrl(item.id(), mcVersion, loader);
        if (url == null && "forge".equals(loader)) {
            // Reintento con fabric
            url = ModrinthStoreClient.getDownloadUrl(item.id(), mcVersion, "fabric");
        }

        if (url == null) {
            // Intento genérico sin loader
            url = ModrinthStoreClient.getDownloadUrl(item.id(), null, null);
        }

        if (url == null) {
            throw new Exception("No se encontró una versión compatible para " + mcVersion);
        }

        String fileName = url.substring(url.lastIndexOf('/') + 1);
        Path destFile;

        switch (item.category()) {
            case MOD:
                destFile = gameDir.resolve("mods").resolve(fileName);
                break;
            case RESOURCEPACK:
                destFile = gameDir.resolve("resourcepacks").resolve(fileName);
                break;
            case SHADERPACK:
                destFile = gameDir.resolve("shaderpacks").resolve(fileName);
                break;
            case MODPACK:
                destFile = gameDir.resolve(fileName); // Temp mrpack
                break;
            default:
                destFile = gameDir.resolve("downloads").resolve(fileName);
                break;
        }

        Files.createDirectories(destFile.getParent());
        progress.accept("Descargando " + fileName + "...");
        
        HttpFiles.downloadIfHashMismatch(url, destFile, null);

        if (item.category() == StoreCategory.MODPACK && fileName.endsWith(".mrpack")) {
            progress.accept("Instalando modpack...");
            installMrPack(destFile, gameDir, progress);
            Files.deleteIfExists(destFile);
        }

        progress.accept("✅ Instalado exitosamente.");
    }

    private static void installMrPack(Path mrPackPath, Path gameDir, Consumer<String> progress) throws Exception {
        // Descomprimir overrides y parsear index json
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(mrPackPath))) {
            ZipEntry e;
            JsonNode index = null;

            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();

                if (name.equals("modrinth.index.json")) {
                    index = M.readTree(zis);
                } else if (name.startsWith("overrides/")) {
                    Path dest = gameDir.resolve(name.substring("overrides/".length()));
                    Files.createDirectories(dest.getParent());
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            if (index != null) {
                JsonNode files = index.path("files");
                int total = files.size();
                int current = 0;
                for (JsonNode file : files) {
                    current++;
                    String pathStr = file.path("path").asText();
                    String downloadUrl = file.path("downloads").get(0).asText(); // Asumimos al menos 1 url

                    Path out = gameDir.resolve(pathStr);
                    Files.createDirectories(out.getParent());

                    progress.accept("Descargando archivo " + current + "/" + total + " (" + out.getFileName() + ")...");
                    HttpFiles.downloadIfHashMismatch(downloadUrl, out, null);
                }
            }
        }
    }
}
