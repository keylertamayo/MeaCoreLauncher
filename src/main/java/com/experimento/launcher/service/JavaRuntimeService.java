package com.experimento.launcher.service;

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

    private static final String ADOPTIUM_API_TEMPLATE = "https://api.adoptium.net/v3/binary/latest/%d/ga/linux/x64/jre/hotspot/normal/eclipse?project=jdk";
    private final Path runtimeDir;

    public JavaRuntimeService(Path launcherDataDir) {
        this.runtimeDir = launcherDataDir.resolve("runtime");
    }

    public Path getExecutable(int version) {
        Path vDir = runtimeDir.resolve("java" + version);
        if (!Files.exists(vDir)) return null;
        
        try (var stream = Files.walk(vDir)) {
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

    @Deprecated
    public Path getJava8Executable() {
        return getExecutable(8);
    }
    
    public Path getJava17Executable() {
        return getExecutable(17);
    }

    public void downloadJavaAsync(int version, Consumer<Double> progress, Consumer<Path> onResult, Consumer<String> onError) {
        new Thread(() -> {
            try {
                Files.createDirectories(runtimeDir);
                Path tarFile = runtimeDir.resolve("java" + version + ".tar.gz");
                Path extractDir = runtimeDir.resolve("java" + version);
                
                if (Files.exists(extractDir)) {
                    deleteDirectory(extractDir);
                }
                Files.createDirectories(extractDir);

                String url = String.format(ADOPTIUM_API_TEMPLATE, version);
                downloadWithProgress(url, tarFile, progress);

                ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tarFile.toAbsolutePath().toString(), "-C", extractDir.toAbsolutePath().toString());
                Process p = pb.start();
                int code = p.waitFor();
                
                Files.deleteIfExists(tarFile);

                if (code == 0) {
                    Path exe = getExecutable(version);
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

    @Deprecated
    public void downloadJava8Async(Consumer<Double> progress, Consumer<Path> onResult, Consumer<String> onError) {
        downloadJavaAsync(8, progress, onResult, onError);
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

    private void deleteDirectory(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
