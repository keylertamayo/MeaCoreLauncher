package com.experimento.launcher.mojang;

import com.experimento.launcher.util.Hashing;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class HttpFiles {

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    private HttpFiles() {}

    public static byte[] getBytes(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " for " + url);
        }
        return res.body();
    }

    public static void downloadIfHashMismatch(String url, Path dest, String expectedSha1) throws Exception {
        Files.createDirectories(dest.getParent());
        if (Files.exists(dest) && expectedSha1 != null && !expectedSha1.isBlank()) {
            try (InputStream in = Files.newInputStream(dest)) {
                String got = Hashing.sha1Hex(in);
                if (expectedSha1.equalsIgnoreCase(got)) {
                    return;
                }
            }
        }
        byte[] body = getBytes(url);
        String got = Hashing.sha1Hex(new java.io.ByteArrayInputStream(body));
        if (expectedSha1 != null && !expectedSha1.isBlank() && !expectedSha1.equalsIgnoreCase(got)) {
            throw new IllegalStateException("SHA1 mismatch for " + url + " expected " + expectedSha1 + " got " + got);
        }
        Files.write(dest, body);
    }
}
