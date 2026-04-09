package com.experimento.launcher.mojang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedOutputStream;
import java.io.InputStream;
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
            if (downloads != null && downloads.has("artifact")) {
                JsonNode art = downloads.get("artifact");
                Path dest = librariesDir.resolve(art.get("path").asText());
                HttpFiles.downloadIfHashMismatch(art.get("url").asText(), dest, art.get("sha1").asText());
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
        int total = objects.size();
        AtomicInteger done = new AtomicInteger();
        progress.log("Sincronizando assets (" + total + " objetos)…");

        Path objectsDir = assetsDir.resolve("objects");
        Files.createDirectories(objectsDir);
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            objects.fields().forEachRemaining(e -> {
                String assetKey = e.getKey();
                
                // Filtrar idiomas (ahorrar espacio y tiempo)
                // Conservar solo español (es_*) y el bloque pack.mcmeta o fallback
                if (assetKey.startsWith("minecraft/lang/")) {
                    if (!assetKey.contains("/es_") && !assetKey.contains("/en_")) {
                        int c = done.incrementAndGet();
                        if (c % 500 == 0) {
                            progress.log("Assets: " + c + "/" + total);
                        }
                        return; // Omitir otros idiomas
                    }
                }

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
                                    if (c % 500 == 0) {
                                        progress.log("Assets: " + c + "/" + total);
                                    }
                                    return;
                                }
                            }
                        }
                        String url = RESOURCE_HOST + prefix + "/" + hash;
                        HttpFiles.downloadIfHashMismatch(url, dest, hash);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    int c = done.incrementAndGet();
                    if (c % 500 == 0) {
                        progress.log("Assets: " + c + "/" + total);
                    }
                }));
            });
            for (Future<?> f : futures) {
                f.get();
            }
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
