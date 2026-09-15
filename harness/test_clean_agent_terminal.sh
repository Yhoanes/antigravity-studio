#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-031 4-Way D-Pad Gesture Navigation Verification
# ==============================================================================
set -e

SPEC_FILE_031="specs/31-horizontal-gesture-navigation-4-way-dpad.md"
TOUCH_NAV_JS="nova-src/src/antigravity2/TerminalTouchNavigation.js"
CLEAN_TERM_JS="nova-src/src/antigravity2/CleanAgentTerminal.js"
CLEAN_TERM_SCSS="nova-src/src/antigravity2/clean-terminal.scss"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"

echo "=== INICIANDO VALIDACIÓN FORMAL DE SPEC-031 ==="

# 1. Verificar documento SPEC-031
echo -n "1. Verificando existencia de documento SPEC-031... "
[ -f "$SPEC_FILE_031" ] || { echo "FALLO: No existe $SPEC_FILE_031"; exit 1; }
echo "[OK]"

# 2. Verificar emulación horizontal ArrowLeft y ArrowRight (Criterios DPAD-01 y DPAD-02)
echo -n "2. Verificando emulación horizontal ArrowLeft y ArrowRight... "
grep -q "\\\\x1b\\[D" "$TOUCH_NAV_JS" || grep -q "ArrowLeft" "$TOUCH_NAV_JS" || { echo "FALLO: No se encuentra emisión de ArrowLeft"; exit 1; }
grep -q "\\\\x1b\\[C" "$TOUCH_NAV_JS" || grep -q "ArrowRight" "$TOUCH_NAV_JS" || { echo "FALLO: No se encuentra emisión de ArrowRight"; exit 1; }
grep -q "onArrowLeft" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no enlaza onArrowLeft"; exit 1; }
grep -q "onArrowRight" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no enlaza onArrowRight"; exit 1; }
echo "[OK]"

# 3. Verificar preservación de emulación vertical (Criterio DPAD-03)
echo -n "3. Verificando preservación de flechas verticales... "
grep -q "\\\\x1b\\[A" "$TOUCH_NAV_JS" || grep -q "ArrowUp" "$TOUCH_NAV_JS" || { echo "FALLO: No se encuentra emisión de ArrowUp"; exit 1; }
grep -q "\\\\x1b\\[B" "$TOUCH_NAV_JS" || grep -q "ArrowDown" "$TOUCH_NAV_JS" || { echo "FALLO: No se encuentra emisión de ArrowDown"; exit 1; }
grep -q "onArrowUp" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no enlaza onArrowUp"; exit 1; }
grep -q "onArrowDown" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no enlaza onArrowDown"; exit 1; }
echo "[OK]"

# 4. Verificar algoritmo de bloqueo de eje (Criterio DPAD-04)
echo -n "4. Verificando bloqueo cinemático de eje (Axis-Locking)... "
grep -q "axisLock" "$TOUCH_NAV_JS" || grep -q "lockedAxis" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no implementa axisLock"; exit 1; }
echo "[OK]"

# 5. Verificar supresión forzada de sidebar (Criterio DPAD-05)
echo -n "5. Verificando reglas CSS de supresión de sidebar... "
grep -q "#sidebar" "$CLEAN_TERM_SCSS" || { echo "FALLO: clean-terminal.scss no suprime #sidebar"; exit 1; }
echo "[OK]"

# 6. Verificar versionado v2.1.3 (Criterio DPAD-06)
echo -n "6. Verificando versión 2.1.3 en configuración... "
grep -q 'version="2.1.3"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versión 2.1.3"; exit 1; }
grep -q '"version": "2.1.3"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene versión 2.1.3"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-031 HAN SIDO SUPERADAS EXITOSAMENTE ==="
