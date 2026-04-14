package com.experimento.launcher.service;

import com.experimento.launcher.model.JvmPresetKind;
import com.experimento.launcher.model.LauncherProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes performance-oriented {@code options.txt} keys. Safe to run before launch.
 */
public final class AutoOptimizerService {

    private AutoOptimizerService() {}

    public static void applyOptionsTxt(Path gameDir, LauncherProfile profile, long totalRamMiB) throws Exception {
        Files.createDirectories(gameDir);
        Path opt = gameDir.resolve("options.txt");
        Map<String, String> existing = new LinkedHashMap<>();
        if (Files.isRegularFile(opt)) {
            for (String line : Files.readAllLines(opt)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int i = line.indexOf(':');
                if (i > 0) {
                    existing.put(line.substring(0, i), line.substring(i + 1));
                }
            }
        }
        JvmPresetKind kind =
                profile.jvmPreset == JvmPresetKind.AUTO
                        ? JvmPresetService.resolveAutoKind(totalRamMiB)
                        : profile.jvmPreset;
        Map<String, String> patch =
                switch (kind) {
                    case LOW -> lowOptions();
                    case BALANCED, AUTO -> balancedOptions();
                    case HIGH -> highOptions();
                };
        existing.putAll(patch);
        String body =
                existing.entrySet().stream()
                        .map(e -> e.getKey() + ":" + e.getValue())
                        .collect(Collectors.joining("\n"))
                        + "\n";
        Files.writeString(opt, body);
    }

    private static Map<String, String> lowOptions() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("renderDistance", "5");
        m.put("simulationDistance", "5");
        m.put("graphicsMode", "1");
        m.put("ao", "0");
        m.put("particles", "2");
        m.put("entityDistanceMul", "0.5");
        m.put("maxFps", "60");
        m.put("mipmapLevels", "2");
        m.put("biomeBlendRadius", "1");
        m.put("glDebugVerbosity", "0");
        m.put("useNativeTransport", "true");
        m.put("skipMultiplayerWarning", "true");
        m.put("reducedDebugInfo", "false");
        m.put("enableVsync", "false");
        return m;
    }

    private static Map<String, String> balancedOptions() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("renderDistance", "10");
        m.put("simulationDistance", "8");
        m.put("graphicsMode", "0");
        m.put("ao", "1");
        m.put("particles", "0");
        m.put("entityDistanceMul", "0.75");
        m.put("maxFps", "120");
        m.put("mipmapLevels", "4");
        m.put("biomeBlendRadius", "3");
        m.put("glDebugVerbosity", "0");
        m.put("useNativeTransport", "true");
        m.put("skipMultiplayerWarning", "true");
        m.put("enableVsync", "false");
        return m;
    }

    private static Map<String, String> highOptions() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("renderDistance", "16");
        m.put("simulationDistance", "12");
        m.put("graphicsMode", "0");
        m.put("ao", "2");
        m.put("particles", "0");
        m.put("entityDistanceMul", "1.0");
        m.put("maxFps", "260");
        m.put("mipmapLevels", "4");
        m.put("biomeBlendRadius", "5");
        m.put("glDebugVerbosity", "0");
        m.put("useNativeTransport", "true");
        m.put("skipMultiplayerWarning", "true");
        m.put("enableVsync", "false");
        return m;
    }

    public static String modSuggestionText(JvmPresetKind effective) {
        return switch (effective) {
            case LOW, AUTO -> "Rendimiento (FOSS): Fabric + Sodium + Lithium + FerriteCore (compatible con tu versión).";
            case BALANCED -> "Opcional: Fabric + Sodium para más FPS sin tocar el servidor.";
            case HIGH -> "Mods de rendimiento opcionales si usas muchos mods.";
        };
    }
}
