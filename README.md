# MeaCore Launcher

Un launcher de Minecraft optimizado, rápido y con herramientas integradas de rendimiento diseñado para ofrecer la mejor experiencia out-of-the-box.

## Arquitectura de Ramas (Stable vs Beta)

Este repositorio utiliza un flujo de trabajo de dos ramas principales para mantener la estabilidad del código mientras desarrollamos nuevas funcionalidades:

### 🌿 Rama `Estable` (Actual)
Contiene la **versión base de producción**. En esta rama el entorno gráfico está construido puramente en JavaFX tradicional. 
Se caracteriza por:
- Máxima fiabilidad y cero *crashes* experimentales.
- Funcionalidades esenciales probadas (instalación segura nativa, configuración inicial en Java, soporte de perfiles base).
- Aquí **solo se hace push** de correcciones críticas (Hotfixes) procedentes de testeos ya validados.

### 🚀 Rama `Beta`
Es la rama de **desarrollo activo e innovación**. 
Se caracteriza por:
- Aquí se desarrollan interfaces web modernas incrustadas (React, Vite, WebViews), scripts asíncronos (`run.bat`/`run.sh`), y rediseños mayores de arquitectura.
- Puede contener bugs temporales en lo que se validan funcionalidades nuevas.
- Una vez que las innovaciones son 100% estables, se aplican a la rama base.

---

## Descarga e Instalación (Alfa 1.1.0)

¡Ya puedes descargar e instalar **MeaCore Launcher** de forma nativa! Los instaladores se generan automáticamente tras cada actualización importante.

### 🐧 Linux (Ubuntu/Debian)
1. Descarga el archivo `.deb` desde la sección de **Releases** (si viene en un `.zip`, descomprímelo primero).
2. Tienes dos opciones de instalación:
   - **Fácil**: Haz doble clic sobre el archivo `.deb` y pulsa "Instalar" en el Centro de Software.
   - **Terminal**: Abre una terminal en la carpeta y usa:
     ```bash
     sudo apt install ./meacorelauncher_1.1.0-1_amd64.deb
     ```
3. Busca "MeaCore Launcher" en tu menú de aplicaciones e inícialo.

### 🪟 Windows
1. Descarga el archivo `.msi` desde la sección de **Releases**.
2. Haz doble clic en el instalador y sigue las instrucciones del asistente.
3. El launcher creará un acceso directo en tu Escritorio y en el Menú de Inicio.

---

## Mejoras de Rendimiento (Auto-Optimización)
MeaCore no solo lanza el juego, sino que analiza la memoria del equipo subyacente para elegir el motor de *Garbage Collection* de Java adecuado, alojando dinámicamente recursos avanzados (como **ZGC Generacional** o **Aikar's Flags**) para reducir drásticamente el lag en versiones modernas de Minecraft (1.21+).
