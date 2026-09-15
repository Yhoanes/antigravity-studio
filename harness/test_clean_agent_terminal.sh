#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-034 Pure Official Experience & Brand Purging Verification
# ==============================================================================
set -e

SPEC_FILE_034="specs/34-pure-official-antigravity-experience-and-brand-purging.md"
CLEAN_TERM_SCSS="nova-src/src/antigravity2/clean-terminal.scss"
CLEAN_TERM_JS="nova-src/src/antigravity2/CleanAgentTerminal.js"
TERMINAL_SERVICE="nova-src/src/plugins/terminal/src/android/TerminalService.java"
INIT_ALPINE="nova-src/src/plugins/terminal/scripts/init-alpine.sh"
MAIN_JS="nova-src/src/main.js"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"

echo "=== INICIANDO VALIDACIÓN FORMAL DE SPEC-034 ==="

# 1. Verificar documento SPEC-034
echo -n "1. Verificando documento SPEC-034... "
[ -f "$SPEC_FILE_034" ] || { echo "FALLO: No existe $SPEC_FILE_034"; exit 1; }
echo "[OK]"

# 2. Verificar supresión de scrollbar DOM en SCSS y JS (Criterio BRAND-01)
echo -n "2. Verificando supresión del scrollbar DOM de xterm... "
grep -q "\.scrollbar" "$CLEAN_TERM_SCSS" || { echo "FALLO: clean-terminal.scss no oculta .scrollbar"; exit 1; }
grep -q "\.slider" "$CLEAN_TERM_SCSS" || { echo "FALLO: clean-terminal.scss no oculta .slider"; exit 1; }
grep -q "scrollbarSliderBackground: \"transparent\"" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal.js no configura scrollbar transparente"; exit 1; }
echo "[OK]"

# 3. Verificar arranque limpio y comando atómico agy (Criterio BRAND-02)
echo -n "3. Verificando arranque directo 'clear && exec agy'... "
grep -q "clear && exec agy" "$CLEAN_TERM_JS" || { echo "FALLO: CleanAgentTerminal.js no ejecuta clear && exec agy"; exit 1; }
echo "[OK]"

# 4. Verificar guardas nulas en main.js y modal silenciado (Criterio BRAND-03)
echo -n "4. Verificando guardas de fsOperation y silenciamiento de consentimiento en main.js... "
grep -A 5 "projectsPath" "$MAIN_JS" | grep -q "typeof fsOperation" || { echo "FALLO: main.js no protege llamada a fsOperation"; exit 1; }
grep -A 5 "async function promptUpdateCheckConsent" "$MAIN_JS" | grep -q "return;" || { echo "FALLO: promptUpdateCheckConsent no está silenciado"; exit 1; }
echo "[OK]"

# 5. Verificar rebranding en TerminalService.java (Criterio BRAND-04)
echo -n "5. Verificando rebranding en TerminalService.java... "
grep -q 'setContentTitle("Google Antigravity")' "$TERMINAL_SERVICE" || { echo "FALLO: TerminalService.java no tiene título Google Antigravity"; exit 1; }
grep -q '"Google Antigravity Service"' "$TERMINAL_SERVICE" || { echo "FALLO: TerminalService.java no tiene canal Google Antigravity Service"; exit 1; }
echo "[OK]"

# 6. Verificar versionado v2.1.6 (Criterio BRAND-05)
echo -n "6. Verificando versión 2.1.6 en configuración... "
grep -q 'version="2.1.6"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versión 2.1.6"; exit 1; }
grep -q 'android-versionCode="20106"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versionCode 20106"; exit 1; }
grep -q '"version": "2.1.6"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene versión 2.1.6"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-034 HAN SIDO SUPERADAS EXITOSAMENTE ==="
