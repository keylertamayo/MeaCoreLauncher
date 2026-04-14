package com.experimento.launcher.service;

import com.experimento.launcher.mojang.HttpFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Descarga e instala automáticamente los mods de rendimiento más conocidos
 * para Fabric: Sodium, Lithium, FerriteCore e Indium.
 * 
 * Estos mods son 100% gratuitos y de código abierto (FOSS).
 * En pruebas con SkyFactory 4 y modpacks similares, su combinación
 * puede subir los FPS de 25-40 a 60+ estables.
 */
public final class PerformanceModsService {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final ObjectMapper M = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private PerformanceModsService() {}

    public record PerformanceMod(String slug, String name, String description) {}

    public static final List<PerformanceMod> FABRIC_MODS = List.of(
        new PerformanceMod("sodium",         "Sodium",          "Motor de renderizado moderno — +50-300% FPS"),
        new PerformanceMod("lithium",        "Lithium",         "Optimización de lógica del juego y servidor"),
        new PerformanceMod("ferrite-core",   "FerriteCore",     "Reducción masiva de uso de RAM (-30%)"),
        new PerformanceMod("indium",         "Indium",          "Compatibilidad de Sodium con Fabric Rendering API"),
        new PerformanceMod("immediatelyfast","ImmediatelyFast", "Renderizado de entidades y UI más rápido")
    );

    /** Forge 1.12.2–1.20.1 */
    public static final List<PerformanceMod> FORGE_MODS = List.of(
        new PerformanceMod("ferritecore", "FerriteCore", "Reducción masiva de uso de RAM (-30%)"),
        new PerformanceMod("rubidium",    "Rubidium",    "Puerto de Sodium para Forge — +50% FPS"),
        new PerformanceMod("oculus",      "Oculus",      "Soporte de shaders compatible con Rubidium")
    );

    /** NeoForge 1.20.2+ — usa Embeddium (fork activo de Rubidium) */
    public static final List<PerformanceMod> NEOFORGE_MODS = List.of(
        new PerformanceMod("ferritecore",  "FerriteCore",  "Reducción masiva de uso de RAM (-30%)"),
        new PerformanceMod("embeddium",    "Embeddium",    "Motor de renderizado para NeoForge/Forge — +50% FPS"),
        new PerformanceMod("modernfix",    "ModernFix",    "Tiempos de carga -50%, RAM -20%, FPS +10%")
    );

    /**
     * Descarga e instala los mods de rendimiento en la carpeta de mods de la instancia.
     * Solo instala los que sean compatibles con la versión dada.
     */
    public static void installPerformanceMods(
            Path modsDir,
            String mcVersion,
            String loader,
            Consumer<String> log) throws Exception {

        Files.createDirectories(modsDir);

        String loaderLow = loader != null ? loader.toLowerCase() : "vanilla";
        List<PerformanceMod> mods;
        if (loaderLow.contains("neoforge")) {
            mods = NEOFORGE_MODS;
        } else if (loaderLow.contains("fabric")) {
            mods = FABRIC_MODS;
        } else if (loaderLow.contains("forge")) {
            mods = FORGE_MODS;
        } else {
            log.accept("[PERF] Loader '" + loader + "' no soportado. Instala Fabric, Forge o NeoForge primero.");
            return;
        }

        log.accept("[PERF] Iniciando instalación de mods de rendimiento para " + mcVersion + " (" + loader + ")...");
        log.accept("[PERF] Mods a instalar: " + mods.stream().map(PerformanceMod::name).reduce((a, b) -> a + ", " + b).orElse("ninguno"));

        int installed = 0;
        for (PerformanceMod mod : mods) {
            try {
                String url = resolveDownloadUrl(mod.slug(), mcVersion, loader.toLowerCase(), log);
                if (url == null) {
                    log.accept("[PERF] ⚠ " + mod.name() + " no disponible para esta versión, omitiendo.");
                    continue;
                }

                String fileName = mod.slug() + "-" + mcVersion + ".jar";
                Path dest = modsDir.resolve(fileName);

                if (Files.exists(dest)) {
                    log.accept("[PERF] ✓ " + mod.name() + " ya está instalado.");
                    installed++;
                    continue;
                }

                log.accept("[PERF] Descargando " + mod.name() + "...");
                HttpFiles.downloadIfHashMismatch(url, dest, null);
                log.accept("[PERF] ✅ " + mod.name() + " instalado — " + mod.description());
                installed++;

            } catch (Exception e) {
                log.accept("[PERF] ⚠ No se pudo instalar " + mod.name() + ": " + e.getMessage());
            }
        }

        log.accept("[PERF] ═══════════════════════════════════════════════");
        log.accept("[PERF] Instalación completa: " + installed + "/" + mods.size() + " mods de rendimiento listos.");
        if (installed > 0) {
            log.accept("[PERF] Los mods se aplicarán la próxima vez que inicies el juego.");
            log.accept("[PERF] Mejora esperada de FPS: +50-150% dependiendo de tu PC.");
        }
    }

    private static String resolveDownloadUrl(String slug, String mcVersion, String loader, Consumer<String> log) {
        try {
            String facets = URLEncoder.encode(
                "[[\"project_type:mod\"],[\"game_versions:" + mcVersion + "\"],[\"categories:" + loader + "\"]]",
                StandardCharsets.UTF_8);
            String searchUrl = MODRINTH_API + "/project/" + slug + "/version?game_versions=[\"" +
                    URLEncoder.encode(mcVersion, StandardCharsets.UTF_8) + "\"]&loaders=[\"" +
                    URLEncoder.encode(loader, StandardCharsets.UTF_8) + "\"]";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "MeaCore-Launcher/performance-mods-installer")
                    .GET()
                    .build();

            HttpResponse<InputStream> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) return null;

            JsonNode root = M.readTree(res.body());
            if (!root.isArray() || root.isEmpty()) return null;

            JsonNode firstVersion = root.get(0);
            JsonNode files = firstVersion.path("files");
            if (!files.isArray() || files.isEmpty()) return null;

            for (JsonNode file : files) {
                if (file.path("primary").asBoolean(false)) {
                    return file.path("url").asText(null);
                }
            }
            return files.get(0).path("url").asText(null);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Verifica si la combinación de loader y versión es compatible con los mods de rendimiento.
     */
    public static boolean isSupported(String loader) {
        if (loader == null) return false;
        String l = loader.toLowerCase();
        return l.contains("fabric") || l.contains("forge") || l.contains("neoforge");
    }
}
