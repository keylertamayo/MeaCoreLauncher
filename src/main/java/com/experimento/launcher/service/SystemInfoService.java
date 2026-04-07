package com.experimento.launcher.service;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

public final class SystemInfoService {

    private static final SystemInfo SI = new SystemInfo();
    private static final HardwareAbstractionLayer HAL = SI.getHardware();
    private static final OperatingSystem OS = SI.getOperatingSystem();

    public record HardwareInfo(
            String cpuName,
            int physicalCores,
            int logicalCores,
            long totalRamBytes,
            long availableRamBytes,
            String osName,
            long diskTotalBytes,
            long diskFreeBytes
    ) {}

    public static HardwareInfo getInfo() {
        CentralProcessor cpu = HAL.getProcessor();
        GlobalMemory mem = HAL.getMemory();
        
        File root = new File("/");
        long diskTotal = root.getTotalSpace();
        long diskFree = root.getFreeSpace();

        return new HardwareInfo(
                cpu.getProcessorIdentifier().getName(),
                cpu.getPhysicalProcessorCount(),
                cpu.getLogicalProcessorCount(),
                mem.getTotal(),
                mem.getAvailable(),
                OS.toString(),
                diskTotal,
                diskFree
        );
    }

    public static void collectTelemetry(Path logPath) {
        try {
            HardwareInfo info = getInfo();
            String logEntry = String.format(
                "[%s] Telemetría de Inicio:\n- CPU: %s (%d nucleos)\n- RAM: %.2f GB / %.2f GB\n- Disco: %.2f GB Libres de %.2f GB\n- SO: %s\n------------------------\n",
                LocalDateTime.now(),
                info.cpuName(), info.physicalCores(),
                (info.totalRamBytes() - info.availableRamBytes()) / 1e9, info.totalRamBytes() / 1e9,
                info.diskFreeBytes() / 1e9, info.diskTotalBytes() / 1e9,
                info.osName()
            );
            Files.writeString(logPath, logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("Error al recopilar telemetría: " + e.getMessage());
        }
    }
}
