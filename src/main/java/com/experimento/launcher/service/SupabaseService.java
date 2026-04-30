package com.experimento.launcher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para enviar telemetría y errores a Supabase.
 */
public class SupabaseService {

    private static String SUPABASE_URL = "";
    private static String SUPABASE_KEY = "";

    static {
        try {
            java.util.Properties props = new java.util.Properties();
            java.nio.file.Path secretPath = java.nio.file.Paths.get("secrets.properties");
            if (java.nio.file.Files.exists(secretPath)) {
                try (java.io.InputStream is = java.nio.file.Files.newInputStream(secretPath)) {
                    props.load(is);
                    SUPABASE_URL = props.getProperty("supabase.url", "");
                    SUPABASE_KEY = props.getProperty("supabase.key", "");
                }
            }
        } catch (Exception ignored) {}
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Envia un reporte de error de forma asíncrona. */
    public static void reportCrash(String crashLog, String version) {
        if (SUPABASE_URL == null || SUPABASE_URL.isBlank()) return;

        Map<String, Object> data = Map.of(
            "launcher_version", version,
            "crash_log", crashLog,
            "os", System.getProperty("os.name")
        );

        postAsync("/rest/v1/crashes", data);
    }

    /** Envia datos de uso genéricos. */
    public static void sendEvent(String eventType, Map<String, Object> metadata) {
        if (SUPABASE_URL.contains("YOUR_PROJECT_REF")) return;

        Map<String, Object> data = Map.of(
            "event_type", eventType,
            "launcher_version", com.experimento.launcher.LauncherMetadata.VERSION,
            "metadata", metadata
        );

        postAsync("/rest/v1/telemetry", data);
    }

    private static void postAsync(String endpoint, Object body) {
        CompletableFuture.runAsync(() -> {
            try {
                String json = MAPPER.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + endpoint))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + SUPABASE_KEY)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // Falla silenciosa para no interrumpir al usuario
            }
        });
    }
}
