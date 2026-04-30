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

    // NOTA: Estas credenciales deben ser configuradas por el usuario.
    // Se recomienda usar variables de entorno o un archivo de configuración externo.
    private static final String SUPABASE_URL = "https://YOUR_PROJECT_REF.supabase.co";
    private static final String SUPABASE_KEY = "YOUR_ANON_KEY";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Envia un reporte de error de forma asíncrona. */
    public static void reportCrash(String crashLog, String version) {
        if (SUPABASE_URL.contains("YOUR_PROJECT_REF")) return;

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
