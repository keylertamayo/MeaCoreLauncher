package com.experimento.launcher.service;

import com.experimento.launcher.model.JvmPresetKind;
import com.experimento.launcher.model.LauncherProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class JvmPresetService {

    private JvmPresetService() {}

    public static JvmPresetKind resolveAutoKind(long totalRamMiB) {
        if (totalRamMiB <= 4 * 1024L) {
            return JvmPresetKind.LOW;
        }
        if (totalRamMiB <= 8 * 1024L) {
            return JvmPresetKind.BALANCED;
        }
        return JvmPresetKind.HIGH;
    }

    public static List<String> argsFor(LauncherProfile p, long totalRamMiB) {
        JvmPresetKind kind = p.jvmPreset == JvmPresetKind.AUTO ? resolveAutoKind(totalRamMiB) : p.jvmPreset;
        List<String> base =
                switch (kind) {
                    case LOW -> lowPreset();
                    case BALANCED -> balancedPreset(totalRamMiB);
                    case HIGH -> highPreset(totalRamMiB);
                    case AUTO -> lowPreset(); // resolved above; kept for exhaustiveness
                };
        List<String> out = new ArrayList<>(base);
        if (p.customJvmArgs != null && !p.customJvmArgs.isBlank()) {
            String custom = p.customJvmArgs.trim();

            // 1. Evitar 'Multiple garbage collectors selected'
            // Si el usuario ya definió un GC, quitamos el del preset automático
            if (custom.contains("-XX:+UseG1GC") || custom.contains("-XX:+UseZGC") || 
                custom.contains("-XX:+UseShenandoahGC") || custom.contains("-XX:+UseParallelGC")) {
                out.removeIf(arg -> arg.contains("-XX:+UseG1GC") || arg.contains("-XX:+UseZGC"));
            }

            // 2. Si el usuario define memoria manual, quitamos la automática
            if (custom.contains("-Xmx")) {
                out.removeIf(arg -> arg.startsWith("-Xmx"));
            }
            if (custom.contains("-Xms")) {
                out.removeIf(arg -> arg.startsWith("-Xms"));
            }

            out.addAll(Arrays.asList(custom.split("\\s+")));
        }
        return out;
    }

    /** Plan LOW: avoid Xms=Xmx on 4GB-class machines. */
    public static List<String> lowPreset() {
        return List.of(
                "-Xms512M",
                "-Xmx2G",
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=40", // Más agresivo para estabilidad
                "-XX:+UseStringDeduplication",
                "-XX:+UnlockExperimentalVMOptions");
    }

    public static List<String> balancedPreset(long totalRamMiB) {
        String mx = totalRamMiB >= 12 * 1024 ? "4G" : (totalRamMiB >= 8 * 1024 ? "3G" : "2G");
        return List.of(
                "-Xms2G",
                "-Xmx" + mx,
                "-XX:+UseG1GC",
                "-XX:+UseStringDeduplication", // Ahorro de RAM
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=20", // Target 60 FPS
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+DisableExplicitGC",
                "-XX:+AlwaysPreTouch",
                "-XX:G1NewSizePercent=30",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1HeapRegionSize=8M",
                "-XX:G1ReservePercent=20",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:InitiatingHeapOccupancyPercent=15",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5",
                "-XX:SurvivorRatio=32",
                "-XX:+PerfDisableSharedMem",
                "-XX:MaxTenuringThreshold=1");
    }

    public static List<String> highPreset(long totalRamMiB) {
        // En configuraciones al máximo para 1.21+, 4-6GB ahogan el GC. Permitimos mas respiro si el PC tiene RAM,
        // usando El Garbage Collector Z (Generacional) nativo de Java 21+ para latencia inferior al milisegundo.
        String mx = totalRamMiB >= 16 * 1024 ? "8G" : "6G";
        return List.of(
                "-Xms3G",
                "-Xmx" + mx,
                "-XX:+UseZGC",
                "-XX:+ZGenerational",
                "-XX:MaxGCPauseMillis=15", // Ultra latencia
                "-XX:+UseStringDeduplication",
                "-XX:+AlwaysPreTouch",
                "-XX:+DisableExplicitGC",
                "-XX:+PerfDisableSharedMem");
    }
}
