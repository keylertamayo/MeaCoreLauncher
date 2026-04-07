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

## Mejoras de Rendimiento (Auto-Optimización)
MeaCore no solo lanza el juego, sino que analiza la memoria del equipo subyacente para elegir el motor de *Garbage Collection* de Java adecuado, alojando dinámicamente recursos avanzados (como Generational ZGC) para reducir la asfixia de memoria en las versiones más modernas como la 1.21.
