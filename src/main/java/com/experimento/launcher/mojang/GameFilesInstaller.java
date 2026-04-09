package com.experimento.launcher.mojang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GameFilesInstaller {

    private static final ObjectMapper M = new ObjectMapper();
    private static final String RESOURCE_HOST = "https://resources.download.minecraft.net/";

    private final Path librariesDir;
    private final Path assetsDir;
    private final Path versionsDir;
    private final OsContext os = OsContext.current();

    public GameFilesInstaller(Path librariesDir, Path assetsDir, Path versionsDir) {
        this.librariesDir = librariesDir;
        this.assetsDir = assetsDir;
        this.versionsDir = versionsDir;
    }

    public JsonNode installVersion(String versionId, MojangVersionResolver resolver, ProgressSink progress)
            throws Exception {
        progress.log("Resolviendo versión " + versionId + "…");
        JsonNode merged = resolver.resolveMerged(versionId);
        Path versionRoot = versionsDir.resolve(versionId);
        Files.createDirectories(versionRoot);

        progress.log("Descargando cliente…");
        JsonNode client = merged.get("downloads").get("client");
        Path clientJar = versionRoot.resolve(versionId + ".jar");
        HttpFiles.downloadIfHashMismatch(
                client.get("url").asText(), clientJar, client.get("sha1").asText());

        progress.log("Descargando bibliotecas…");
        Path nativesDir = versionRoot.resolve("natives");
        Files.createDirectories(nativesDir);
        for (JsonNode lib : merged.get("libraries")) {
            if (!RuleEvaluator.libraryAllowed(lib, os)) {
                continue;
            }
            JsonNode downloads = lib.get("downloads");
            boolean downloaded = false;
            
            if (downloads != null && downloads.has("artifact")) {
                JsonNode art = downloads.get("artifact");
                Path dest = librariesDir.resolve(art.get("path").asText());
                HttpFiles.downloadIfHashMismatch(art.get("url").asText(), dest, art.get("sha1").asText());
                downloaded = true;
            } 
            
            // Fallback: Si no se descargó (no hay artifact.url), probar por nombre Maven
            if (!downloaded && lib.has("name")) {
                downloadMavenLibrary(lib.get("name").asText());
            }

            if (downloads != null && downloads.has("classifiers")) {
                JsonNode cls = downloads.get("classifiers");
                String key = pickNativeClassifier(cls);
                if (key != null && cls.has(key)) {
                    JsonNode nat = cls.get(key);
                    Path zipPath = librariesDir.resolve(nat.get("path").asText());
                    HttpFiles.downloadIfHashMismatch(nat.get("url").asText(), zipPath, nat.get("sha1").asText());
                    extractNatives(zipPath, nativesDir);
                }
            } else if (lib.has("name") && lib.get("name").asText().contains("text2speech")) {
                // Fix para el Narrador en Linux: text2speech no tiene classifier pero contiene .so
                JsonNode art = downloads.get("artifact");
                Path jarPath = librariesDir.resolve(art.get("path").asText());
                if (Files.exists(jarPath)) extractNatives(jarPath, nativesDir);
            }
        }
        progress.log("Descargando índice de assets…");
        JsonNode assetIndex = merged.get("assetIndex");
        Path indexesDir = assetsDir.resolve("indexes");
        Files.createDirectories(indexesDir);
        Path indexFile = indexesDir.resolve(assetIndex.get("id").asText() + ".json");
        HttpFiles.downloadIfHashMismatch(
                assetIndex.get("url").asText(), indexFile, assetIndex.get("sha1").asText());

        JsonNode indexJson = M.readTree(Files.readAllBytes(indexFile));
        JsonNode objects = indexJson.get("objects");
        
        // Ghosting: Eliminar idiomas no deseados del índice para que Minecraft ni los vea
        if (objects instanceof com.fasterxml.jackson.databind.node.ObjectNode objNode) {
            java.util.List<String> toRemove = new java.util.ArrayList<>();
            objNode.fieldNames().forEachRemaining(key -> {
                if (key.startsWith("minecraft/lang/")) {
                    boolean keep = key.contains("/en_us") || key.contains("/en_gb")
                               || key.contains("/es_ar") || key.contains("/es_cl")
                               || key.contains("/es_ec") || key.contains("/es_es")
                               || key.contains("/es_mx") || key.contains("/es_uy")
                               || key.contains("/es_ve");
                    if (!keep) toRemove.add(key);
                }
            });
            toRemove.forEach(objNode::remove);
            M.writeValue(indexFile.toFile(), indexJson);
            if (!toRemove.isEmpty()) progress.log("Deep Clean: Ocultados " + toRemove.size() + " idiomas del menú.");
        }

        int total = objects.size();
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        progress.log("Sincronizando assets (" + total + " objetos)…");

        Path objectsDir = assetsDir.resolve("objects");
        Files.createDirectories(objectsDir);
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        try (java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            objects.fields().forEachRemaining(e -> {
                JsonNode h = e.getValue();
                String hash = h.get("hash").asText();
                String prefix = hash.substring(0, 2);
                Path dest = objectsDir.resolve(prefix).resolve(hash);
                futures.add(pool.submit(() -> {
                    try {
                        if (Files.exists(dest)) {
                            try (InputStream in = Files.newInputStream(dest)) {
                                String got = com.experimento.launcher.util.Hashing.sha1Hex(in);
                                if (hash.equalsIgnoreCase(got)) {
                                    int c = done.incrementAndGet();
                                    if (c % 500 == 0) progress.log("Assets: " + c + "/" + total);
                                    return;
                                }
                            }
                        }
                        String url = RESOURCE_HOST + prefix + "/" + hash;
                        HttpFiles.downloadIfHashMismatch(url, dest, hash);
                        int c = done.incrementAndGet();
                        if (c % 500 == 0) progress.log("Assets: " + c + "/" + total);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }));
            });
            for (java.util.concurrent.Future<?> f : futures) {
                f.get();
            }
        } catch (Exception e) {
            progress.log("[ERROR] Error en descarga de assets: " + e.getMessage());
        }

        // Deep Clean: Borrar idiomas no filtrados que ya existen
        try {
            cleanupUnneededAssets(objects, progress);
        } catch (Exception e) {
            progress.log("[ADVERTENCIA] No se pudo completar la limpieza profunda: " + e.getMessage());
        }

        progress.log("Descargando configuración de logging…");
        JsonNode logging = merged.path("logging").path("client").path("file");
        if (!logging.isMissingNode() && logging.has("url")) {
            Path logCfg = versionRoot.resolve(logging.get("id").asText());
            HttpFiles.downloadIfHashMismatch(
                    logging.get("url").asText(), logCfg, logging.path("sha1").asText(null));
        }

        progress.log("Instalación de " + versionId + " completada.");
        return merged;
    }

    private void cleanupUnneededAssets(JsonNode objects, ProgressSink progress) throws IOException {
        Path objectsDir = assetsDir.resolve("objects");
        if (!Files.exists(objectsDir)) return;

        objects.fields().forEachRemaining(e -> {
            String key = e.getKey();
            if (key.startsWith("minecraft/lang/")) {
                boolean keep = key.contains("/en_us") || key.contains("/en_gb")
                           || key.contains("/es_ar") || key.contains("/es_cl")
                           || key.contains("/es_ec") || key.contains("/es_es")
                           || key.contains("/es_mx") || key.contains("/es_uy")
                           || key.contains("/es_ve");
                if (!keep) {
                    String hash = e.getValue().get("hash").asText();
                    Path file = objectsDir.resolve(hash.substring(0, 2)).resolve(hash);
                    try {
                        if (Files.exists(file)) Files.delete(file);
                    } catch (IOException ignored) {}
                }
            }
        });
    }

    private void downloadMavenLibrary(String name) {
        String path = nameToPath(name);
        Path dest = librariesDir.resolve(path);
        if (Files.exists(dest)) return;

        String[] mirrors = {
            "https://libraries.minecraft.net/",
            "https://maven.minecraftforge.net/"
        };

        for (String mirror : mirrors) {
            try {
                HttpFiles.downloadIfHashMismatch(mirror + path, dest, null);
                if (Files.exists(dest)) break;
            } catch (Exception ignored) {}
        }
    }

    private static String nameToPath(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) return name.replace(":", "_") + ".jar";
        String group = parts[0].replace(".", "/");
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? parts[3] : null;
        String filename = artifact + "-" + version + (classifier != null ? "-" + classifier : "") + ".jar";
        return group + "/" + artifact + "/" + version + "/" + filename;
    }

    private String pickNativeClassifier(JsonNode classifiers) {
        String primary = os.nativeClassifier();
        if (classifiers.has(primary)) {
            return primary;
        }
        if ("osx".equals(os.name()) && classifiers.has("natives-macos")) {
            return "natives-macos";
        }
        return null;
    }

    private static void extractNatives(Path zipFile, Path nativesDir) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName();
                if (name.endsWith(".gitignore") || name.endsWith(".sha1")) {
                    continue;
                }
                if (name.contains("META-INF")) {
                    continue;
                }
                if (name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")) {
                    Path out = nativesDir.resolve(name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name);
                    Files.createDirectories(out.getParent());
                    try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(out))) {
                        zin.transferTo(bos);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface ProgressSink {
        void log(String line);
    }
}
