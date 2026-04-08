#!/bin/bash
echo "[MeaCore] Limpiando y empaquetando JAR..."
./gradlew clean jar

APP_VERSION=$(grep 'version=' src/main/resources/version.properties | cut -d'=' -f2)
if [ -z "$APP_VERSION" ]; then
    APP_VERSION="1.1.10"
fi

echo "[MeaCore] Generando instalador nativo .deb (Versión: $APP_VERSION)..."

# Asegurar que jpackage esté disponible
if ! command -v jpackage &> /dev/null; then
    echo "ERROR: 'jpackage' no está instalado. Instala openjdk-21-jdk completo."
    exit 1
fi

jpackage \
  --type deb \
  --name "MeaCore Launcher" \
  --app-version "$APP_VERSION" \
  --icon icon.png \
  --input build/libs \
  --main-jar meacore-launcher-${APP_VERSION}.jar \
  --main-class com.experimento.launcher.Main \
  --linux-shortcut \
  --linux-menu-group "Game" \
  --linux-app-category "Game" \
  --description "Tu plataforma definitiva para inyectar mods de Minecraft" \
  --vendor "MeaCore"

echo "[MeaCore] ¡Paquete .deb generado con éxito!"
