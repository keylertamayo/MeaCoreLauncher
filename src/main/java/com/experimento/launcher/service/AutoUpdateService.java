package com.experimento.launcher.service;

import com.experimento.launcher.LauncherMetadata;
import com.experimento.launcher.mojang.HttpFiles;
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
            // Comando mejorado: Instala el .deb y REINICIA el launcher
            // meacorelauncher & al final lanza el proceso en segundo plano despues de que apt termine
            String cmd = String.format("apt install -y %s && meacorelauncher &", debPath.toAbsolutePath().toString());
            ProcessBuilder pb = new ProcessBuilder("pkexec", "bash", "-c", cmd);
            pb.start();
            
            System.exit(0);
        } catch (Exception ignored) {}
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
