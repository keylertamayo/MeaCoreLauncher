package com.experimento.launcher.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Optional read of {@code ~/.tlauncher/tlauncher-2.0.properties} for suggested {@code minecraft.args}.
 */
public final class TlauncherConfigReader {

    private TlauncherConfigReader() {}

    public static String suggestedJvmArgsFromTlauncher() {
        Path p = Path.of(System.getProperty("user.home"), ".tlauncher", "tlauncher-2.0.properties");
        if (!Files.isRegularFile(p)) {
            return "";
        }
        try {
            Properties props = new Properties();
            try (var in = Files.newInputStream(p)) {
                props.load(in);
            }
            String args = props.getProperty("minecraft.args", "");
            return args == null ? "" : args.replace("\\:", ":");
        } catch (Exception e) {
            return "";
        }
    }
}
