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

    public static List<StoreItem> search(String query, StoreCategory category, int offset) {
        String cacheKey = query + "_" + category.name() + "_" + offset;
        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            return cached.items;
        }

        List<StoreItem> results = new ArrayList<>();
        try {
            String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String facets = URLEncoder.encode("[[\"project_type:" + category.getModrinthType() + "\"]]", StandardCharsets.UTF_8);
            String url = BASE_URL + "/search?query=" + q + "&facets=" + facets + "&limit=20&offset=" + offset;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "MeaCore-Launcher/1.2.2")
                    .GET()
                    .build();

            HttpResponse<InputStream> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                JsonNode root = M.readTree(res.body());
                JsonNode hits = root.path("hits");
                for (JsonNode hit : hits) {
                    JsonNode versions = hit.path("versions");
                    JsonNode mcVersions = hit.path("display_categories");

                    results.add(new StoreItem(
                            hit.path("project_id").asText(),
                            hit.path("title").asText(),
                            hit.path("author").asText(),
                            hit.path("description").asText(),
                            hit.path("icon_url").asText(),
                            hit.path("downloads").asLong(),
                            versions.isArray() && versions.size() > 0 ? versions.get(0).asText() : "",
                            mcVersions.isArray() && mcVersions.size() > 0 ? mcVersions.get(0).asText() : "",
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
                urlBuilder.append("game_versions=[\"").append(mcVersion).append("\"]&");
            }
            if (loader != null && !loader.isBlank()) {
                urlBuilder.append("loaders=[\"").append(loader).append("\"]");
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .header("User-Agent", "MeaCore-Launcher/1.2.2")
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
}
