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
 * Servicio de auto-actualizacion del launcher — Windows y Linux.
 *
 * Correcciones aplicadas (v1.4.9):
 *  1. SmartScreen  -> Unblock-File via PowerShell antes de ejecutar el .exe.
 *  2. Directorio   -> LOCALAPPDATA/MeaCore/updates en vez de TEMP.
 *  3. Confirmacion -> downloadAndInstallAsync ya NO inicia la instalacion
 *                    automaticamente; solo avisa al listener (onDownloadComplete).
 *                    La instalacion real la inicia el usuario desde el banner.
 *  4. Detach       -> cmd /c start desacopla completamente el proceso hijo.
 *  5. URL          -> Unificada a MeaCore-Enterprise/MeaCoreLauncher.
 */
public class AutoUpdateService {

    private static final String GITHUB_API_LATEST =
            "https://api.github.com/repos/MeaCore-Enterprise/MeaCoreLauncher/releases/latest";
    private static final ObjectMapper M = new ObjectMapper();
    private static UpdateListener listener;

    public interface UpdateListener {
        void onUpdateFound(String version, String url);
        void onDownloadProgress(double fraction);
        /** Descarga finalizada. El listener decide si lanzar installFromPath(). */
        void onDownloadComplete(Path installerPath);
        void onDownloadError(String message);
    }

    public static void setListener(UpdateListener l) {
        listener = l;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHECK
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Consulta GitHub Releases en segundo plano y notifica al listener si hay
     * una versión más reciente disponible para la plataforma actual.
     */
    public static void checkForUpdatesAsync() {
        Thread thread = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(GITHUB_API_LATEST))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "MeaCoreLauncher/" + LauncherMetadata.VERSION)
                        .GET()
                        .build();

                HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() == 200) {
                    JsonNode release = M.readTree(res.body());
                    String tagName = release.path("tag_name").asText("");

                    // Normalizar: "bat-1.2.2" → "1.2.2", "v1.4.9" → "1.4.9"
                    String latestVersion = tagName
                            .replace("bat-", "")
                            .replaceFirst("^[vV]", "")
                            .replaceAll("-(alfa|alpha)", "")
                            .trim();
                    String currentVersion = LauncherMetadata.VERSION
                            .replaceFirst("^[vV]", "")
                            .replaceAll("-(alfa|alpha)", "")
                            .trim();

                    if (!latestVersion.isBlank() && isNewer(latestVersion, currentVersion)) {
                        boolean isWindows = isWindows();
                        String preferredExt = isWindows ? ".exe" : ".deb";

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
                            final String url = downloadUrl;
                            listener.onUpdateFound(latestVersion, url);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fallo silencioso: sin conexión o API no disponible
            }
        }, "meacore-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOWNLOAD  (solo descarga — NO ejecuta automáticamente)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Descarga el instalador al directorio de actualizaciones de MeaCore y
     * notifica onDownloadComplete cuando termina.
     * La instalación real se lanza SÓLO cuando el usuario confirma desde la UI
     * (botón "Reiniciar Ahora") invocando {@link #installFromPath(Path)}.
     */
    public static void downloadAndInstallAsync(String installerUrl) {
        Thread t = new Thread(() -> {
            try {
                boolean isWindows = isWindows();
                String ext = isWindows ? ".exe" : ".deb";

                // CORRECCION 2: usar LOCALAPPDATA/MeaCore/updates (mas limpio y
                // menos sospechoso para el antivirus que TEMP)
                Path updateDir = isWindows
                        ? Path.of(System.getenv().getOrDefault("LOCALAPPDATA",
                                  System.getProperty("java.io.tmpdir")), "MeaCore", "updates")
                        : Path.of(System.getProperty("user.home"), ".cache", "meacore", "updates");

                Files.createDirectories(updateDir);

                Path dest = updateDir.resolve("meacore-update" + ext);
                Files.deleteIfExists(dest);

                downloadWithProgress(installerUrl, dest);

                // CORRECCIÓN 1: Eliminar Zone.Identifier (SmartScreen) en background
                // Se lanza aquí mientras el usuario lee el banner, para que al clicsar "Reiniciar" ya esté listo.
                if (isWindows) {
                    try {
                        new ProcessBuilder(
                                "powershell", "-NonInteractive", "-WindowStyle", "Hidden",
                                "-Command", "Unblock-File -Path '" + dest.toAbsolutePath() + "'")
                                .start(); // fire-and-forget
                    } catch (Exception ignored) {}
                }

                // CORRECCIÓN 3: NO ejecutar automáticamente.
                // Avisamos al listener y él decide (normalmente el banner le pregunta al usuario).
                if (listener != null) listener.onDownloadComplete(dest);

            } catch (Exception ex) {
                if (listener != null) listener.onDownloadError(
                        ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            }
        }, "meacore-update-download");
        t.setDaemon(true);
        t.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INSTALL  (llamado desde la UI cuando el usuario confirma)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lanza el instalador de forma apropiada según la plataforma y cierra el
     * launcher. Llamar únicamente tras confirmación explícita del usuario.
     */
    public static void installFromPath(Path installerPath) {
        new Thread(() -> executeInstaller(installerPath, isWindows()), "meacore-update-install")
                .start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IMPLEMENTACIÓN INTERNA
    // ─────────────────────────────────────────────────────────────────────────

    private static void downloadWithProgress(String urlStr, Path dest) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .header("User-Agent", "MeaCoreLauncher/" + LauncherMetadata.VERSION)
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        long totalBytes;
        try {
            totalBytes = Long.parseLong(response.headers().firstValue("Content-Length").orElse("-1"));
        } catch (NumberFormatException e) {
            totalBytes = -1;
        }

        try (InputStream is = response.body();
             OutputStream os = Files.newOutputStream(dest)) {
            byte[] buffer = new byte[524288]; // 512 KB
            long readBytes = 0;
            int n;
            while ((n = is.read(buffer)) != -1) {
                os.write(buffer, 0, n);
                readBytes += n;
                if (totalBytes > 0 && listener != null) {
                    final double progress = (double) readBytes / totalBytes;
                    listener.onDownloadProgress(progress);
                }
            }
        }
    }

    /**
     * Lanza el instalador de forma completamente desacoplada del proceso Java.
     *
     * Windows:
     *   1. Desbloquea el .exe con PowerShell (elimina Zone.Identifier / SmartScreen).
     *   2. Escribe un .bat en el mismo directorio que usa `timeout` nativo.
     *   3. Lanza el .bat con `cmd /c start` para desacoplarlo completamente.
     *   Resultado: Inno Setup arranca 3 s después de que la JVM haya muerto.
     *
     * Linux:
     *   Usa bash + setsid + nohup para desacoplar de la sesión actual.
     */
    private static void executeInstaller(Path installerPath, boolean isWindows) {
        try {
            String absPath = installerPath.toAbsolutePath().toString();

            if (isWindows) {
                // El Unblock-File ahora se hace en downloadAndInstallAsync para no bloquear el hilo principal.

                // CORRECCIÓN 4: .bat con timeout nativo y cmd /c start (detach total)
                Path batFile = installerPath.getParent().resolve("meacore_updater.bat");
                String bat =
                    "@echo off\r\n" +
                    "title MeaCore Updater\r\n" +
                    "echo.\r\n" +
                    "echo  === MeaCore Launcher - Actualizacion ===\r\n" +
                    "echo  Esperando que el launcher se cierre (3 segundos)...\r\n" +
                    "timeout /t 3 /nobreak > nul\r\n" +
                    "echo  Asegurando cierre del proceso...\r\n" +
                    "taskkill /F /IM \"MeaCore Launcher.exe\" /T > nul 2>&1\r\n" +
                    "echo  Ejecutando instalador...\r\n" +
                    "start /wait \"\" \"" + absPath + "\" /VERYSILENT /NORESTART /SUPPRESSMSGBOXES\r\n" +
                    "echo  Relanzando MeaCore Launcher...\r\n" +
                    "set \"APPDIR=%LOCALAPPDATA%\\MeaCore Launcher\\app\"\r\n" +
                    "if exist \"%APPDIR%\\MeaCore Launcher.exe\" (\r\n" +
                    "    start \"\" \"%APPDIR%\\MeaCore Launcher.exe\"\r\n" +
                    ")\r\n" +
                    "exit\r\n";

                Files.writeString(batFile, bat);

                // Lanzar el .bat de forma completamente independiente (detached)
                new ProcessBuilder(
                        "cmd", "/c", "start", "\"MeaCore Updater\"",
                        "/min", batFile.toAbsolutePath().toString())
                        .start();

            } else {
                // Linux: setsid + nohup para desacoplar de la sesión padre
                // && encadena la reapertura solo si el install fue exitoso
                String cmd = String.format(
                        "sleep 2 && pkexec apt install -y \"%s\" && " +
                        "setsid nohup meacorelauncher > /dev/null 2>&1 &",
                        absPath);
                new ProcessBuilder("bash", "-c", cmd).start();
            }

            // Dar tiempo al proceso hijo para arrancar antes de que la JVM muera
            Thread.sleep(1200);
            System.exit(0);

        } catch (Exception e) {
            // Si algo falla, no dejar el launcher sin salida
            System.exit(1);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isNewer(String latest, String current) {
        try {
            String[] lp = latest.split("\\.");
            String[] cp = current.split("\\.");
            int max = Math.max(lp.length, cp.length);
            for (int i = 0; i < max; i++) {
                int l = i < lp.length ? Integer.parseInt(lp[i].replaceAll("[^0-9]", "")) : 0;
                int c = i < cp.length ? Integer.parseInt(cp[i].replaceAll("[^0-9]", "")) : 0;
                if (l > c) return true;
                if (l < c) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
