package com.experimento.launcher.mojang;

import com.experimento.launcher.LauncherMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class GameLauncher {

    private final Path librariesDir;
    private final Path assetsDir;
    private final Path versionsDir;
    private final OsContext os = OsContext.current();

    public GameLauncher(Path librariesDir, Path assetsDir, Path versionsDir) {
        this.librariesDir = librariesDir;
        this.assetsDir = assetsDir;
        this.versionsDir = versionsDir;
    }

    public List<String> buildCommand(
            JsonNode mergedVersion,
            String versionId,
            Path gameDir,
            String username,
            String uuidString,
            List<String> extraJvmArgs,
            String customJavaPath,
            LaunchFeatures features)
            throws Exception {

        Path versionRoot = versionsDir.resolve(versionId);
        Path clientJar = versionRoot.resolve(versionId + ".jar");
        Path nativesDir = versionRoot.resolve("natives");

        List<Path> classpath = buildClasspath(mergedVersion, clientJar);

        String cpSep = File.pathSeparator;
        String classpathStr =
                classpath.stream().map(p -> p.toAbsolutePath().toString()).collect(Collectors.joining(cpSep));

        JsonNode assetIndex = mergedVersion.get("assetIndex");
        String assetIndexName = assetIndex.get("id").asText();

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", username);
        vars.put("version_name", versionId);
        vars.put("game_directory", gameDir.toAbsolutePath().toString());
        vars.put("assets_root", assetsDir.toAbsolutePath().toString());
        vars.put("assets_index_name", assetIndexName);
        vars.put("auth_uuid", uuidString.replace("-", ""));
        vars.put("auth_access_token", "0");
        vars.put("clientid", "");
        vars.put("auth_xuid", "");
        vars.put("user_type", "legacy");
        vars.put("version_type", mergedVersion.path("type").asText("release"));
        vars.put("natives_directory", nativesDir.toAbsolutePath().toString());
        vars.put("launcher_name", LauncherMetadata.TECHNICAL_ID);
        vars.put("launcher_version", LauncherMetadata.VERSION);
        vars.put("user_properties", "{}");
        vars.put("classpath", classpathStr);
        vars.put("classpath_separator", cpSep);
        vars.put("library_directory", librariesDir.toAbsolutePath().toString());
        vars.put("game_assets", assetsDir.toAbsolutePath().toString());
        vars.put("resolution_width", String.valueOf(features.resolutionWidth));
        vars.put("resolution_height", String.valueOf(features.resolutionHeight));
        vars.put("quickPlayPath", "");
        vars.put("quickPlaySingleplayer", "");
        vars.put("quickPlayMultiplayer", "");
        vars.put("quickPlayRealms", "");

        Path logFile = resolveLogConfig(mergedVersion, versionRoot);
        if (logFile != null) {
            vars.put("path", logFile.toAbsolutePath().toString());
        }

        List<String> jvm = new ArrayList<>();
        jvm.add(customJavaPath != null && !customJavaPath.isBlank() ? customJavaPath : resolveJavaBinary());
        if (extraJvmArgs != null) {
            for (String a : extraJvmArgs) {
                if (a != null && !a.isBlank()) {
                    jvm.add(a);
                }
            }
        }
        JsonNode args = mergedVersion.get("arguments");
        if (args != null && args.has("jvm")) {
            for (String a : ArgumentFlattener.flatten(args, "jvm", os, features)) {
                jvm.add(substitute(a, vars));
            }
        } else {
            jvm.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
            jvm.add("-cp");
            jvm.add(classpathStr);
        }

        String mainClass = mergedVersion.get("mainClass").asText("net.minecraft.client.main.Main");
        jvm.add(mainClass);

        List<String> game = new ArrayList<>();
        if (args != null && args.has("game")) {
            for (String a : ArgumentFlattener.flatten(args, "game", os, features)) {
                game.add(substitute(a, vars));
            }
        } else {
            String legacy = mergedVersion.get("minecraftArguments").asText();
            for (String part : splitMinecraftArguments(legacy)) {
                game.add(substitute(part, vars));
            }
        }

        List<String> full = new ArrayList<>(jvm);
        full.addAll(game);
        return full;
    }

    private static Path resolveLogConfig(JsonNode merged, Path versionRoot) throws Exception {
        JsonNode file = merged.path("logging").path("client").path("file");
        if (file.isMissingNode() || !file.has("id")) {
            return null;
        }
        Path p = versionRoot.resolve(file.get("id").asText());
        return Files.exists(p) ? p : null;
    }

    private static String resolveJavaBinary() {
        String home = System.getProperty("java.home");
        if (home != null) {
            Path bin = Path.of(home, "bin", "java");
            if (Files.exists(bin)) {
                return bin.toString();
            }
            Path binExe = Path.of(home, "bin", "java.exe");
            if (Files.exists(binExe)) {
                return binExe.toString();
            }
        }
        return "java";
    }

    private List<Path> buildClasspath(JsonNode merged, Path clientJar) throws Exception {
        List<Path> cp = new ArrayList<>();
        if (merged.has("libraries")) {
            for (JsonNode lib : merged.get("libraries")) {
                if (!RuleEvaluator.libraryAllowed(lib, os)) {
                    continue;
                }
                
                Path libPath = null;
                JsonNode downloads = lib.get("downloads");
                
                if (downloads != null && downloads.has("artifact")) {
                    libPath = librariesDir.resolve(downloads.get("artifact").get("path").asText());
                } else if (lib.has("name")) {
                    // Fallback: Resolver por nombre Maven (necesario para Forge 1.12.2 y librerías viejas)
                    libPath = librariesDir.resolve(nameToPath(lib.get("name").asText()));
                }

                if (libPath != null) {
                    cp.add(libPath);
                }
            }
        }
        cp.add(clientJar);
        return cp;
    }

    /** Convierte un nombre Maven (g:a:v) en una ruta de archivo. */
    private static String nameToPath(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) return name.replace(":", "_") + ".jar";

        String group = parts[0].replace(".", "/");
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? parts[3] : null;

        String filename = artifact + "-" + version + (classifier != null ? "-" + classifier : "") + ".jar";
        return group + "/" + artifact + "/" + version + "/" + filename;
    }

    private static String substitute(String s, Map<String, String> vars) {
        String out = s;
        for (var e : vars.entrySet()) {
            out = out.replace("${" + e.getKey() + "}", e.getValue());
        }
        if (out.contains("${")) {
            // Best-effort: leave remaining tokens empty to avoid broken launches on unknown placeholders
            out = out.replaceAll("\\$\\{[^}]+\\}", "");
        }
        return out;
    }

    private static List<String> splitMinecraftArguments(String raw) {
        List<String> parts = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return parts;
        }
        StringBuilder cur = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                quote = !quote;
            } else if (!quote && Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        return parts;
    }
}
