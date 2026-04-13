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
        
        // Adoptium naming and general arch detection
        String a;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            a = "arm64";
        } else if (arch.contains("64")) {
            a = "x64";
        } else {
            a = "x86";
        }
        
        return new OsContext(name, a);
    }

    public boolean isWindows() {
        return "windows".equals(name);
    }

    public boolean isLinux() {
        return "linux".equals(name);
    }

    public String javaExecutableName() {
        return isWindows() ? "java.exe" : "java";
    }

    public String archiveExtension() {
        return isWindows() ? ".zip" : ".tar.gz";
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

