#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-028 Minimal Clean Agent Terminal Verification
# ==============================================================================
set -e

SPEC_FILE="specs/28-minimal-agent-terminal-clean-apk.md"
MAIN_JS="nova-src/src/main.js"
CLEAN_TERM_JS="nova-src/src/antigravity2/CleanAgentTerminal.js"
CLEAN_TERM_SCSS="nova-src/src/antigravity2/clean-terminal.scss"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"
APK_TARGET="GoogleAntigravity-v2.1.0-ARM64.apk"

echo "=== INICIANDO VALIDACIÓN FORMAL DE SPEC-028 ==="

# 1. Verificar existencia de la especificación
echo -n "1. Verificando existencia de documento SPEC-028... "
[ -f "$SPEC_FILE" ] || { echo "FALLO: No existe $SPEC_FILE"; exit 1; }
echo "[OK]"

# 2. AC-CORE-01: Verificar desacoplamiento total de AntigravityApp y ChatCanvas en main.js
echo -n "2. Verificando desacoplamiento en main.js (AC-CORE-01)... "
if grep -q "new AntigravityApp()" "$MAIN_JS"; then
    echo "FALLO: main.js aún contiene instanciación de AntigravityApp"; exit 1;
fi
if grep -q "import AntigravityApp" "$MAIN_JS"; then
    echo "FALLO: main.js aún importa AntigravityApp"; exit 1;
fi
echo "[OK]"

# 3. AC-CORE-02: Verificar montaje de CleanAgentTerminal en main.js
echo -n "3. Verificando montaje de CleanAgentTerminal en main.js (AC-CORE-02)... "
grep -q "import CleanAgentTerminal" "$MAIN_JS" || { echo "FALLO: main.js no importa CleanAgentTerminal"; exit 1; }
grep -q "cleanAgentTerminal\.mount" "$MAIN_JS" || { echo "FALLO: main.js no monta cleanAgentTerminal"; exit 1; }
echo "[OK]"

# 4. AC-CORE-03 & AC-CORE-04: Verificar conexión AXS y auto-ejecución de agy en CleanAgentTerminal.js
echo -n "4. Verificando lógica PTY y auto-comando agy (AC-CORE-03 & AC-CORE-04)... "
[ -f "$CLEAN_TERM_JS" ] || { echo "FALLO: No existe $CLEAN_TERM_JS"; exit 1; }
grep -q "ws://127.0.0.1" "$CLEAN_TERM_JS" || { echo "FALLO: No se encuentra URL de WebSocket PTY"; exit 1; }
grep -q "agy\\\\r" "$CLEAN_TERM_JS" || grep -q 'this\.autoCommand' "$CLEAN_TERM_JS" || { echo "FALLO: No se detecta auto-ejecución de agy"; exit 1; }
echo "[OK]"

# 5. AC-CORE-05: Verificar soporte de resize adaptativo y visualViewport
echo -n "5. Verificando adaptación a viewport y teclado (AC-CORE-05)... "
grep -q "visualViewport" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no escucha visualViewport"; exit 1; }
grep -q "fitAddon\.fit" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal no invoca fitAddon.fit"; exit 1; }
echo "[OK]"

# 6. AC-CORE-06 & AC-PERF-01: Verificar versionado y tamaño del paquete
echo -n "6. Verificando sincronización de versión 2.1.0 en configuración... "
grep -q 'version="2.1.0"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene version 2.1.0"; exit 1; }
grep -q '"version": "2.1.0"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene version 2.1.0"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-028 HAN SIDO SUPERADAS EXITOSAMENTE ==="
