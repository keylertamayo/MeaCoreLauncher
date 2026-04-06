package com.experimento.launcher.mojang;

import java.util.Locale;

public record OsContext(String name, String arch) {
    public static OsContext current() {
        String os = System.getProperty("os.name", "linux").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "amd64").toLowerCase(Locale.ROOT);
        String name;
        if (os.contains("win")) {
            name = "windows";
        } else if (os.contains("mac") || os.contains("darwin")) {
            name = "osx";
        } else {
            name = "linux";
        }
        String a =
                (arch.contains("aarch64") || arch.contains("arm64")) ? "arm64" : "x86";
        return new OsContext(name, a);
    }

    public String nativeClassifier() {
        if ("osx".equals(name)) {
            return "arm64".equals(arch) ? "natives-macos-arm64" : "natives-macos";
        }
        if ("windows".equals(name)) {
            return "natives-windows";
        }
        return "natives-linux";
    }
}
