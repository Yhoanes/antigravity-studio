#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-035 Splash UX, OAuth Stabilizer & Progress Loader Verification
# ==============================================================================
set -e

SPEC_FILE_035="specs/35-splash-ux-oauth-stabilizer-and-progress-loader.md"
INDEX_HTML="nova-src/www/index.html"
CLEAN_TERM_JS="nova-src/src/antigravity2/CleanAgentTerminal.js"
PROV_LOADER_JS="nova-src/src/antigravity2/ProvisioningLoader.js"
TOUCH_NAV_JS="nova-src/src/antigravity2/TerminalTouchNavigation.js"
TERMINAL_JS="nova-src/src/plugins/terminal/www/Terminal.js"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"

echo "=== INICIANDO VALIDACIÓN FORMAL DE SPEC-035 ==="

# 1. Verificar documento SPEC-035
echo -n "1. Verificando documento SPEC-035... "
[ -f "$SPEC_FILE_035" ] || { echo "FALLO: No existe $SPEC_FILE_035"; exit 1; }
echo "[OK]"

# 2. Verificar supresión de textos en splash (Criterio SPLASH-01)
echo -n "2. Verificando supresión de textos en splash index.html... "
grep -q "\.splash-version" "$INDEX_HTML" && grep -A 2 "\.splash-version" "$INDEX_HTML" | grep -q "display: none" || { echo "FALLO: .splash-version no está oculto"; exit 1; }
grep -q "\.splash-message" "$INDEX_HTML" && grep -A 2 "\.splash-message" "$INDEX_HTML" | grep -q "display: none" || { echo "FALLO: .splash-message no está oculto"; exit 1; }
echo "[OK]"

# 3. Verificar estabilizador de OAuth y anti-404 (Criterios OAUTH-01 y OAUTH-02)
echo -n "3. Verificando estabilizador y validador de OAuth PKCE... "
grep -q "validateOAuthUrl" "$CLEAN_TERM_JS" || { echo "FALLO: validateOAuthUrl no implementado"; exit 1; }
grep -q "client_id=" "$CLEAN_TERM_JS" || { echo "FALLO: Verificación de client_id ausente"; exit 1; }
grep -q "code_challenge=" "$CLEAN_TERM_JS" || { echo "FALLO: Verificación de code_challenge ausente"; exit 1; }
echo "[OK]"

# 4. Verificar componente ProvisioningLoader y callback de progreso (Criterios LOADER-01 y LOADER-02)
echo -n "4. Verificando ProvisioningLoader y onProgress en Terminal.js... "
[ -f "$PROV_LOADER_JS" ] || { echo "FALLO: No existe $PROV_LOADER_JS"; exit 1; }
grep -q "onProgress" "$TERMINAL_JS" || { echo "FALLO: Terminal.js no soporta callback onProgress"; exit 1; }
echo "[OK]"

# 5. Verificar discriminador contextual en TerminalTouchNavigation (Criterio GESTURE-01)
echo -n "5. Verificando discriminador contextual de menú vs chat... "
grep -q "isInteractiveMenu" "$TOUCH_NAV_JS" || { echo "FALLO: isInteractiveMenu ausente"; exit 1; }
grep -q "alternate" "$TOUCH_NAV_JS" || { echo "FALLO: Soporte de buffer alternate ausente"; exit 1; }
echo "[OK]"

# 6. Verificar versionado v2.1.7 (Criterio VER-01)
echo -n "6. Verificando versión 2.1.7 en configuración... "
grep -q 'version="2.1.7"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versión 2.1.7"; exit 1; }
grep -q 'android-versionCode="20107"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versionCode 20107"; exit 1; }
grep -q '"version": "2.1.7"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene versión 2.1.7"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-035 HAN SIDO SUPERADAS EXITOSAMENTE ==="
