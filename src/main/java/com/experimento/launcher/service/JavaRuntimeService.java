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
    private static final int DOWNLOAD_BUFFER_SIZE = 65536;

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
                Files.createDirectories(runtimeDir);
                String ext = os.archiveExtension();
                Path archiveFile = runtimeDir.resolve("java" + version + ext);
                Path extractDir = runtimeDir.resolve("java" + version);

                if (Files.exists(extractDir)) {
                    deleteDirectory(extractDir);
                }
                Files.createDirectories(extractDir);

                String url = String.format(ADOPTIUM_API_TEMPLATE, version, os.name(), os.arch());
                downloadWithProgress(url, archiveFile, progress);

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
                        onResult.accept(exe);
                    } else {
                        onError.accept("No se encontró el ejecutable tras la extracción.");
                    }
                } else {
                    onError.accept("Error al extraer el archivo " + ext);
                }

            } catch (Exception e) {
                onError.accept(e.getMessage());
            }
        }).start();
    }

    private void extractZip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofMinutes(10))
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        long total = Long.parseLong(response.headers().firstValue("Content-Length").orElse("-1"));
        int retries = 3;
        while (retries > 0) {
            try (InputStream is = response.body();
                 OutputStream os2 = Files.newOutputStream(dest)) {
                byte[] buf = new byte[DOWNLOAD_BUFFER_SIZE];
                long read = 0;
                int n;
                while ((n = is.read(buf)) != -1) {
                    os2.write(buf, 0, n);
                    read += n;
                    if (total > 0) progress.accept((double) read / total);
                }
                return;
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
