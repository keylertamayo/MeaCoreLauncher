package com.experimento.launcher.service;

import com.experimento.launcher.mojang.OsContext;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Gestiona los entornos de ejecución de Java (JRE) portátiles.
 * Soporta Java 8, 17 y 21.
 */
public final class JavaRuntimeService {

    private static final String ADOPTIUM_API_TEMPLATE =
            "https://api.adoptium.net/v3/binary/latest/%d/ga/%s/%s/jre/hotspot/normal/eclipse?project=jdk";
    
    // Fallbacks de GitHub para casos donde Adoptium falle (repositorio oficial MeaCore o mirrors)
    private static final String GITHUB_JRE_8_WIN = "https://github.com/MeaCore-Enterprise/MeaCoreLauncher/releases/download/v1.0.0/jre8-windows.zip";
    private static final String GITHUB_JRE_21_WIN = "https://github.com/MeaCore-Enterprise/MeaCoreLauncher/releases/download/v1.0.0/jre21-windows.zip";

    private static final int DOWNLOAD_BUFFER_SIZE = 524288;

    private final Path runtimeDir;
    private final OsContext os = OsContext.current();

    public JavaRuntimeService(Path launcherDataDir) {
        this.runtimeDir = launcherDataDir.resolve("runtime");
    }

    public Path getExecutable(int version) {
        Path vDir = runtimeDir.resolve("java" + version);
        if (!Files.exists(vDir)) return null;

        String exeName = os.javaExecutableName();
        try (var stream = Files.walk(vDir)) {
            return stream
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(exeName))
                .filter(p -> p.getParent().getFileName().toString().equalsIgnoreCase("bin"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    @Deprecated
    public Path getJava8Executable() {
        return getExecutable(8);
    }

    public Path getJava17Executable() {
        return getExecutable(17);
    }

    public Path getJava21Executable() {
        return getExecutable(21);
    }

    public void downloadJavaAsync(int version, Consumer<Double> progress, Consumer<Path> onResult, Consumer<String> onError) {
        new Thread(() -> {
            try {
                Path result = downloadJavaSync(version, progress);
                if (result != null) onResult.accept(result);
                else onError.accept("No se pudo instalar Java " + version);
            } catch (Exception e) {
                onError.accept(e.getMessage());
            }
        }).start();
    }

    /** Versión síncrona para ser usada dentro de Tasks o hilos controlados. */
    public Path downloadJavaSync(int version, Consumer<Double> progress) throws Exception {
        Files.createDirectories(runtimeDir);
        String ext = os.archiveExtension();
        Path archiveFile = runtimeDir.resolve("java" + version + ext);
        Path extractDir = runtimeDir.resolve("java" + version);

        if (Files.exists(extractDir)) {
            deleteDirectory(extractDir);
        }
        Files.createDirectories(extractDir);

        String url = String.format(ADOPTIUM_API_TEMPLATE, version, os.name(), os.arch());
        
        try {
            downloadWithProgress(url, archiveFile, progress);
        } catch (Exception e) {
            // Intento de fallback a GitHub si es Windows y falla el API principal
            if (os.isWindows()) {
                String fallback = (version == 8) ? GITHUB_JRE_8_WIN : (version == 21 ? GITHUB_JRE_21_WIN : null);
                if (fallback != null) {
                    System.out.println("[JRE] Fallback detectado para Java " + version);
                    downloadWithProgress(fallback, archiveFile, progress);
                } else {
                    throw e;
                }
            } else {
                throw e;
            }
        }

        boolean success;
        if (os.isWindows()) {
            extractZip(archiveFile, extractDir);
            success = true;
        } else {
            ProcessBuilder pb = new ProcessBuilder(
                    "tar", "-xzf",
                    archiveFile.toAbsolutePath().toString(),
                    "-C", extractDir.toAbsolutePath().toString());
            Process p = pb.start();
            success = p.waitFor() == 0;
        }

        Files.deleteIfExists(archiveFile);

        if (success) {
            Path exe = getExecutable(version);
            if (exe != null) {
                if (!os.isWindows()) exe.toFile().setExecutable(true);
                return exe;
            }
        }
        return null;
    }

    private void extractZip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            int extractedCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    try {
                        Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                        extractedCount++;
                    } catch (IOException e) {
                        System.err.println("[JRE] Error extracting " + entry.getName() + ": " + e.getMessage());
                        throw new IOException("ZIP corrupted or incomplete: " + e.getMessage(), e);
                    }
                }
                zis.closeEntry();
            }
            if (extractedCount == 0) {
                throw new IOException("No files extracted from ZIP - file may be corrupted");
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("ZIP file corrupted: " + e.getMessage() + ". Try downloading again.", e);
        }
    }

    @Deprecated
    public void downloadJava8Async(Consumer<Double> progress, Consumer<Path> onResult, Consumer<String> onError) {
        downloadJavaAsync(8, progress, onResult, onError);
    }

    private void downloadWithProgress(String url, Path dest, Consumer<Double> progress) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        int retries = 3;
        while (retries > 0) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(java.time.Duration.ofMinutes(10))
                        .build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                long total = Long.parseLong(response.headers().firstValue("Content-Length").orElse("-1"));
                try (InputStream is = response.body();
                     OutputStream os2 = Files.newOutputStream(dest)) {
                    byte[] buf = new byte[DOWNLOAD_BUFFER_SIZE];
                    long read = 0;
                    int n;
                    if (progress != null) {
                        if (total > 0) {
                            progress.accept(0.0);
                        } else {
                            progress.accept(-1.0);
                        }
                    }
                    while ((n = is.read(buf)) != -1) {
                        os2.write(buf, 0, n);
                        read += n;
                        if (progress != null) {
                            if (total > 0) {
                                progress.accept((double) read / total);
                            }
                        }
                    }
                    if (progress != null) {
                        progress.accept(1.0);
                    }
                    return;
                }
            } catch (IOException e) {
                retries--;
                if (retries == 0) throw e;
                Thread.sleep(1000);
            }
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
