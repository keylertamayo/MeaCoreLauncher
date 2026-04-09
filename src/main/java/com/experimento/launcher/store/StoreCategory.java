package com.experimento.launcher.store;

public enum StoreCategory {
    MODPACK("modpack"),
    MOD("mod"),
    RESOURCEPACK("resourcepack"),
    SHADERPACK("shader"),
    MAP("datapack"); 

    private final String modrinthType;

    StoreCategory(String modrinthType) {
        this.modrinthType = modrinthType;
    }

    public String getModrinthType() {
        return modrinthType;
    }
}
