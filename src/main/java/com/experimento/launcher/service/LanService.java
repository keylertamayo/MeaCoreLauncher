package com.experimento.launcher.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LanService {

    /**
     * Intenta añadir reglas al Firewall de Windows para permitir el tráfico de Minecraft LAN.
     * Requiere permisos de administrador para tener éxito real.
     */
    public static void fixWindowsFirewallAsync(Path javaPath, Consumer<String> logger) {
        new Thread(() -> {
            try {
                if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                    logger.accept("[LAN] Esta función solo está disponible en Windows.");
                    return;
                }

                String path = javaPath.toAbsolutePath().toString();
                logger.accept("[LAN] Configurando Firewall para: " + path);

                // Comandos para permitir tráfico entrante y saliente para el ejecutable de Java
                // Usamos netsh advfirewall que es el estándar en Windows
                executeCommand(List.of("netsh", "advfirewall", "firewall", "add", "rule",
                        "name=MeaCore Minecraft LAN (In)", "dir=in", "action=allow",
                        "program=" + path, "enable=yes", "profile=any"), logger);

                executeCommand(List.of("netsh", "advfirewall", "firewall", "add", "rule",
                        "name=MeaCore Minecraft LAN (Out)", "dir=out", "action=allow",
                        "program=" + path, "enable=yes", "profile=any"), logger);

                logger.accept("[LAN] ¡Proceso completado! Si no funcionó, intenta ejecutar el launcher como Administrador.");
            } catch (Exception e) {
                logger.accept("[LAN] Error al configurar firewall: " + e.getMessage());
            }
        }).start();
    }

    private static void executeCommand(List<String> command, Consumer<String> logger) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        logger.accept("[Firewall] " + line.trim());
                    }
                }
            }
            int code = p.waitFor();
            if (code != 0) {
                logger.accept("[Firewall] El comando terminó con código " + code + ". ¿Tienes permisos de Admin?");
            }
        } catch (Exception e) {
            logger.accept("[Firewall] Excepción: " + e.getMessage());
        }
    }
}
