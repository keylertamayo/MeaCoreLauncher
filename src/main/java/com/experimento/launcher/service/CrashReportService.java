package com.experimento.launcher.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Analiza los crash reports de Minecraft y sugiere soluciones específicas.
 */
public final class CrashReportService {

    private CrashReportService() {}

    public record CrashAnalysis(String title, String cause, String suggestion, String rawContent) {}

    /**
     * Busca el crash report más reciente en el directorio de la instancia.
     */
    public static Optional<Path> findLatestCrashReport(Path gameDir) {
        Path crashDir = gameDir.resolve("crash-reports");
        if (!Files.isDirectory(crashDir)) return Optional.empty();

        try (Stream<Path> files = Files.list(crashDir)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .max(Comparator.comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (IOException e) { return 0L; }
                    }));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Analiza el contenido del crash report y devuelve un diagnóstico útil.
     */
    public static CrashAnalysis analyze(Path crashReport) {
        String content;
        try {
            content = Files.readString(crashReport);
        } catch (IOException e) {
            return new CrashAnalysis("No se pudo leer el crash report", "", "Comprueba que el archivo exista.", "");
        }

        String lower = content.toLowerCase();
        String snippet = content.length() > 3000 ? content.substring(0, 3000) : content;

        if (lower.contains("outofmemoryerror") || lower.contains("java heap space")) {
            return new CrashAnalysis(
                "Sin memoria (OutOfMemoryError)",
                "El juego se quedó sin RAM asignada.",
                "Ve a 'Config. Java' → selecciona el preset ALTO o aumenta la RAM manualmente en los argumentos JVM (-Xmx4G). Si tienes un modpack pesado como SkyFactory, necesitas al menos 6-8 GB.",
                snippet
            );
        }

        if (lower.contains("unsatisfiedlinkerror") || lower.contains("could not load") && lower.contains("native")) {
            return new CrashAnalysis(
                "Error de librería nativa",
                "Falta una librería del sistema o los nativos de Minecraft están corruptos.",
                "Reinstala la versión desde el botón 'Instalar'. Si el problema persiste, borra la carpeta 'natives' dentro de la versión.",
                snippet
            );
        }

        if (lower.contains("forgemod") || lower.contains("modloadingexception") || lower.contains("mod") && lower.contains("requires")) {
            return new CrashAnalysis(
                "Conflicto o dependencia de mods",
                "Un mod tiene dependencias faltantes o hay un conflicto entre mods.",
                "Revisa la consola: busca líneas con 'requires' o 'incompatible'. Asegúrate de tener instalado Fabric API si usas mods de Fabric. Prueba eliminando el último mod añadido.",
                snippet
            );
        }

        if (lower.contains("classnotfoundexception") || lower.contains("noclassdeffounderror")) {
            return new CrashAnalysis(
                "Clase Java no encontrada",
                "Un JAR del classpath está corrupto, faltante o es de una versión incompatible.",
                "Reinstala la versión con el botón 'Instalar'. Si usas mods, verifica que sean compatibles con tu versión de Minecraft y el modloader (Forge/Fabric/NeoForge).",
                snippet
            );
        }

        if (lower.contains("opengl") || lower.contains("lwjgl") || lower.contains("gl error")) {
            return new CrashAnalysis(
                "Error de OpenGL / LWJGL",
                "Fallo en la capa de renderizado 3D.",
                "Actualiza los drivers de tu GPU. Si usas una GPU integrada Intel, prueba añadir '-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true' en los argumentos JVM. También puedes intentar desactivar las sombras si tienes OptiFine instalado.",
                snippet
            );
        }

        if (lower.contains("connectionrefused") || lower.contains("failed to connect") || lower.contains("unknownhostexception")) {
            return new CrashAnalysis(
                "Error de conexión",
                "No se pudo conectar al servidor.",
                "Verifica que el servidor esté activo y que la IP/puerto sean correctos. Revisa tu firewall. En servidores Aternos, espera a que el servidor arranque completamente antes de unirte.",
                snippet
            );
        }

        if (lower.contains("java.lang.stackoverflowerror")) {
            return new CrashAnalysis(
                "Stack Overflow",
                "Un mod entró en un bucle recursivo infinito.",
                "Prueba deshabilitar mods uno a uno para identificar el culpable. Este error suele ser un bug del mod, no del juego base.",
                snippet
            );
        }

        if (lower.contains("java.lang.illegalstateexception") && lower.contains("entity")) {
            return new CrashAnalysis(
                "Error de estado en entidad",
                "Una entidad del mundo causó un estado ilegal (bug en mod o mundo corrupto).",
                "Prueba cargar el mundo en modo un jugador y ejecuta '/kill @e[type=!player]' para eliminar entidades problemáticas. Si el error es solo en un chunk, el mod WorldEdit puede borrarlo.",
                snippet
            );
        }

        if (lower.contains("mixinexception") || lower.contains("mixin")) {
            return new CrashAnalysis(
                "Error de Mixin",
                "Un mod está intentando modificar código de Minecraft de forma incompatible.",
                "Este error generalmente indica que un mod no es compatible con tu versión de Minecraft o con otro mod. Revisa si hay actualizaciones del mod afectado. El mod que aparece en el stack trace es el responsable.",
                snippet
            );
        }

        String firstExceptionLine = "Desconocida";
        for (String line : content.split("\n")) {
            if (line.contains("Exception") || line.contains("Error:")) {
                firstExceptionLine = line.trim();
                break;
            }
        }

        return new CrashAnalysis(
            "Crash desconocido",
            firstExceptionLine,
            "Revisa la consola completa. Puedes buscar el error en Google o en los foros de soporte del modpack. El crash report se ha guardado en la carpeta crash-reports de tu instancia.",
            snippet
        );
    }
}
