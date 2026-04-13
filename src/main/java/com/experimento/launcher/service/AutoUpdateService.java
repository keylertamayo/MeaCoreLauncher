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

/**
 * Servicio de auto-actualización del launcher.
 * Multiplataforma: usa .msi en Windows y .deb en Linux.
 */
public class AutoUpdateService {

    private static final String GITHUB_API_LATEST =
            "https://api.github.com/repos/keylertamayo/MeaCoreLauncher/releases/latest";
    private static final ObjectMapper M = new ObjectMapper();
    private static UpdateListener listener;

    public interface UpdateListener {
        void onUpdateFound(String version, String url);
        void onDownloadProgress(double fraction);
        void onDownloadComplete(Path installerPath);
        void onDownloadError(String message);
    }

    public static void setListener(UpdateListener l) {
        listener = l;
    }

    /**
     * Consulta GitHub Releases en segundo plano y notifica al listener si hay
     * una versión más reciente disponible para la plataforma actual.
     */
    public static void checkForUpdatesAsync() {
        Thread thread = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(GITHUB_API_LATEST))
                        .header("Accept", "application/vnd.github.v3+json")
                        .GET()
                        .build();

                HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() == 200) {
                    JsonNode release = M.readTree(res.body());
                    String tagName = release.path("tag_name").asText("");

                    // "bat-1.2.2" -> "1.2.2"
                    String latestVersion = tagName.replace("bat-", "")
                            .replaceAll("-alfa", "").replaceAll("-alpha", "").trim();
                    String currentVersion = LauncherMetadata.VERSION
                            .replaceAll("-alfa", "").replaceAll("-alpha", "").trim();

                    if (!latestVersion.isBlank() && isNewer(latestVersion, currentVersion)) {
                        // Elegir la extensión correcta según el SO
                        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
                        String preferredExt = isWindows ? ".msi" : ".deb";

                        String downloadUrl = null;
                        JsonNode assets = release.path("assets");
                        for (JsonNode asset : assets) {
                            String name = asset.path("name").asText("");
                            if (name.endsWith(preferredExt)) {
                                downloadUrl = asset.path("browser_download_url").asText("");
                                break;
                            }
                        }

                        if (downloadUrl != null && listener != null) {
                            listener.onUpdateFound(latestVersion, downloadUrl);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fallo silencioso: sin conexión o API no disponible
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Descarga el instalador y lo ejecuta.
     * En Windows usa {@code msiexec /i ... /passive /norestart}.
     * En Linux usa {@code pkexec apt install -y ...}.
     */
    public static void downloadAndInstallAsync(String installerUrl) {
        Thread t = new Thread(() -> {
            try {
                boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
                String ext = isWindows ? ".msi" : ".deb";

                // Directorio temporal apropiado según el SO
                Path tempDir;
                if (isWindows) {
                    String tmp = System.getenv("TEMP");
                    tempDir = Path.of(tmp != null ? tmp : System.getProperty("java.io.tmpdir"));
                } else {
                    tempDir = Path.of(System.getProperty("user.home"), ".cache", "meacore");
                }
                Files.createDirectories(tempDir);

                Path dest = tempDir.resolve("meacore-update" + ext);
                Files.deleteIfExists(dest);

                downloadWithProgress(installerUrl, dest);

                if (listener != null) listener.onDownloadComplete(dest);
                executeInstaller(dest, isWindows);
            } catch (Exception ex) {
                if (listener != null) listener.onDownloadError(ex.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void downloadWithProgress(String urlStr, Path dest) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
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

    /**
     * Lanza el instalador de forma apropiada para cada plataforma.
     * El launcher se cierra tras arrancar el proceso instalador.
     */
    private static void executeInstaller(Path installerPath, boolean isWindows) {
        try {
            String path = installerPath.toAbsolutePath().toString();
            if (isWindows) {
                // /passive: UI de progreso mínima, sin interacción.
                // /norestart: no reinicia automáticamente el sistema.
                new ProcessBuilder("msiexec", "/i", path, "/passive", "/norestart").start();
            } else {
                // sleep 1: tiempo para que el launcher se cierre antes que pkexec tome control.
                // setsid nohup: el nuevo proceso es independiente del proceso padre.
                String cmd = String.format(
                        "sleep 1; pkexec apt install -y %s && setsid nohup meacorelauncher > /dev/null 2>&1 &",
                        path);
                new ProcessBuilder("bash", "-c", cmd).start();
            }
            // Pequeño delay para que el proceso hijo arranque antes de salir
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
