package com.experimento.launcher.service;

import com.experimento.launcher.LauncherMetadata;
import com.experimento.launcher.mojang.HttpFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servicio de auto-actualización del launcher — Windows y Linux.
 *
 * Mejoras v2.0:
 *  1. Descarga delegada a HttpFiles (8MB buffer, BufferedI/O, retry x3, backoff exponencial).
 *  2. Guards atómicos — imposible lanzar dos checks o dos descargas simultáneas.
 *  3. Rate limiting — el check de GitHub no se repite más de una vez por hora.
 *  4. Limpieza automática de instaladores viejos antes de descargar.
 *  5. Limpieza de archivos parciales si la descarga falla.
 *  6. Thread.sleep reducido a 300ms (bat ya está completamente desacoplado).
 *  7. Nombre de bat con timestamp único — evita colisión si se clica Reiniciar dos veces.
 *  8. Bat se autolimpia con "del %~f0" al terminar.
 *  9. Mensajes de error humanizados — sin rutas técnicas crudas al usuario.
 * 10. Timeout explícito en el HttpRequest al GitHub API.
 * 11. Unblock-File lanzado antes de notificar al listener — ya está listo cuando el usuario clica.
 */
public final class AutoUpdateService {

    private static final String GITHUB_API_LATEST =
            "https://api.github.com/repos/MeaCore-Enterprise/MeaCoreLauncher/releases/latest";

    private static final ObjectMapper M = new ObjectMapper();

    // Listener de eventos — volatile para visibilidad entre hilos
    private static volatile UpdateListener listener;

    // Guards: impiden checks y descargas duplicadas simultáneas
    private static final AtomicBoolean checking    = new AtomicBoolean(false);
    private static final AtomicBoolean downloading = new AtomicBoolean(false);

    // Rate limiting: no chequear GitHub más de una vez por hora
    private static volatile Instant lastCheck    = Instant.EPOCH;
    private static final Duration   CHECK_COOLDOWN = Duration.ofHours(1);

