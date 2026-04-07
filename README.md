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

## Instalar desde el paquete `.tar` (distribución Gradle)

Genera el archivo en tu máquina (o descarga el `.tar` de una release, si se publica):

```bash
./gradlew distTar
```

El archivo queda en:

`build/distributions/experimento-launcher-<versión>.tar`

**Linux / macOS**

```bash
cd build/distributions   # o la carpeta donde descargaste el .tar
tar -xvf experimento-launcher-*.tar
cd experimento-launcher-<versión>   # nombre exacto de la carpeta (p. ej. experimento-launcher-1.1.0-pre.1)
chmod +x bin/experimento-launcher
./bin/experimento-launcher
```

Si `cd experimento-launcher-*` devuelve «demasiados argumentos», en esa carpeta hay **varias** carpetas que coinciden con el patrón; entra con el nombre concreto que mostró `tar` (tabulador ayuda).

- Necesitas **Java 21** en el `PATH` (`java -version`). OpenJFX va en `lib/` y el script de `bin/` ya arranca el JVM con `--module-path` / `--add-modules`; **no** hace falta instalar JavaFX aparte del JDK.
- Ejecuta el script **desde la carpeta del paquete** (usa rutas relativas a `lib/`).
- Coloca **`frontend/dist/`** compilado junto al directorio de trabajo del proceso si quieres la UI web (misma regla que al desarrollar).

**Windows:** descomprime el `.zip` equivalente (`distZip`) y usa `bin\experimento-launcher.bat`.

## Instalar / ejecutar el `.jar`

Hay dos casos distintos:

### 1) `.jar` “fino” (`./gradlew jar`)

Salida típica: `build/libs/experimento-launcher-<versión>.jar`.

Ese JAR **no incluye dependencias ni JavaFX empaquetadas**: **no** es un `java -jar` usable solo. Para ejecutar el proyecto usa **`./gradlew run`**, el **`.tar`** de arriba o un instalador que ya traiga el classpath.

### 2) `.jar` ejecutable “todo en uno” (fat / sombra)

Si una **release** publica un único `.jar` ejecutable (dependencias embebidas), instala **JDK 21**, coloca el `.jar` donde quieras y:

```bash
java -jar experimento-launcher-<versión>.jar
```

(Solo aplica a ese tipo de artefacto; el build por defecto del repo no genera fat-jar.)

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

- [CONTRIBUTING.md](CONTRIBUTING.md) — cómo colaborar, informar de errores y **qué rama usar** (`Estable` vs `Beta`).
