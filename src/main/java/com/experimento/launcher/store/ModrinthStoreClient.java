package com.experimento.launcher.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModrinthStoreClient {

    private static final String BASE_URL = "https://api.modrinth.com/v2";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper M = new ObjectMapper();

    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    private record CacheEntry(long timestamp, List<StoreItem> items) {}

    private static void evictExpired() {
        long now = System.currentTimeMillis();
        CACHE.entrySet().removeIf(e -> (now - e.getValue().timestamp) > CACHE_TTL_MS);
    }

    public static List<StoreItem> search(String query, StoreCategory category, String loader, int offset) {
        evictExpired();
        String cacheKey = query + "_" + category.name() + "_" + loader + "_" + offset;
        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            return cached.items;
        }

        List<StoreItem> results = new ArrayList<>();
        try {
            String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
            
            // Construir facets dinámicamente
            StringBuilder facetsBuilder = new StringBuilder("[[\"project_type:").append(category.getModrinthType()).append("\"]");
            if (loader != null && !loader.isBlank()) {
                facetsBuilder.append(",[\"categories:").append(loader.toLowerCase()).append("\"]");
            }
            facetsBuilder.append("]");
            
            String facets = URLEncoder.encode(facetsBuilder.toString(), StandardCharsets.UTF_8);
            String url = BASE_URL + "/search?query=" + q + "&facets=" + facets + "&limit=20&offset=" + offset;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "MeaCore-Launcher/" + com.experimento.launcher.LauncherMetadata.VERSION + " (meacore.launcher)")
                    .GET()
                    .build();

            HttpResponse<InputStream> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                JsonNode root = M.readTree(res.body());
                JsonNode hits = root.path("hits");
                for (JsonNode hit : hits) {
                    JsonNode versions = hit.path("versions");

                    results.add(new StoreItem(
                            hit.path("project_id").asText(),
                            hit.path("title").asText(),
                            hit.path("author").asText(),
                            hit.path("description").asText(),
                            hit.path("icon_url").asText(),
                            hit.path("downloads").asLong(),
                            hit.path("latest_version").asText(),
                            versions.isArray() && versions.size() > 0 ? versions.get(0).asText() : "",
                            null, // Download URL is fetched later per specific version/loader
                            category,
                            "https://modrinth.com/" + category.getModrinthType() + "/" + hit.path("project_id").asText()
                    ));
                }
                CACHE.put(cacheKey, new CacheEntry(System.currentTimeMillis(), results));
            }
        } catch (Exception e) {
            // Falla silenciosa
        }
        return results;
    }

    public static String getDownloadUrl(String projectId, String mcVersion, String loader) {
        try {
            StringBuilder urlBuilder = new StringBuilder(BASE_URL).append("/project/").append(projectId).append("/version?");
            
            if (mcVersion != null && !mcVersion.isBlank()) {
                urlBuilder.append("game_versions=").append(URLEncoder.encode("[\"" + mcVersion + "\"]", StandardCharsets.UTF_8)).append("&");
            }
            if (loader != null && !loader.isBlank()) {
                urlBuilder.append("loaders=").append(URLEncoder.encode("[\"" + loader + "\"]", StandardCharsets.UTF_8));
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .header("User-Agent", "MeaCore-Launcher/" + com.experimento.launcher.LauncherMetadata.VERSION + " (meacore.launcher)")
                    .GET()
                    .build();

            HttpResponse<InputStream> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                JsonNode root = M.readTree(res.body());
                if (root.isArray() && root.size() > 0) {
                    JsonNode files = root.get(0).path("files");
                    if (files.isArray() && files.size() > 0) {
                        for (JsonNode file : files) {
                            if (file.path("primary").asBoolean(false)) {
                                return file.path("url").asText();
                            }
                        }
                        return files.get(0).path("url").asText();
                    }
                }
            }
        } catch (Exception ignored) { }
        return null; // Falló obtener url
    }

    /**
     * Obtiene todas las versiones disponibles de un mod, opcionalmente filtradas por versión de Minecraft y loader.
     */
    public static List<ModVersion> getModVersions(String projectId, String mcVersion, String loader) {
        List<ModVersion> versions = new ArrayList<>();
        try {
            StringBuilder urlBuilder = new StringBuilder(BASE_URL).append("/project/").append(projectId).append("/version?");
            
            if (mcVersion != null && !mcVersion.isBlank()) {
                urlBuilder.append("game_versions=").append(URLEncoder.encode("[\"" + mcVersion + "\"]", StandardCharsets.UTF_8)).append("&");
            }
            if (loader != null && !loader.isBlank()) {
                urlBuilder.append("loaders=").append(URLEncoder.encode("[\"" + loader.toLowerCase() + "\"]", StandardCharsets.UTF_8));
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .header("User-Agent", "MeaCore-Launcher/" + com.experimento.launcher.LauncherMetadata.VERSION + " (meacore.launcher)")
                    .GET()
                    .build();

            HttpResponse<InputStream> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                JsonNode root = M.readTree(res.body());
                if (root.isArray()) {
                    for (JsonNode versionNode : root) {
                        String versionId = versionNode.path("id").asText();
                        String versionNumber = versionNode.path("version_number").asText();
                        String changelog = versionNode.path("changelog").asText("");
                        
                        // Extraer game versions
                        List<String> gameVersions = new ArrayList<>();
                        JsonNode gameVersionsNode = versionNode.path("game_versions");
                        if (gameVersionsNode.isArray()) {
                            for (JsonNode gv : gameVersionsNode) {
                                gameVersions.add(gv.asText());
                            }
                        }
                        
                        // Extraer loaders
                        List<String> loaders = new ArrayList<>();
                        JsonNode loadersNode = versionNode.path("loaders");
                        if (loadersNode.isArray()) {
                            for (JsonNode ld : loadersNode) {
                                loaders.add(ld.asText());
                            }
                        }
                        
                        // Buscar archivo primario
                        JsonNode files = versionNode.path("files");
                        String downloadUrl = null;
                        String fileName = null;
                        long fileSize = 0;
                        
                        if (files.isArray() && files.size() > 0) {
                            for (JsonNode file : files) {
                                if (file.path("primary").asBoolean(false)) {
                                    downloadUrl = file.path("url").asText();
                                    fileName = file.path("filename").asText();
                                    fileSize = file.path("size").asLong();
                                    break;
                                }
                            }
                            // Si no hay primario, usar el primero
                            if (downloadUrl == null) {
                                JsonNode firstFile = files.get(0);
                                downloadUrl = firstFile.path("url").asText();
                                fileName = firstFile.path("filename").asText();
                                fileSize = firstFile.path("size").asLong();
                            }
                        }
                        
                        if (downloadUrl != null) {
                            versions.add(new ModVersion(versionId, versionNumber, downloadUrl, fileName, fileSize, gameVersions, loaders, changelog));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Falla silenciosa
        }
        return versions;
    }
}
