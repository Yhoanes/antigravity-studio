#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-028 & SPEC-029 Clean Agent Terminal & OAuth Bridge Verification
# ==============================================================================
set -e

SPEC_FILE_028="specs/28-minimal-agent-terminal-clean-apk.md"
SPEC_FILE_029="specs/29-oauth-browser-bridge-and-interactive-auth.md"
MAIN_JS="nova-src/src/main.js"
CLEAN_TERM_JS="nova-src/src/antigravity2/CleanAgentTerminal.js"
CLEAN_TERM_SCSS="nova-src/src/antigravity2/clean-terminal.scss"
SYSTEM_JAVA="nova-src/src/plugins/system/android/com/foxdebug/system/System.java"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"

echo "=== INICIANDO VALIDACIÓN FORMAL DE SPEC-028 Y SPEC-029 ==="

# 1. Verificar especificaciones
echo -n "1. Verificando documentos SPEC-028 y SPEC-029... "
[ -f "$SPEC_FILE_028" ] || { echo "FALLO: No existe $SPEC_FILE_028"; exit 1; }
[ -f "$SPEC_FILE_029" ] || { echo "FALLO: No existe $SPEC_FILE_029"; exit 1; }
echo "[OK]"

# 2. AC-CORE-01 & AC-CORE-02: Desacoplamiento de ChatCanvas y montaje de CleanAgentTerminal
echo -n "2. Verificando desacoplamiento y montaje en main.js... "
grep -q "import CleanAgentTerminal" "$MAIN_JS" || { echo "FALLO: main.js no importa CleanAgentTerminal"; exit 1; }
grep -q "cleanAgentTerminal\.mount" "$MAIN_JS" || { echo "FALLO: main.js no monta cleanAgentTerminal"; exit 1; }
if grep -q "new AntigravityApp()" "$MAIN_JS"; then
    echo "FALLO: main.js aún contiene AntigravityApp"; exit 1;
fi
echo "[OK]"

# 3. AC-OAUTH-01: Sniffer de WebSocket para URLs de Google OAuth
echo -n "3. Verificando sniffer de flujo WebSocket para OAuth (AC-OAUTH-01)... "
grep -q "accounts\.google\.com" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal.js no contiene regex de OAuth Google"; exit 1; }
grep -q "sniffWebSocketMessage" "$CLEAN_TERM_JS" || grep -q "openOAuthUrl" "$CLEAN_TERM_JS" || { echo "FALLO: Sniffer de WebSocket no implementado"; exit 1; }
echo "[OK]"

# 4. AC-OAUTH-02: linkHandler para hipervínculos OSC 8 en Xterm.js
echo -n "4. Verificando configuración de linkHandler OSC 8 (AC-OAUTH-02)... "
grep -q "linkHandler" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal.js no configura linkHandler en Xterm"; exit 1; }
echo "[OK]"

# 5. AC-OAUTH-03: Barra de acción contextual flotante con botones Chrome y Pegar
echo -n "5. Verificando barra contextual y botones de interacción (AC-OAUTH-03)... "
grep -q "oauth-action-bar" "$CLEAN_TERM_JS" || grep -q "clean-agent-oauth-bar" "$CLEAN_TERM_JS" || { echo "FALLO: Barra contextual de OAuth no presente en CleanAgentTerminal.js"; exit 1; }
grep -q "btn-oauth-paste" "$CLEAN_TERM_JS" || grep -q "handlePasteOAuthCode" "$CLEAN_TERM_JS" || { echo "FALLO: Manejador de pegado de código no presente"; exit 1; }
echo "[OK]"

# 6. AC-OAUTH-04: Blindaje FLAG_ACTIVITY_NEW_TASK en System.java
echo -n "6. Verificando FLAG_ACTIVITY_NEW_TASK en System.java (AC-OAUTH-04)... "
grep -q "FLAG_ACTIVITY_NEW_TASK" "$SYSTEM_JAVA" || { echo "FALLO: System.java no incluye FLAG_ACTIVITY_NEW_TASK"; exit 1; }
echo "[OK]"

# 7. AC-VER-01: Versionado v2.1.1
echo -n "7. Verificando versión 2.1.1 en configuración... "
grep -q 'version="2.1.1"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene version 2.1.1"; exit 1; }
grep -q '"version": "2.1.1"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene version 2.1.1"; exit 1; }
echo "[OK]"

# 8. Verificaciones adicionales de métodos SPEC-029 requeridos
echo -n "8. Verificando métodos openInBrowser y detectOAuthUrl en CleanAgentTerminal.js... "
grep -q "openInBrowser" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal.js no tiene openInBrowser"; exit 1; }
grep -q "detectOAuthUrl" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal.js no tiene detectOAuthUrl"; exit 1; }
grep -q "pasteAuthCode" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal.js no tiene pasteAuthCode"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-029 HAN SIDO SUPERADAS EXITOSAMENTE ==="
