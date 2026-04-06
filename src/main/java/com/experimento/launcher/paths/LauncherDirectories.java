package com.experimento.launcher.paths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LauncherDirectories {

    private final Path root;

    public LauncherDirectories(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public static LauncherDirectories fromDefault() {
        String override = System.getProperty("launcher.root");
        Path root = override != null && !override.isBlank()
                ? Paths.get(override)
                : Paths.get(System.getProperty("user.dir"));
        return new LauncherDirectories(root);
    }

    public Path root() {
        return root;
    }

    public Path launcherData() {
        return root.resolve("launcher-data");
    }

    public Path profilesDir() {
        return launcherData().resolve("profiles");
    }

    public Path versionsDir() {
        return launcherData().resolve("versions");
    }

    public Path librariesDir() {
        return launcherData().resolve("libraries");
    }

    public Path assetsDir() {
        return launcherData().resolve("assets");
    }

    public Path instancesDir() {
        return root.resolve("instances");
    }

    public Path instanceGameDir(String instanceId) {
        return instancesDir().resolve(safeSegment(instanceId));
    }

    public void ensureBaseDirs() throws Exception {
        Files.createDirectories(profilesDir());
        Files.createDirectories(versionsDir());
        Files.createDirectories(librariesDir());
        Files.createDirectories(assetsDir());
        Files.createDirectories(instancesDir());
    }

    private static String safeSegment(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("instance id required");
        }
        if (id.contains("/") || id.contains("\\") || id.contains("..")) {
            throw new IllegalArgumentException("invalid instance id");
        }
        return id;
    }
}
