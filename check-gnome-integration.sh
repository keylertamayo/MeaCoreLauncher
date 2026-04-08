#!/bin/bash

# Colores ANSI
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "--- Verificación de Integración GNOME ---"

# Detectar modo: ¿estamos corriendo desde el .deb instalado o desde dev?
DESKTOP_PATH="/usr/share/applications/meacorelauncher.desktop"
LOCAL_DESKTOP="src/main/resources/meacorelauncher.desktop"
DEV_MODE=false

if [ -f "$DESKTOP_PATH" ]; then
    echo -e "[${GREEN}PASS${NC}] Archivo .desktop encontrado en sistema: $DESKTOP_PATH"
    WM_CLASS_DESKTOP=$(grep "StartupWMClass" "$DESKTOP_PATH" | cut -d'=' -f2)
elif [ -f "$LOCAL_DESKTOP" ]; then
    echo -e "[${YELLOW}INFO${NC}] Modo desarrollo: usando .desktop local."
    DESKTOP_PATH="$LOCAL_DESKTOP"
    WM_CLASS_DESKTOP=$(grep "StartupWMClass" "$DESKTOP_PATH" | cut -d'=' -f2)
    DEV_MODE=true
else
    echo -e "[${RED}FAIL${NC}] Archivo .desktop NO encontrado en ninguna ruta."
    WM_CLASS_DESKTOP=""
fi

echo -e "[INFO] StartupWMClass del .desktop: ${GREEN}$WM_CLASS_DESKTOP${NC}"

# Verificar Icono
ICON_PATH="/usr/share/icons/hicolor/256x256/apps/meacorelauncher.png"
if [ -f "$ICON_PATH" ] || [ -f "/opt/meacorelauncher/lib/meacorelauncher.png" ]; then
    echo -e "[${GREEN}PASS${NC}] Recursos de ícono encontrados en el sistema."
else
    echo -e "[${YELLOW}INFO${NC}] Ícono de sistema no encontrado (normal si no está instalado el .deb)."
fi

# Buscar la ventana activa
echo -e "[INFO] Buscando ventana activa 'MeaCore Launcher'..."

# Obtener todos los IDs de ventana del cliente (no decoradores)
WINDOW_IDS=$(xprop -root _NET_CLIENT_LIST 2>/dev/null | grep -o '0x[0-9a-f]\+')
if [ -z "$WINDOW_IDS" ]; then
    WINDOW_IDS=$(xwininfo -tree -root 2>/dev/null | grep "MeaCore Launcher" | awk '{print $1}')
fi

FOUND=false
ACTUAL_WM_CLASS=""

for ID in $WINDOW_IDS; do
    NAME=$(xprop -id $ID WM_NAME 2>/dev/null | grep -o '".*"' | tr -d '"' | head -1)
    if [[ "$NAME" == *"MeaCore Launcher"* ]]; then
        WC=$(xprop -id $ID WM_CLASS 2>/dev/null)
        [ -z "$WC" ] && continue
        # Ignorar decoradores de Mutter
        [[ "$WC" == *"mutter-x11-frames"* ]] && continue

        INSTANCE_CLASS=$(echo "$WC" | awk -F '"' '{print $2}')
        CLASS_NAME=$(echo "$WC" | awk -F '"' '{print $4}')
        ACTUAL_WM_CLASS="$INSTANCE_CLASS"

        echo -e "[DEBUG] Ventana ID=$ID WM_CLASS -> Instancia='$INSTANCE_CLASS' Clase='$CLASS_NAME'"
        FOUND=true
        break
    fi
done

if [ "$FOUND" = false ]; then
    echo -e "[${YELLOW}WAIT${NC}] No se encontró la ventana. Asegúrate de que el launcher esté abierto."
    exit 0
fi

# Comparar
if [ "$ACTUAL_WM_CLASS" == "$WM_CLASS_DESKTOP" ]; then
    echo -e "[${GREEN}PASS${NC}] ¡WM_CLASS coincide! Integración GNOME correcta."
else
    if [ "$DEV_MODE" = true ]; then
        echo ""
        echo -e "[${YELLOW}INFO${NC}] En MODO DESARROLLO es normal ver discordancia."
        echo -e "  → JavaFX reporta: ${RED}$ACTUAL_WM_CLASS${NC}"
        echo -e "  → .desktop espera: ${GREEN}$WM_CLASS_DESKTOP${NC} (para el binario instalado)"
        echo ""
        echo -e "[${YELLOW}SOLUCIÓN${NC}]: Esto se resolverá automáticamente al instalar el .deb."
        echo "  Cuando la app corre via '/opt/meacorelauncher/bin/meacorelauncher',"
        echo "  el proceso se llama 'meacorelauncher' y GNOME lo asocia correctamente."
        echo ""
        echo -e "[${YELLOW}VERIFICACIÓN ALTERNATIVA EN DEV${NC}]: Comprueba que el .desktop de producción"
        echo "  tenga StartupWMClass=com.experimento.launcher.LauncherApp si quieres"
        echo "  probar la integración desde Gradle también."
    else
        echo -e "[${RED}FAIL${NC}] DISCORDANCIA en instalación de sistema."
        echo -e "  Esperado: '$WM_CLASS_DESKTOP'"
        echo -e "  Obtenido: '$ACTUAL_WM_CLASS'"
        echo "  Solución: Reinstala el .deb y verifica que jpackage usó --resource-dir."
    fi
fi

echo -e "-----------------------------------------"
