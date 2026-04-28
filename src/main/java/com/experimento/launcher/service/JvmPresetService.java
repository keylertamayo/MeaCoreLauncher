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
        return argsFor(p, totalRamMiB, HardwareProbe.physicalCores(), HardwareProbe.availableProcessors());
    }

    public static List<String> argsFor(LauncherProfile p, long totalRamMiB, int physCores, int logicCores) {
        JvmPresetKind kind = p.jvmPreset == JvmPresetKind.AUTO ? resolveAutoKind(totalRamMiB) : p.jvmPreset;
        List<String> base =
                switch (kind) {
                    case LOW -> lowPreset(physCores, logicCores);
                    case BALANCED -> balancedPreset(totalRamMiB, physCores, logicCores);
                    case HIGH -> highPreset(totalRamMiB, physCores, logicCores);
                    case AUTO -> lowPreset(physCores, logicCores);
                };
        List<String> out = new ArrayList<>(base);
        if (p.customJvmArgs != null && !p.customJvmArgs.isBlank()) {
            String custom = p.customJvmArgs.trim();

            if (custom.contains("-XX:+UseG1GC") || custom.contains("-XX:+UseZGC") ||
                custom.contains("-XX:+UseShenandoahGC") || custom.contains("-XX:+UseParallelGC")) {
                out.removeIf(arg -> arg.contains("-XX:+UseG1GC") || arg.contains("-XX:+UseZGC"));
            }

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

    public static List<String> lowPreset(int physCores, int logicCores) {
        List<String> args = new ArrayList<>(List.of(
                "-Xms512M",
                "-Xmx2G",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=35",
                "-XX:G1NewSizePercent=20",
                "-XX:G1MaxNewSizePercent=30",
                "-XX:G1HeapRegionSize=8M",
                "-XX:+UseStringDeduplication",
                "-XX:+ParallelRefProcEnabled",
                "-XX:+DisableExplicitGC",
                "-XX:+PerfDisableSharedMem",
                "-Dlog4j2.formatMsgNoLookups=true",
                "-Djdk.nio.maxCachedBufferSize=262144",
                "-Djava.net.preferIPv4Stack=true",
                "-Dfile.encoding=UTF-8"));
        
        applyCpuArgs(args, physCores, logicCores);
        return args;
    }

    public static List<String> balancedPreset(long totalRamMiB, int physCores, int logicCores) {
        String mx;
        if (totalRamMiB >= 16 * 1024L) mx = "6G";
        else if (totalRamMiB >= 12 * 1024L) mx = "5G";
        else if (totalRamMiB >= 8 * 1024L) mx = "4G";
        else mx = "3G";

        List<String> args = new ArrayList<>(List.of(
                "-Xms2G",
                "-Xmx" + mx,
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseG1GC",
                "-XX:+UseStringDeduplication",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=20",
                "-XX:+DisableExplicitGC",
                "-XX:+AlwaysPreTouch",
                "-XX:G1NewSizePercent=30",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1HeapRegionSize=16M",
                "-XX:G1ReservePercent=20",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:InitiatingHeapOccupancyPercent=15",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5",
                "-XX:SurvivorRatio=32",
                "-XX:+PerfDisableSharedMem",
                "-XX:MaxTenuringThreshold=1",
                "-XX:+UseNUMA",
                "-Dlog4j2.formatMsgNoLookups=true",
                "-Djdk.nio.maxCachedBufferSize=262144",
                "-Djava.net.preferIPv4Stack=true",
                "-Dfile.encoding=UTF-8"));

        applyCpuArgs(args, physCores, logicCores);
        return args;
    }

    public static List<String> highPreset(long totalRamMiB, int physCores, int logicCores) {
        String mx;
        if (totalRamMiB >= 32 * 1024L) mx = "12G";
        else if (totalRamMiB >= 16 * 1024L) mx = "8G";
        else mx = "6G";

        String ms;
        if (totalRamMiB >= 16 * 1024L) ms = "4G";
        else ms = "3G";

        List<String> args = new ArrayList<>(List.of(
                "-Xms" + ms,
                "-Xmx" + mx,
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseG1GC",
                "-XX:+UseStringDeduplication",
                "-XX:+AlwaysPreTouch",
                "-XX:+DisableExplicitGC",
                "-XX:+PerfDisableSharedMem",
                "-XX:+UseNUMA",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=10",
                "-XX:G1NewSizePercent=30",
                "-XX:G1MaxNewSizePercent=40",
                "-Dlog4j2.formatMsgNoLookups=true",
                "-Djdk.nio.maxCachedBufferSize=262144",
                "-Dfile.encoding=UTF-8"));

        applyCpuArgs(args, physCores, logicCores);
        return args;
    }

    private static void applyCpuArgs(List<String> args, int physCores, int logicCores) {
        // Hilos de GC Paralelo: idealmente 1 por núcleo físico, cap de 12 para estabilidad
        int parallelGC = Math.max(2, Math.min(physCores, 12));
        // Hilos de GC Concurrente: 1/4 de los hilos paralelos
        int concGC = Math.max(1, parallelGC / 4);
        // CICompilerCount: hilos para compilación JIT (Jave se encarga de repartirlos entre C1 y C2)
        int ciCompiler = Math.max(2, Math.min(logicCores / 2, 8));

        args.add("-XX:ParallelGCThreads=" + parallelGC);
        args.add("-XX:ConcGCThreads=" + concGC);
        args.add("-XX:G1ConcRefinementThreads=" + parallelGC);
        args.add("-XX:CICompilerCount=" + ciCompiler);
        
        // Forzar a la JVM a reconocer la cantidad exacta de cores si detectamos discrepancias
        args.add("-XX:ActiveProcessorCount=" + logicCores);
    }
}
