package com.experimento.launcher.service;

import java.lang.management.ManagementFactory;

public final class HardwareProbe {

    private HardwareProbe() {}

    /** Best-effort total physical RAM in MiB. */
    public static long totalPhysicalRamMiB() {
        try {
            var osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sun) {
                long bytes = sun.getTotalMemorySize();
                if (bytes > 0) {
                    return bytes / (1024 * 1024);
                }
            }
        } catch (Throwable ignored) {
        }
        return 4096;
    }

    public static int availableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }
}
