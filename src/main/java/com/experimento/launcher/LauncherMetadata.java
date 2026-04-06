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
    public static final String TECHNICAL_ID = "experimento-launcher";

    public static final String VERSION = "0.1.0-pre.1";

    /** Titular del software (licencia propietaria; ver LICENSE en la raíz del proyecto). */
    public static final String VENDOR = "MeaCore-Enterprise";

    public static final String VENDOR_LOCATION = "Arica, Chile";

    public static final String COPYRIGHT =
            "© MeaCore-Enterprise — Arica, Chile. Todos los derechos reservados.";

    private LauncherMetadata() {}
}
