package com.experimento.launcher;

/**
 * Nombre y versión del producto (cambia aquí si quieres otro nombre comercial).
 */
public final class LauncherMetadata {

    /** Título de ventana y nombre visible para el usuario. */
    public static final String DISPLAY_NAME = "MeaCore Launcher";

    /**
     * Identificador corto sin espacios (p. ej. variable {@code launcher_name} que envía el cliente a Mojang).
     */
    public static final String TECHNICAL_ID = "meacore-launcher";

    public static final String VERSION;

    static {
        String v = "unknown";
        try (var in = LauncherMetadata.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                java.util.Properties p = new java.util.Properties();
                p.load(in);
                v = p.getProperty("version", "unknown");
            }
        } catch (Exception ignored) {}
        VERSION = v;
    }

    /** Titular del software (licencia propietaria; ver LICENSE en la raíz del proyecto). */
    public static final String VENDOR = "MeaCore-Enterprise";

    public static final String VENDOR_LOCATION = "Arica, Chile";

    public static final String COPYRIGHT =
            "© MeaCore-Enterprise — Arica, Chile. Todos los derechos reservados.";

    private LauncherMetadata() {}
}
