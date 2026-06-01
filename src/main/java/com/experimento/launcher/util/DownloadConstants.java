package com.experimento.launcher.util;

/**
 * Constantes compartidas para operaciones de descarga.
 * Unifica los tamaños de buffer en toda la aplicación para consistencia.
 */
public final class DownloadConstants {

    private DownloadConstants() {}

    /**
     * Tamaño del buffer para descargas de archivos (512KB).
     * Este tamaño es un equilibrio entre rendimiento y uso de memoria.
     */
    public static final int BUFFER_SIZE = 524288; // 512KB
}
