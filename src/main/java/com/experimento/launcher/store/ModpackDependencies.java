package com.experimento.launcher.store;

/**
 * Representa las dependencias necesarias para un modpack.
 * @param mcVersion Versión de Minecraft base (ej: "1.12.2")
 * @param loader Motor de mods (ej: "forge", "fabric", "quilt" o null para vanilla)
 */
public record ModpackDependencies(String mcVersion, String loader) {
}