    public interface UpdateListener {
        void onUpdateFound(String version, String url);
        void onDownloadProgress(double fraction);
        /** Descarga completada. El listener decide si llamar installFromPath(). */
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
     * Consulta GitHub Releases en segundo plano.
     * - No lanza un segundo hilo si ya hay uno corriendo (guard atómico).
     * - No consulta si el último check fue hace menos de 1 hora (rate limiting).
     */
    public static void checkForUpdatesAsync() {
        if (!checking.compareAndSet(false, true)) return;

        if (Duration.between(lastCheck, Instant.now()).compareTo(CHECK_COOLDOWN) < 0) {
            checking.set(false);
            return;
        }

        Thread t = new Thread(() -> {
            try {
                lastCheck = Instant.now();

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(GITHUB_API_LATEST))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "MeaCoreLauncher/" + LauncherMetadata.VERSION)
                        // Timeout total del request — sin esto usa solo el connectTimeout
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() != 200) return;

                JsonNode release = M.readTree(res.body());
                String tagName       = release.path("tag_name").asText("");
                String latestVersion = normalizeVersion(tagName);
                String currentVersion = normalizeVersion(LauncherMetadata.VERSION);

                if (latestVersion.isBlank() || !isNewer(latestVersion, currentVersion)) return;

                boolean isWindows    = isWindows();
                String  preferredExt = isWindows ? ".exe" : ".deb";
                String  downloadUrl  = null;

                for (JsonNode asset : release.path("assets")) {
                    if (asset.path("name").asText("").endsWith(preferredExt)) {
                        downloadUrl = asset.path("browser_download_url").asText("");
                        break;
                    }
                }

                if (downloadUrl != null && listener != null) {
                    final String url = downloadUrl;
                    listener.onUpdateFound(latestVersion, url);
                }

            } catch (Exception ignored) {
                // Fallo silencioso: sin conexión o API no disponible
            } finally {
                checking.set(false);
            }
        }, "meacore-update-check");
        t.setDaemon(true);
        t.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOWNLOAD
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Descarga el instalador usando HttpFiles:
     *   - Buffer 8MB con BufferedInputStream + BufferedOutputStream en capas
     *   - Retry automático x3 con backoff exponencial (1s, 2s, 4s)
     *   - Timeout de conexión 30s / lectura 10min
     *   - Progreso en tiempo real vía Consumer<Double>
     *
     * Solo notifica onDownloadComplete — la instalación la dispara el usuario.
     */
    public static void downloadAndInstallAsync(String installerUrl) {
        if (!downloading.compareAndSet(false, true)) return;

        Thread t = new Thread(() -> {
            Path dest = null;
            try {
                boolean isWindows = isWindows();
                String  ext       = isWindows ? ".exe" : ".deb";

                Path updateDir = isWindows
                        ? Path.of(System.getenv().getOrDefault("LOCALAPPDATA",
                                  System.getProperty("java.io.tmpdir")), "MeaCore", "updates")
                        : Path.of(System.getProperty("user.home"), ".cache", "meacore", "updates");

                Files.createDirectories(updateDir);

                // Limpiar instaladores anteriores antes de descargar
                cleanOldInstallers(updateDir, ext);

                dest = updateDir.resolve("meacore-update-" + System.currentTimeMillis() + ext);
                final Path finalDest = dest;

                // HttpFiles: 8MB buffer + BufferedI/O + retry x3 + backoff exponencial
                // sin hash (null) porque GitHub no expone el SHA del asset en la API
                HttpFiles.downloadIfHashMismatch(installerUrl, finalDest, null, fraction -> {
                    if (listener != null) {
                        listener.onDownloadProgress(fraction < 0 ? -1.0 : fraction);
                    }
                });

                // Sanidad: verificar que el archivo no esté vacío
                if (!Files.exists(finalDest) || Files.size(finalDest) == 0) {
                    throw new IllegalStateException("El archivo descargado está vacío.");
                }

                // Unblock-File ANTES de notificar al listener
                // → cuando el usuario clique "Reiniciar Ahora" ya estará desbloqueado
                if (isWindows) {
                    unblockFileAsync(finalDest);
                }

                if (listener != null) listener.onDownloadComplete(finalDest);

            } catch (Exception ex) {
                // Limpiar archivo parcial para no dejar basura en disco
                if (dest != null) {
                    try { Files.deleteIfExists(dest); } catch (Exception ignored) {}
                }
                if (listener != null) listener.onDownloadError(humanizeError(ex));
            } finally {
                downloading.set(false);
            }
        }, "meacore-update-download");
        t.setDaemon(true);
        t.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INSTALL  (llamado desde la UI cuando el usuario confirma)
    // ─────────────────────────────────────────────────────────────────────────

    public static void installFromPath(Path installerPath) {
        new Thread(() -> executeInstaller(installerPath, isWindows()), "meacore-update-install")
                .start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IMPLEMENTACIÓN INTERNA
    // ─────────────────────────────────────────────────────────────────────────

    private static void executeInstaller(Path installerPath, boolean isWindows) {
        try {
            String absPath = installerPath.toAbsolutePath().toString();

            if (isWindows) {
                // Nombre único con timestamp — evita colisión si se clica Reiniciar dos veces
                Path batFile = installerPath.getParent()
                        .resolve("meacore_updater_" + System.currentTimeMillis() + ".bat");

                String bat =
                    "@echo off\r\n" +
                    "title MeaCore Updater\r\n" +
                    // 3s es suficiente — la JVM muere en ~300ms tras System.exit(0)
                    "timeout /t 3 /nobreak > nul\r\n" +
                    // Cierre forzado como respaldo por si algo quedó colgado
                    "taskkill /F /IM \"MeaCore Launcher.exe\" /T > nul 2>&1\r\n" +
                    // start /wait — el bat espera a que Inno Setup termine
                    // antes de intentar relanzar el launcher
                    "start /wait \"\" \"" + absPath + "\" /VERYSILENT /NORESTART /SUPPRESSMSGBOXES\r\n" +
                    // Cubrir ambas estructuras de instalación de jpackage
                    "set \"APPDIR=%LOCALAPPDATA%\\MeaCore Launcher\"\r\n" +
                    "if exist \"%APPDIR%\\MeaCore Launcher.exe\" (\r\n" +
                    "    start \"\" \"%APPDIR%\\MeaCore Launcher.exe\"\r\n" +
                    ") else if exist \"%APPDIR%\\app\\MeaCore Launcher.exe\" (\r\n" +
                    "    start \"\" \"%APPDIR%\\app\\MeaCore Launcher.exe\"\r\n" +
                    ")\r\n" +
                    // El bat se autolimpia — no deja basura en disco
                    "del \"%~f0\"\r\n" +
                    "exit\r\n";

                Files.writeString(batFile, bat);

                new ProcessBuilder(
                        "cmd", "/c", "start", "\"MeaCore Updater\"",
                        "/min", batFile.toAbsolutePath().toString())
                        .start();

            } else {
                // Linux: setsid + nohup, && encadena solo si apt tiene éxito
                String cmd = String.format(
                        "sleep 2 && pkexec apt install -y \"%s\" && " +
                        "setsid nohup meacorelauncher > /dev/null 2>&1 &",
                        absPath);
                new ProcessBuilder("bash", "-c", cmd).start();
            }

            // 300ms suficientes — el proceso hijo está completamente desacoplado
            Thread.sleep(300);
            System.exit(0);

        } catch (Exception e) {
            System.exit(1);
        }
    }

    /**
     * Lanza PowerShell Unblock-File como fire-and-forget.
     * No usa .waitFor() — nunca bloquea el hilo de descarga.
     */
    private static void unblockFileAsync(Path file) {
        try {
            new ProcessBuilder(
                    "powershell", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-Command", "Unblock-File -Path '" + file.toAbsolutePath() + "'")
                    .start();
        } catch (Exception ignored) {}
    }

    /**
     * Elimina instaladores anteriores del directorio de updates.
     * Evita acumulación de archivos .exe/.deb de versiones previas.
     */
    private static void cleanOldInstallers(Path dir, String ext) {
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith("meacore-update-")
                           && p.getFileName().toString().endsWith(ext))
                  .forEach(p -> {
                      try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                  });
        } catch (Exception ignored) {}
    }

    /**
     * Convierte excepciones técnicas en mensajes legibles para el usuario.
     * El listener no debería mostrar rutas del sistema ni stack traces.
     */
    private static String humanizeError(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null) return "Error inesperado al descargar la actualización.";
        if (msg.contains("updates\\meacore-update") || msg.contains("Access is denied")) {
            return "El archivo de actualización está bloqueado por Windows o el antivirus. Reinicia el launcher e intenta de nuevo.";
        }
        if (msg.contains("Connection refused") || msg.contains("UnknownHost")
                || msg.contains("UnknownHostException")) {
            return "Sin conexión a internet. Verifica tu red e intenta de nuevo.";
        }
        if (msg.contains("timed out") || msg.contains("timeout")) {
            return "La descarga tardó demasiado. Verifica tu conexión e intenta de nuevo.";
        }
        if (msg.contains("SHA1 mismatch")) {
            return "El archivo descargado está corrupto. Intenta de nuevo.";
        }
        if (msg.contains("vacío")) {
            return "Se descargó un archivo vacío. Verifica tu conexión e intenta de nuevo.";
        }
        return "Error al descargar: " + msg;
    }

    private static String normalizeVersion(String raw) {
        if (raw == null) return "";
        return raw.replace("bat-", "")
                  .replaceFirst("^[vV]", "")
                  .replaceAll("-(alfa|alpha)", "")
                  .trim();
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
