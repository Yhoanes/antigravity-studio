#!/bin/bash
# Test Harness - Automated Verification for SPEC-020
set -e

SPEC_FILE="specs/20-nova-tri-column-layout-projects-bridge-and-clean-identity.md"

echo "=== INICIANDO VERIFICACIÓN FORMAL DE ESPECIFICACIÓN SPEC-020 ==="

# 1. Verificar existencia de la especificación
echo -n "1. Comprobando existencia de SPEC-020... "
if [ ! -f "$SPEC_FILE" ]; then
  echo "FALLO: No existe $SPEC_FILE"; exit 1;
fi
echo "[OK]"

# 2. Verificar erradicación de marca Acode y vector de arranque
echo -n "2. Comprobando especificación de identidad limpia y logo.svg... "
grep -q "www/logo.svg" "$SPEC_FILE" || { echo "FALLO: Referencia a www/logo.svg ausente"; exit 1; }
grep -q "novaGlowGrad" "$SPEC_FILE" || { echo "FALLO: Definición de gradiente vectorial ausente"; exit 1; }
grep -q "logo.png" "$SPEC_FILE" || { echo "FALLO: Regla de erradicación de logo.png ausente"; exit 1; }
echo "[OK]"

# 3. Verificar eliminación de botón de cierre y diseño del toggler
echo -n "3. Comprobando eliminación de closeBtn y diseño de agentToggler... "
grep -q "agent-close-btn" "$SPEC_FILE" || { echo "FALLO: Regla de eliminación de closeBtn ausente"; exit 1; }
grep -q "agent-toggler-btn" "$SPEC_FILE" || { echo "FALLO: Especificación de toggler ausente"; exit 1; }
grep -q "thinking" "$SPEC_FILE" || { echo "FALLO: Estado thinking ausente"; exit 1; }
echo "[OK]"

# 4. Verificar desactivación de máscara y layout de 3 columnas
echo -n "4. Comprobando desactivación de .mask y layout de 3 columnas... "
grep -q ".mask" "$SPEC_FILE" || { echo "FALLO: Regla de desactivación de .mask ausente"; exit 1; }
grep -q "1024px" "$SPEC_FILE" || { echo "FALLO: Breakpoint 1024px ausente"; exit 1; }
grep -q "display: none !important" "$SPEC_FILE" || { echo "FALLO: Regla display:none ausente"; exit 1; }
grep -q "Tri-Column Layout" "$SPEC_FILE" || { echo "FALLO: Mención Tri-Column ausente"; exit 1; }
echo "[OK]"

# 5. Verificar puente de proyectos y sincronización de espacio de trabajo
echo -n "5. Comprobando anclaje automático de /storage/emulated/0/Projects... "
grep -q "/storage/emulated/0/Projects" "$SPEC_FILE" || { echo "FALLO: Ruta Projects ausente"; exit 1; }
grep -q "/home/studio/workspace" "$SPEC_FILE" || { echo "FALLO: Ruta workspace PRoot ausente"; exit 1; }
grep -q "ProjectsWorkspaceBridge" "$SPEC_FILE" || { echo "FALLO: Nombre de contrato ausente"; exit 1; }
echo "[OK]"

# 6. Verificar matriz de criterios de aceptación AC-TRI-*
echo -n "6. Comprobando criterios de aceptación AC-TRI-*... "
grep -q "AC-TRI-001" "$SPEC_FILE" || { echo "FALLO: AC-TRI-001 ausente"; exit 1; }
grep -q "AC-TRI-002" "$SPEC_FILE" || { echo "FALLO: AC-TRI-002 ausente"; exit 1; }
grep -q "AC-TRI-003" "$SPEC_FILE" || { echo "FALLO: AC-TRI-003 ausente"; exit 1; }
grep -q "AC-TRI-004" "$SPEC_FILE" || { echo "FALLO: AC-TRI-004 ausente"; exit 1; }
grep -q "AC-TRI-005" "$SPEC_FILE" || { echo "FALLO: AC-TRI-005 ausente"; exit 1; }
grep -q "AC-TRI-006" "$SPEC_FILE" || { echo "FALLO: AC-TRI-006 ausente"; exit 1; }
grep -q "AC-TRI-007" "$SPEC_FILE" || { echo "FALLO: AC-TRI-007 ausente"; exit 1; }
grep -q "AC-TRI-008" "$SPEC_FILE" || { echo "FALLO: AC-TRI-008 ausente"; exit 1; }
echo "[OK]"

# 7. Verificación técnica de implementación de código (AC-TRI-001 a AC-TRI-008)
echo -n "7. Comprobando implementación de código fuente... "
grep -q "novaGlowGrad" "nova-src/www/logo.svg" || { echo "FALLO: AC-TRI-001 novaGlowGrad ausente en www/logo.svg"; exit 1; }
[ ! -f "nova-src/src/components/logo/logo.png" ] || { echo "FALLO: AC-TRI-002 logo.png aun existe"; exit 1; }
[ $(grep -c "agent-close-btn" "nova-src/src/components/agentPanel/index.js") -eq 0 ] || { echo "FALLO: AC-TRI-003 agent-close-btn aun existe en agentPanel"; exit 1; }
grep -q "agent-toggler-btn" "nova-src/src/components/agentPanel/style.scss" || { echo "FALLO: AC-TRI-004 agent-toggler-btn ausente en style.scss"; exit 1; }
grep -q "pulse-stellar" "nova-src/src/components/agentPanel/style.scss" || { echo "FALLO: AC-TRI-004 pulse-stellar ausente en style.scss"; exit 1; }
grep -q "display: none !important" "nova-src/src/styles/wideScreen.scss" || { echo "FALLO: AC-TRI-005 display none !important ausente en wideScreen.scss"; exit 1; }
grep -q "Tri-Column Layout" "nova-src/src/styles/wideScreen.scss" || { echo "FALLO: AC-TRI-006 Tri-Column ausente en wideScreen.scss"; exit 1; }
grep -q "/storage/emulated/0/Projects" "nova-src/src/main.js" || { echo "FALLO: AC-TRI-007 Projects path ausente en main.js"; exit 1; }
grep -q "icon-nova-star" "nova-src/src/pages/welcome/welcome.js" || { echo "FALLO: AC-TRI-008 icon-nova-star ausente en welcome.js"; exit 1; }
! grep -q "icon acode" "nova-src/src/pages/welcome/welcome.js" || { echo "FALLO: AC-TRI-008 icon acode aun presente en welcome.js"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS DE VERIFICACIÓN DE SPEC-020 HAN PASADO EXITOSAMENTE ==="
