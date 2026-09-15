#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-030 Pure Clean Terminal & Gesture Touch Navigation
# ==============================================================================
set -e

SPEC_FILE_030="specs/30-pure-clean-terminal-and-gesture-touch-navigation.md"
MAIN_JS="nova-src/src/main.js"
CLEAN_TERM_JS="nova-src/src/antigravity2/CleanAgentTerminal.js"
TOUCH_NAV_JS="nova-src/src/antigravity2/TerminalTouchNavigation.js"
CLEAN_TERM_SCSS="nova-src/src/antigravity2/clean-terminal.scss"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"

echo "=== INICIANDO VALIDACIÓN FORMAL DE SPEC-030 ==="

# 1. Verificar documento SPEC-030
echo -n "1. Verificando existencia de documento SPEC-030... "
[ -f "$SPEC_FILE_030" ] || { echo "FALLO: No existe $SPEC_FILE_030"; exit 1; }
echo "[OK]"

# 2. Verificar supresión de elementos UI decorativos (Criterio CLEAN-01: Zero-Decoration)
echo -n "2. Verificando supresión de TopBar y barras flotantes... "
if grep -q "clean-agent-topbar" "$CLEAN_TERM_JS"; then
    echo "FALLO: CleanAgentTerminal.js aún contiene referencias de render a clean-agent-topbar"; exit 1;
fi
if grep -q "oauth-action-bar" "$CLEAN_TERM_JS"; then
    echo "FALLO: CleanAgentTerminal.js aún contiene referencias de render a oauth-action-bar"; exit 1;
fi
echo "[OK]"

# 3. Verificar motor gestual táctil TerminalTouchNavigation (Criterio TOUCH-01)
echo -n "3. Verificando motor de navegación táctil TerminalTouchNavigation... "
[ -f "$TOUCH_NAV_JS" ] || { echo "FALLO: No existe $TOUCH_NAV_JS"; exit 1; }
grep -q "\\\\x1b\\[A" "$TOUCH_NAV_JS" || grep -q "ArrowUp" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no emite flecha arriba"; exit 1; }
grep -q "\\\\x1b\\[B" "$TOUCH_NAV_JS" || grep -q "ArrowDown" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no emite flecha abajo"; exit 1; }
grep -q "TerminalTouchNavigation" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no integra TerminalTouchNavigation"; exit 1; }
grep -q "onArrowUp" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no define onArrowUp"; exit 1; }
grep -q "onArrowDown" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no define onArrowDown"; exit 1; }
echo "[OK]"

# 4. Verificar soporte de Long-Press para pegado (Criterio TOUCH-03)
echo -n "4. Verificando gesto long-press para portapapeles... "
grep -q "longPress" "$TOUCH_NAV_JS" || { echo "FALLO: TerminalTouchNavigation no implementa longPress"; exit 1; }
grep -q "pasteFromClipboard" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no implementa pasteFromClipboard"; exit 1; }
grep -q "clipboard" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no accede al portapapeles"; exit 1; }
echo "[OK]"

# 5. Verificar preservación de auto-bridge OAuth silencioso (Criterio CORE-07)
echo -n "5. Verificando auto-bridge silencioso de OAuth... "
grep -q "accounts\.google\.com" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no conserva el sniffer de OAuth"; exit 1; }
grep -q "linkHandler" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no conserva linkHandler OSC 8"; exit 1; }
echo "[OK]"

# 6. Verificar versionado v2.1.2 (Criterio VER-02)
echo -n "6. Verificando versión 2.1.2 en configuración... "
grep -q 'version="2.1.2"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versión 2.1.2"; exit 1; }
grep -q '"version": "2.1.2"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene versión 2.1.2"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-030 HAN SIDO SUPERADAS EXITOSAMENTE ==="
