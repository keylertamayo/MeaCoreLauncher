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
            out.addAll(Arrays.asList(p.customJvmArgs.trim().split("\\s+")));
        }
        return out;
    }

    /** Plan LOW: avoid Xms=Xmx on 4GB-class machines. */
    public static List<String> lowPreset() {
        return List.of(
                "-Xms512M",
                "-Xmx2G",
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=100",
                "-XX:+UnlockExperimentalVMOptions");
    }

    public static List<String> balancedPreset(long totalRamMiB) {
        String mx = totalRamMiB >= 12 * 1024 ? "4G" : "3G";
        return List.of(
                "-Xms1G",
                "-Xmx" + mx,
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=100",
                "-XX:+UnlockExperimentalVMOptions");
    }

    public static List<String> highPreset(long totalRamMiB) {
        String mx = totalRamMiB >= 16 * 1024 ? "6G" : "4G";
        return List.of(
                "-Xms2G",
                "-Xmx" + mx,
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=100",
                "-XX:+UnlockExperimentalVMOptions");
    }
}
