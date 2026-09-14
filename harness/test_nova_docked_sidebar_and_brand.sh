#!/bin/bash
# Test Harness - Automated Verification for SPEC-019 & TASK-021
set -e

SPEC_FILE="specs/19-nova-docked-sidebar-keepalive-and-brand-identity.md"
MAIN_JS="nova-src/src/main.js"
AGENT_PANEL_JS="nova-src/src/components/agentPanel/index.js"
AGENT_PANEL_SCSS="nova-src/src/components/agentPanel/style.scss"
WIDE_SCREEN_SCSS="nova-src/src/styles/wideScreen.scss"
INIT_ALPINE="nova-src/src/plugins/terminal/scripts/init-alpine.sh"
FOREGROUND_XML="nova-src/res/android/drawable/ic_launcher_foreground.xml"
BACKGROUND_XML="nova-src/res/android/drawable/ic_launcher_background.xml"
CONFIG_XML="nova-src/config.xml"

echo "=== INICIANDO VERIFICACIÓN FORMAL DE SPEC-019 Y TASK-021 ==="

# 1. Verificar existencia de la especificación
echo -n "1. Comprobando existencia de SPEC-019... "
if [ ! -f "$SPEC_FILE" ]; then
  echo "FALLO: No existe $SPEC_FILE"; exit 1;
fi
echo "[OK]"

# 2. Verificar especificación del isotipo y marca (< ✦ >)
echo -n "2. Comprobando especificación e implementación del isotipo oficial (< ✦ >)... "
grep -q "< ✦ >" "$SPEC_FILE" || { echo "FALLO: Isotipo < ✦ > ausente en spec"; exit 1; }
grep -q "agent-toggler" "$MAIN_JS" || { echo "FALLO: agent-toggler ausente en main.js"; exit 1; }
grep -q "agent-logo-star" "$MAIN_JS" || { echo "FALLO: agent-logo-star ausente en main.js"; exit 1; }
grep -q "✦" "$MAIN_JS" || { echo "FALLO: estrella ✦ ausente en main.js"; exit 1; }
grep -q "00F0FF" "$FOREGROUND_XML" || grep -q "00f0ff" "$FOREGROUND_XML" || { echo "FALLO: Supernova Cyan ausente en foreground icon"; exit 1; }
grep -q "8B5CF6" "$FOREGROUND_XML" || grep -q "8b5cf6" "$FOREGROUND_XML" || { echo "FALLO: Stellar Violet ausente en foreground icon"; exit 1; }
grep -q "090D16" "$BACKGROUND_XML" || grep -q "090d16" "$BACKGROUND_XML" || { echo "FALLO: Deep Void ausente en background icon"; exit 1; }
echo "[OK]"

# 3. Verificar diseño Docked Sidebar y tirador de arrastre #agent-resize-handle
echo -n "3. Comprobando contratos de Docked Sidebar y #agent-resize-handle... "
grep -q "agent-resize-handle" "$AGENT_PANEL_JS" || { echo "FALLO: #agent-resize-handle ausente en index.js"; exit 1; }
grep -q "setPointerCapture" "$AGENT_PANEL_JS" || { echo "FALLO: setPointerCapture ausente en index.js"; exit 1; }
grep -q "0.25" "$AGENT_PANEL_JS" || { echo "FALLO: límite 25vw ausente en index.js"; exit 1; }
grep -q "0.75" "$AGENT_PANEL_JS" || { echo "FALLO: límite 75vw ausente en index.js"; exit 1; }
grep -q "nova:agent-panel-width" "$AGENT_PANEL_JS" || { echo "FALLO: clave de almacenamiento ausente"; exit 1; }
grep -q "has-docked-agent" "$WIDE_SCREEN_SCSS" || { echo "FALLO: regla has-docked-agent ausente en wideScreen.scss"; exit 1; }
echo "[OK]"

# 4. Verificar invariante Keep-Alive de terminal
echo -n "4. Comprobando ciclo de vida Keep-Alive... "
grep -q "isConnected" "$AGENT_PANEL_JS" || { echo "FALLO: chequeo isConnected ausente"; exit 1; }
grep -q "READY" "$AGENT_PANEL_JS" || { echo "FALLO: estado READY ausente"; exit 1; }
echo "[OK]"

# 5. Verificar ergonomía de 80 columnas y tipografía
echo -n "5. Comprobando ergonomía de 80 columnas y tipografía adaptativa... "
grep -q "updateAdaptiveTypography" "$AGENT_PANEL_JS" || { echo "FALLO: updateAdaptiveTypography ausente"; exit 1; }
grep -q "ResizeObserver" "$AGENT_PANEL_JS" || { echo "FALLO: ResizeObserver ausente en index.js"; exit 1; }
grep -q "desiredCols = 80" "$AGENT_PANEL_JS" || { echo "FALLO: 80 columnas no configuradas en index.js"; exit 1; }
echo "[OK]"

# 6. Verificar supresión de advertencias PRoot bionic de grupos
echo -n "6. Comprobando supresión de advertencias de grupos Android... "
grep -q "aid_inet" "$INIT_ALPINE" || { echo "FALLO: aid_inet ausente en init-alpine.sh"; exit 1; }
grep -q "aid_everybody" "$INIT_ALPINE" || { echo "FALLO: aid_everybody ausente en init-alpine.sh"; exit 1; }
grep -q "/etc/group" "$INIT_ALPINE" || { echo "FALLO: inyección /etc/group ausente en init-alpine.sh"; exit 1; }
echo "[OK]"

# 7. Verificar version bump v1.0.12 (versionCode 10013)
echo -n "7. Comprobando version bump a v1.0.12 (versionCode 10013)... "
grep -q 'version="1.0.12"' "$CONFIG_XML" || { echo "FALLO: versión 1.0.12 ausente en config.xml"; exit 1; }
grep -q 'android-versionCode="10013"' "$CONFIG_XML" || { echo "FALLO: versionCode 10013 ausente en config.xml"; exit 1; }
echo "[OK]"

# 8. Verificar criterios de aceptación AC-DOCKED-* en SPEC
echo -n "8. Comprobando criterios de aceptación AC-BRAND-*, AC-DOCK-*, AC-KEEPALIVE-*... "
grep -q "AC-BRAND-001" "$SPEC_FILE" || { echo "FALLO: AC-BRAND-001 ausente"; exit 1; }
grep -q "AC-DOCK-001" "$SPEC_FILE" || { echo "FALLO: AC-DOCK-001 ausente"; exit 1; }
grep -q "AC-RESIZE-001" "$SPEC_FILE" || { echo "FALLO: AC-RESIZE-001 ausente"; exit 1; }
grep -q "AC-KEEPALIVE-001" "$SPEC_FILE" || { echo "FALLO: AC-KEEPALIVE-001 ausente"; exit 1; }
grep -q "AC-TYPO-001" "$SPEC_FILE" || { echo "FALLO: AC-TYPO-001 ausente"; exit 1; }
grep -q "AC-GROUPS-001" "$SPEC_FILE" || { echo "FALLO: AC-GROUPS-001 ausente"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS DE VERIFICACIÓN DE SPEC-019 Y TASK-021 HAN PASADO EXITOSAMENTE ==="
