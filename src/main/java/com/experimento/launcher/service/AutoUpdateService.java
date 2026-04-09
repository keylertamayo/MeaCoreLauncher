package com.experimento.launcher.service;

import com.experimento.launcher.LauncherMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class AutoUpdateService {

    private static final String GITHUB_API_LATEST = "https://api.github.com/repos/keylertamayo/MeaCoreLauncher/releases/latest";
    private static final ObjectMapper M = new ObjectMapper();
    private static UpdateListener listener;

    public interface UpdateListener {
        void onUpdateFound(String version, String url);
        void onDownloadProgress(double fraction);
        void onDownloadComplete(Path debPath);
        void onDownloadError(String message);
    }

    public static void setListener(UpdateListener l) {
        listener = l;
    }

    public static void checkForUpdatesAsync() {
        Thread thread = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(GITHUB_API_LATEST))
                        .header("Accept", "application/vnd.github.v3+json")
                        .GET()
                        .build();

                HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() == 200) {
                    JsonNode release = M.readTree(res.body());
                    String tagName = release.path("tag_name").asText("");
                    
                    // Extraer versión de "bat-1.2.2" -> "1.2.2"
                    String latestVersion = tagName.replace("bat-", "").replaceAll("-alfa", "").replaceAll("-alpha", "").trim();
                    String currentVersion = LauncherMetadata.VERSION.replaceAll("-alfa", "").replaceAll("-alpha", "").trim();

                    if (!latestVersion.isBlank() && isNewer(latestVersion, currentVersion)) {
                        String downloadUrl = null;
                        JsonNode assets = release.path("assets");
                        for (JsonNode asset : assets) {
                            String name = asset.path("name").asText("");
                            if (name.endsWith(".deb")) {
                                downloadUrl = asset.path("browser_download_url").asText("");
                                break;
                            }
                        }

                        if (downloadUrl != null) {
                            final String finalUrl = downloadUrl;
                            final String ver = latestVersion;
                            if (listener != null) listener.onUpdateFound(ver, finalUrl);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public static void downloadAndInstallAsync(String debUrl) {
        Thread t = new Thread(() -> {
            try {
                Path dest = Path.of(System.getProperty("user.home"), ".cache", "meacore-update.deb");
                Files.createDirectories(dest.getParent());
                Files.deleteIfExists(dest);

                // Descarga personalizada con progreso
                downloadWithProgress(debUrl, dest);

                if (listener != null) listener.onDownloadComplete(dest);
                executePkexec(dest);
            } catch (Exception ex) {
                if (listener != null) listener.onDownloadError(ex.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void downloadWithProgress(String urlStr, Path dest) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlStr)).build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        long totalBytes = Long.parseLong(response.headers().firstValue("Content-Length").orElse("-1"));
        
        try (InputStream is = response.body();
             OutputStream os = Files.newOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            long readBytes = 0;
            int n;
            while ((n = is.read(buffer)) != -1) {
                os.write(buffer, 0, n);
                readBytes += n;
                if (totalBytes > 0 && listener != null) {
                    listener.onDownloadProgress((double) readBytes / totalBytes);
                }
            }
        }
    }

    private static void executePkexec(Path debPath) {
        try {
            // Comando robusto y desvinculado (detached)
            // 1. sleep 1 da tiempo al launcher a cerrarse si es necesario.
            // 2. pkexec solicita permisos.
            // 3. apt instala el .deb.
            // 4. meacorelauncher reinicia la app (asumiendo que está en el PATH tras instalar el .deb).
            String deb = debPath.toAbsolutePath().toString();
            // Usamos setsid y nohup para que el nuevo proceso sea totalmente independiente de este bash y de pkexec
            String innerCmd = String.format("sleep 1; pkexec apt install -y %s && setsid nohup meacorelauncher > /dev/null 2>&1 &", deb);
            
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", innerCmd);
            pb.start();
            
            // IMPORTANTE: Un pequeño delay antes del exit ayuda a que OS/PolicyKit 
            // no descarte la petición de pkexec al morir el padre instantáneamente.
            Thread.sleep(2000);
            System.exit(0);
        } catch (Exception ignored) {
            System.exit(1);
        }
    }

    private static boolean isNewer(String latest, String current) {
        try {
            String[] lParts = latest.split("\\.");
            String[] cParts = current.split("\\.");
            int max = Math.max(lParts.length, cParts.length);
            for (int i = 0; i < max; i++) {
                int l = i < lParts.length ? Integer.parseInt(lParts[i]) : 0;
                int c = i < cParts.length ? Integer.parseInt(cParts[i]) : 0;
                if (l > c) return true;
                if (l < c) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
