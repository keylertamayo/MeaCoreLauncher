package com.experimento.launcher.service;

import com.experimento.launcher.LauncherMetadata;
import com.experimento.launcher.mojang.HttpFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.InputStream;
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
                            Platform.runLater(() -> promptUpdate(latestVersion, finalUrl));
                        }
                    }
                }
            } catch (Exception ignored) {
                // Falla silenciosa si no hay internet o GitHub cae
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static void promptUpdate(String newVersion, String debUrl) {
        Alert info = new Alert(Alert.AlertType.INFORMATION, 
            "MeaCore Launcher v" + newVersion + " está disponible.\n\n" +
            "¿Deseas descargar e instalar esta actualización?\n" +
            "Se te pedirá tu contraseña de administrador para instalar paquetes nativamente.",
            ButtonType.YES, ButtonType.NO);
        info.setTitle("Actualización Disponible");
        info.setHeaderText("¡Nueva versión nativa detectada!");

        info.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                downloadAndInstall(debUrl);
            }
        });
    }

    private static void downloadAndInstall(String debUrl) {
        // Ventana de progreso sencilla
        Alert progress = new Alert(Alert.AlertType.INFORMATION, "Descargando paquete .deb...");
        progress.setTitle("Actualizando");
        progress.setHeaderText("Por favor, espera...");
        progress.getButtonTypes().clear(); // Sin botones
        progress.show();

        Thread t = new Thread(() -> {
            try {
                Path dest = Path.of(System.getProperty("user.home"), ".cache", "meacore-update.deb");
                Files.createDirectories(dest.getParent());
                Files.deleteIfExists(dest);

                HttpFiles.downloadIfHashMismatch(debUrl, dest, null); // Forzamos descarga sin hash

                Platform.runLater(() -> {
                    progress.close();
                    executePkexec(dest);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progress.close();
                    new Alert(Alert.AlertType.ERROR, "Error descargando la actualización: " + ex.getMessage()).show();
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void executePkexec(Path debPath) {
        try {
            // Comando para instalar el .deb de forma nativa usando Polkit (GUI de sudo de GNOME/Ubuntu)
            ProcessBuilder pb = new ProcessBuilder("pkexec", "apt", "install", "-y", debPath.toAbsolutePath().toString());
            pb.start();
            
            // Cerrar el launcher para no interferir con la sobreescritura de archivos
            System.exit(0);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Error ejecutando la instalación: " + ex.getMessage()).show();
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
