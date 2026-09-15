#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-033 Zero-Scrollbar Edge-to-Edge Verification
# ==============================================================================
set -e

SPEC_FILE_033="specs/33-zero-scrollbar-clean-edge-to-edge-terminal.md"
CLEAN_TERM_SCSS="nova-src/src/antigravity2/clean-terminal.scss"
TOUCH_NAV_JS="nova-src/src/antigravity2/TerminalTouchNavigation.js"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"

echo "=== INICIANDO VALIDACIÓN FORMAL DE SPEC-033 ==="

# 1. Verificar documento SPEC-033
echo -n "1. Verificando documento SPEC-033... "
[ -f "$SPEC_FILE_033" ] || { echo "FALLO: No existe $SPEC_FILE_033"; exit 1; }
echo "[OK]"

# 2. Verificar supresión de scrollbar en SCSS (Criterio CLEAN-01)
echo -n "2. Verificando reglas CSS de supresión de scrollbar... "
grep -q "scrollbar-width: none" "$CLEAN_TERM_SCSS" || { echo "FALLO: clean-terminal.scss no contiene scrollbar-width: none"; exit 1; }
grep -q "display: none !important" "$CLEAN_TERM_SCSS" || { echo "FALLO: clean-terminal.scss no contiene display: none !important en scrollbar"; exit 1; }
echo "[OK]"

# 3. Verificar preservación del motor gestual táctil (Criterio CLEAN-03)
echo -n "3. Verificando preservación de TerminalTouchNavigation... "
[ -f "$TOUCH_NAV_JS" ] || { echo "FALLO: No existe $TOUCH_NAV_JS"; exit 1; }
grep -q "scrollLines" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no contiene scrollLines"; exit 1; }
grep -q "startMomentum" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no contiene startMomentum"; exit 1; }
grep -q "isInteractiveMenu" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no contiene isInteractiveMenu"; exit 1; }
echo "[OK]"

# 4. Verificar versionado v2.1.5 (Criterio CLEAN-04)
echo -n "4. Verificando versión 2.1.5 en configuración... "
grep -q 'version="2.1.5"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versión 2.1.5"; exit 1; }
grep -q 'android-versionCode="20105"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versionCode 20105"; exit 1; }
grep -q '"version": "2.1.5"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene versión 2.1.5"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-033 HAN SIDO SUPERADAS EXITOSAMENTE ==="
