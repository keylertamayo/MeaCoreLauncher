package com.experimento.launcher.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestiona los mods instalados en la carpeta mods/ de una instancia.
 * Soporta activar/desactivar (extensión .jar.disabled) y eliminar permanentemente.
 */
public final class InstalledModsService {

    private InstalledModsService() {}

    /**
     * Representa un mod instalado (activo o desactivado).
     */
    public record InstalledMod(
            Path path,
            String displayName,
            long sizeBytes,
            boolean enabled
    ) {
        /** Tamaño del archivo formateado en B, KB o MB. */
        public String formattedSize() {
            if (sizeBytes < 1024L) return sizeBytes + " B";
            if (sizeBytes < 1024L * 1024) return String.format("%.1f KB", sizeBytes / 1024.0);
            return String.format("%.2f MB", sizeBytes / (1024.0 * 1024));
        }

        /** Nombre del archivo sin la extensión .disabled. */
        public String cleanName() {
            if (displayName.endsWith(".disabled")) {
                return displayName.substring(0, displayName.length() - ".disabled".length());
            }
            return displayName;
        }
    }

    /**
     * Escanea la carpeta mods/ y devuelve todos los mods encontrados.
     * Incluye .jar (activos) y .jar.disabled (desactivados).
     * Orden: activos primero, luego desactivados; ambos grupos alfabéticos.
     */
    public static List<InstalledMod> scanMods(Path modsDir) {
        List<InstalledMod> mods = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) return mods;

        try (var stream = Files.list(modsDir)) {
            stream.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return (n.endsWith(".jar") || n.endsWith(".jar.disabled"))
                                && !Files.isDirectory(p);
                    })
                    .sorted((a, b) -> {
                        boolean aEnabled = a.getFileName().toString().toLowerCase().endsWith(".jar");
                        boolean bEnabled = b.getFileName().toString().toLowerCase().endsWith(".jar");
                        if (aEnabled != bEnabled) return aEnabled ? -1 : 1;
                        return a.getFileName().toString()
                                .compareToIgnoreCase(b.getFileName().toString());
                    })
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        boolean active = name.toLowerCase().endsWith(".jar");
                        long size = 0;
                        try { size = Files.size(p); } catch (IOException ignored) {}
                        mods.add(new InstalledMod(p, name, size, active));
                    });
        } catch (IOException ignored) {}

        return mods;
    }

    /**
     * Activa un mod desactivado renombrando .jar.disabled → .jar.
     * Devuelve el nuevo Path del archivo.
     */
    public static Path enableMod(InstalledMod mod) throws IOException {
        if (mod.enabled()) return mod.path();
        String newName = mod.displayName().endsWith(".disabled")
                ? mod.displayName().substring(0, mod.displayName().length() - ".disabled".length())
                : mod.displayName();
        Path target = mod.path().resolveSibling(newName);
        Files.move(mod.path(), target);
        return target;
    }

    /**
     * Desactiva un mod activo renombrando .jar → .jar.disabled.
     * Devuelve el nuevo Path del archivo.
     */
    public static Path disableMod(InstalledMod mod) throws IOException {
        if (!mod.enabled()) return mod.path();
        Path target = mod.path().resolveSibling(mod.displayName() + ".disabled");
        Files.move(mod.path(), target);
        return target;
    }

    /**
     * Elimina permanentemente un mod del disco.
     */
    public static void deleteMod(InstalledMod mod) throws IOException {
        Files.deleteIfExists(mod.path());
    }
}
