package com.experimento.launcher.service;

public final class HardwareProbe {

    private HardwareProbe() {}

    /** Best-effort total physical RAM in MiB. */
    public static long totalPhysicalRamMiB() {
        return SystemInfoService.getInfo().totalRamBytes() / (1024 * 1024);
    }

    public static int availableProcessors() {
        return SystemInfoService.getInfo().logicalCores();
    }
}
