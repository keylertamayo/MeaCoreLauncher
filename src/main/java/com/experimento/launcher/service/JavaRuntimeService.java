package com.experimento.launcher.service;

import com.experimento.launcher.mojang.HttpFiles;
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
 */
public final class JavaRuntimeService {

    private static final String ADOPTIUM_API = "https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jre/hotspot/normal/eclipse?project=jdk";
    private final Path runtimeDir;

    public JavaRuntimeService(Path launcherDataDir) {
        this.runtimeDir = launcherDataDir.resolve("runtime");
    }

    public Path getJava8Executable() {
        if (!Files.exists(runtimeDir)) return null;
        
        try (var stream = Files.walk(runtimeDir)) {
            return stream
                .filter(p -> p.getFileName().toString().equals("java"))
                .filter(p -> p.getParent().getFileName().toString().equals("bin"))
                .filter(p -> Files.isExecutable(p))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public void downloadJava8Async(Consumer<Double> progress, Consumer<Path> onResult, Consumer<String> onError) {
        new Thread(() -> {
            try {
                Files.createDirectories(runtimeDir);
                Path tarFile = runtimeDir.resolve("java8.tar.gz");
                Path extractDir = runtimeDir.resolve("java8");
                
                if (Files.exists(extractDir)) {
                    deleteDirectory(extractDir);
                }
                Files.createDirectories(extractDir);

                // Descarga con progreso
                downloadWithProgress(ADOPTIUM_API, tarFile, progress);

                // Extracción nativa (preserva permisos y maneja tar.gz)
                ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tarFile.toAbsolutePath().toString(), "-C", extractDir.toAbsolutePath().toString());
                Process p = pb.start();
                int code = p.waitFor();
                
                Files.deleteIfExists(tarFile);

                if (code == 0) {
                    Path exe = getJava8Executable();
                    if (exe != null) {
                        exe.toFile().setExecutable(true);
                        onResult.accept(exe);
                    } else {
                        onError.accept("No se encontró el ejecutable tras la extracción.");
                    }
                } else {
                    onError.accept("Error al extraer el archivo tar.gz (Código " + code + ").");
                }

            } catch (Exception e) {
                onError.accept(e.getMessage());
            }
        }).start();
    }

    private void downloadWithProgress(String url, Path dest, Consumer<Double> progress) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        long total = Long.parseLong(response.headers().firstValue("Content-Length").orElse("-1"));
        try (InputStream is = response.body();
             OutputStream os = Files.newOutputStream(dest)) {
            byte[] buf = new byte[8192];
            long read = 0;
            int n;
            while ((n = is.read(buf)) != -1) {
                os.write(buf, 0, n);
                read += n;
                if (total > 0) progress.accept((double) read / total);
            }
        }
    }

    private void extractZip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
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
