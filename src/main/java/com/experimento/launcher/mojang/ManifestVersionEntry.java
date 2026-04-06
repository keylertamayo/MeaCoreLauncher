package com.experimento.launcher.mojang;

public record ManifestVersionEntry(String id, String type, long releasedAtMs) {
    public String label() {
        if (type == null || type.isBlank()) {
            return id;
        }
        return id + "  [" + type + "]";
    }
}
