# MeaCore Launcher

Launcher de **Minecraft Java Edition** en modo offline: interfaz **React (Vite)** embebida en **JavaFX WebView** con servidor HTTP local, o interfaz **JavaFX** nativa si no existe `frontend/dist/`.

**Versión actual del código:** `1.1.0-pre.1` (pre-release / beta).

### Estabilidad

La **única versión que el proyecto considera referencia estable** es la **primera release publicada**, etiqueta Git **`v0.1.0-pre.1`**. Todo lo construido después (incluida la rama actual y `v1.1.0-pre.1`) puede fallar en juego, UI o instalación: **se deja así**, sin garantía de corrección inmediata.

Si necesitas algo predecible, **usa el checkout o la etiqueta `v0.1.0-pre.1`**. Para fallos en builds nuevas, puedes **abrir un [Issue](../../issues)** (SO, Java, pasos), sin compromiso de arreglo.

## Requisitos

- **JDK 21** (coincide con la toolchain de Gradle).
- **Node.js** (para compilar el frontend).
- Conexión a Internet la primera vez que instalas una versión (descarga de Mojang).

## Compilar y ejecutar

```bash
# Frontend (producción)
cd frontend && npm install && npm run build && cd ..

# Launcher
./gradlew run
```

O el script de la raíz:

```bash
chmod +x run.sh
./run.sh
```

El WebView carga `http://127.0.0.1:<puerto>/` sirviendo `frontend/dist/`.

## Pruebas

```bash
./gradlew test
```

## Estructura relevante

| Ruta | Descripción |
|------|-------------|
| `src/main/java/com/experimento/launcher/` | App JavaFX, bridge JS↔Java, instalación Mojang |
| `frontend/src/` | UI React |
| `launcher-data/` | Datos en tiempo de ejecución (perfiles, `versions/`, `libraries/`, `assets/`) |

## Licencia

Ver [LICENSE](LICENSE). Metadatos del producto en `LauncherMetadata.java`.

## Más

- [CONTRIBUTING.md](CONTRIBUTING.md) — cómo colaborar e informar de errores.
