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

    private static volatile HardwareInfo cachedInfo = null;

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
        if (cachedInfo != null) {
            CentralProcessor cpu = HAL.getProcessor();
            GlobalMemory mem = HAL.getMemory();
            return new HardwareInfo(
                    cachedInfo.cpuName(),
                    cachedInfo.physicalCores(),
                    cachedInfo.logicalCores(),
                    mem.getTotal(),
                    mem.getAvailable(),
                    cachedInfo.osName(),
                    cachedInfo.diskTotalBytes(),
                    cachedInfo.diskFreeBytes()
            );
        }
        return refreshCache();
    }

    public static HardwareInfo refreshCache() {
        CentralProcessor cpu = HAL.getProcessor();
        GlobalMemory mem = HAL.getMemory();

        File root = new File(System.getProperty("user.home"));
        long diskTotal = root.getTotalSpace();
        long diskFree = root.getFreeSpace();

        HardwareInfo info = new HardwareInfo(
                cpu.getProcessorIdentifier().getName(),
                cpu.getPhysicalProcessorCount(),
                cpu.getLogicalProcessorCount(),
                mem.getTotal(),
                mem.getAvailable(),
                OS.toString(),
                diskTotal,
                diskFree
        );
        cachedInfo = info;
        return info;
    }

    public static void collectTelemetry(Path logPath) {
        try {
            HardwareInfo info = refreshCache();
            String logEntry = String.format(
                "[%s] Telemetría de Inicio:\n- CPU: %s (%d núcleos físicos / %d lógicos)\n- RAM: %.2f GB total / %.2f GB disponibles\n- Disco: %.2f GB Libres de %.2f GB\n- SO: %s\n- Java: %s %s\n------------------------\n",
                LocalDateTime.now(),
                info.cpuName(), info.physicalCores(), info.logicalCores(),
                info.totalRamBytes() / 1e9, info.availableRamBytes() / 1e9,
                info.diskFreeBytes() / 1e9, info.diskTotalBytes() / 1e9,
                info.osName(),
                System.getProperty("java.version"), System.getProperty("java.vendor")
            );
            Files.writeString(logPath, logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("Error al recopilar telemetría: " + e.getMessage());
        } finally {
            System.out.println("[MeaCore] Recopilación de hardware completada.");
        }
    }
}
