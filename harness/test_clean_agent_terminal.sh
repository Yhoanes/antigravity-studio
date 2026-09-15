#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-032 Unified Chat Touch Scrolling & Contextual D-Pad Verification
# ==============================================================================
set -e

SPEC_FILE_032="specs/32-unified-chat-touch-scrolling-and-contextual-dpad.md"
TOUCH_NAV_JS="nova-src/src/antigravity2/TerminalTouchNavigation.js"
CLEAN_TERM_JS="nova-src/src/antigravity2/CleanAgentTerminal.js"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"

echo "=== INICIANDO VALIDACIÓN FORMAL DE SPEC-032 ==="

# 1. Verificar documento SPEC-032
echo -n "1. Verificando documento SPEC-032... "
[ -f "$SPEC_FILE_032" ] || { echo "FALLO: No existe $SPEC_FILE_032"; exit 1; }
echo "[OK]"

# 2. Verificar scrollLines e inercia para chat (Criterio CHAT-01)
echo -n "2. Verificando implementación de scrollLines, scrollByPixels y momentum... "
grep -q "scrollLines" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no invoca scrollLines"; exit 1; }
grep -q "scrollByPixels" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no implementa scrollByPixels"; exit 1; }
grep -q "startMomentum" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no implementa startMomentum"; exit 1; }
echo "[OK]"

# 3. Verificar discriminador contextual isInteractiveMenu (Criterio CHAT-02 y CHAT-03)
echo -n "3. Verificando detector de menú interactivo isInteractiveMenu... "
grep -q "isInteractiveMenu" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no implementa isInteractiveMenu"; exit 1; }
grep -q "viewportY <" "$TOUCH_NAV_JS" || { echo "FALLO: No se compara viewportY con baseY"; exit 1; }
echo "[OK]"

# 4. Verificar soporte de gesto con 2 dedos para historial (Criterio CHAT-04)
echo -n "4. Verificando soporte de 2 dedos para historial... "
grep -q "touches\.length === 2" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no detecta 2 dedos"; exit 1; }
echo "[OK]"

# 5. Verificar preservación de flechas horizontales y long-press (Criterio CHAT-05)
echo -n "5. Verificando preservación de flechas horizontales y longPress... "
grep -q "onArrowLeft" "$TOUCH_NAV_JS" || { echo "FALLO: onArrowLeft no presente"; exit 1; }
grep -q "onArrowRight" "$TOUCH_NAV_JS" || { echo "FALLO: onArrowRight no presente"; exit 1; }
grep -q "longPressMs" "$TOUCH_NAV_JS" || { echo "FALLO: longPressMs no presente"; exit 1; }
echo "[OK]"

# 6. Verificar versionado v2.1.4 (Criterio CHAT-06)
echo -n "6. Verificando versión 2.1.4 en configuración... "
grep -q 'version="2.1.4"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versión 2.1.4"; exit 1; }
grep -q '"version": "2.1.4"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene versión 2.1.4"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-032 HAN SIDO SUPERADAS EXITOSAMENTE ==="
