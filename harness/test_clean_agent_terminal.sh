#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-037 Cordova Terminal Wrapper & Splash Dismissal Verification
# ==============================================================================
set -e

SPEC_FILE_036="specs/36-smooth-provisioning-loader-and-stream-logger.md"
SPEC_FILE_037="specs/37-fix-cordova-terminal-wrapper-and-splash-dismissal.md"
SPEC_FILE_038="specs/38-repair-arm64-subsystem-download-and-smooth-loader.md"
SPEC_FILE_039="specs/39-fix-native-library-path-and-proot-sandbox-init.md"
PROV_LOADER_JS="nova-src/src/antigravity2/ProvisioningLoader.js"
SCSS_FILE="nova-src/src/antigravity2/clean-terminal.scss"
TERM_SRC="nova-src/src/plugins/terminal/www/Terminal.js"
TERM_PLUGIN="nova-src/plugins/com.foxdebug.acode.rk.exec.terminal/www/Terminal.js"
TERM_PLATFORM="nova-src/platforms/android/platform_www/plugins/com.foxdebug.acode.rk.exec.terminal/www/Terminal.js"
TERM_ASSETS="nova-src/platforms/android/app/src/main/assets/www/plugins/com.foxdebug.acode.rk.exec.terminal/www/Terminal.js"
CLEAN_TERM_JS="nova-src/src/antigravity2/CleanAgentTerminal.js"
MAIN_JS="nova-src/src/main.js"
POST_PROCESS_JS="nova-src/hooks/post-process.js"
INIT_SANDBOX_SH="nova-src/src/plugins/terminal/scripts/init-sandbox.sh"
PROCESS_MANAGER_JAVA="nova-src/src/plugins/terminal/src/android/ProcessManager.java"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"

echo "=== INICIANDO VALIDACIÓN FORMAL DE TERMINAL Y BOOTSTRAP (SPEC-036 / SPEC-037 / SPEC-038 / SPEC-039) ==="

# 1. Verificar documentos SPEC
echo -n "1. Verificando documentos de especificación... "
[ -f "$SPEC_FILE_036" ] || { echo "FALLO: No existe $SPEC_FILE_036"; exit 1; }
[ -f "$SPEC_FILE_037" ] || { echo "FALLO: No existe $SPEC_FILE_037"; exit 1; }
[ -f "$SPEC_FILE_038" ] || { echo "FALLO: No existe $SPEC_FILE_038"; exit 1; }
[ -f "$SPEC_FILE_039" ] || { echo "FALLO: No existe $SPEC_FILE_039"; exit 1; }
echo "[OK]"

# 2. Verificar API polimórfica y anti-NaN en ProvisioningLoader (Criterio LOADER-01)
echo -n "2. Verificando salvaguarda anti-NaN y polimorfismo en ProvisioningLoader.js... "
grep -q "_targetPercent" "$PROV_LOADER_JS" || { echo "FALLO: _targetPercent ausente en ProvisioningLoader"; exit 1; }
grep -q "Number.isFinite" "$PROV_LOADER_JS" || grep -q "!isNaN" "$PROV_LOADER_JS" || { echo "FALLO: Validación numérica estricta ausente"; exit 1; }
echo "[OK]"

# 3. Verificar log stream monolínea (Criterio LOADER-02)
echo -n "3. Verificando log stream monolínea en ProvisioningLoader y SCSS... "
grep -q "provisioning-log-stream" "$PROV_LOADER_JS" || { echo "FALLO: Contenedor .provisioning-log-stream ausente en JS"; exit 1; }
grep -q "provisioning-log-stream" "$SCSS_FILE" || { echo "FALLO: Regla .provisioning-log-stream ausente en SCSS"; exit 1; }
grep -q "text-overflow: ellipsis" "$SCSS_FILE" || { echo "FALLO: Recorte con elipsis ausente en SCSS"; exit 1; }
echo "[OK]"

# 4. Verificar ticker cinemático e interpolación continua (Criterio LOADER-03)
echo -n "4. Verificando ticker cinemático y shimmer en SCSS... "
grep -q "_tickerTimer" "$PROV_LOADER_JS" || { echo "FALLO: _tickerTimer ausente en ProvisioningLoader"; exit 1; }
grep -q "progressShimmer" "$SCSS_FILE" || { echo "FALLO: Animación progressShimmer ausente en SCSS"; exit 1; }
echo "[OK]"

# 5. Verificar sincronización cuádruple de Terminal.js (Criterio LOADER-04)
echo -n "5. Verificando sincronización canónica de Terminal.js en las 4 ubicaciones... "
for f in "$TERM_SRC" "$TERM_PLUGIN" "$TERM_PLATFORM" "$TERM_ASSETS"; do
    if [ -f "$f" ]; then
        grep -q "async install(onProgress" "$f" || { echo "FALLO: $f no posee la firma async install(onProgress, ...)"; exit 1; }
    fi
done
echo "[OK]"

# 6. Verificar envoltura canónica de Cordova en Terminal.js (Criterio WRAPPER-01)
echo -n "6. Verificando envoltura cordova.define en Terminal.js de Android... "
for f in "$TERM_PLATFORM" "$TERM_ASSETS"; do
    if [ -f "$f" ]; then
        grep -q 'cordova.define("com.foxdebug.acode.rk.exec.terminal.Terminal"' "$f" || {
            echo "FALLO: $f carece de la envoltura cordova.define canónica"; exit 1;
        }
        tail -n 5 "$f" | grep -q '});' || {
            echo "FALLO: $f carece del cierre }); para cordova.define"; exit 1;
        }
    fi
done
echo "[OK]"

# 7. Verificar elevación de z-index a 1000001 (Criterio ZINDEX-02)
echo -n "7. Verificando elevación de z-index en clean-terminal.scss... "
grep -q "1000001" "$SCSS_FILE" || { echo "FALLO: z-index 1000001 ausente en clean-terminal.scss"; exit 1; }
echo "[OK]"

# 8. Verificar descarte activo en ProvisioningLoader.mount() (Criterio DISMISS-03)
echo -n "8. Verificando descarte activo de splash en ProvisioningLoader.js... "
grep -q 'getElementById("splash")' "$PROV_LOADER_JS" || { echo "FALLO: No se busca elemento splash en mount()"; exit 1; }
grep -q 'classList.remove("loading"' "$PROV_LOADER_JS" || { echo "FALLO: No se remueve clase loading en mount()"; exit 1; }
echo "[OK]"

# 9. Verificar Watchdog timer en main.js (Criterio WATCHDOG-04)
echo -n "9. Verificando watchdog timer en main.js... "
grep -q '2500' "$MAIN_JS" || { echo "FALLO: Watchdog timer de 2500ms ausente en main.js"; exit 1; }
grep -q 'disipando splash screen' "$MAIN_JS" || { echo "FALLO: Mensaje de disipación de splash ausente en main.js"; exit 1; }
echo "[OK]"

# 10. Verificar descarga legítima por red en Terminal.js (Criterio REPAIR-01)
echo -n "10. Verificando URLs legítimas de descarga en Terminal.js... "
grep -q "cdimage.ubuntu.com" "$TERM_SRC" || { echo "FALLO: URL de Ubuntu base ausente en Terminal.js"; exit 1; }
grep -q "storage.googleapis.com" "$TERM_SRC" || { echo "FALLO: URL de Google Antigravity CLI ausente en Terminal.js"; exit 1; }
grep -q "downloadFile" "$TERM_SRC" || { echo "FALLO: downloadFile ausente en Terminal.js"; exit 1; }
echo "[OK]"

# 11. Verificar validación de éxito en CleanAgentTerminal.js (Criterio REPAIR-03)
echo -n "11. Verificando validación de installSuccess en CleanAgentTerminal.js... "
grep -q "installSuccess" "$CLEAN_TERM_JS" || { echo "FALLO: installSuccess ausente en CleanAgentTerminal.js"; exit 1; }
echo "[OK]"

# 12. Verificar formateador anti-[object Object] en CleanAgentTerminal.js (Criterio REPAIR-04)
echo -n "12. Verificando formateador anti-[object Object] en CleanAgentTerminal.js... "
grep -q "formatErrorMessage" "$CLEAN_TERM_JS" || { echo "FALLO: formatErrorMessage ausente en CleanAgentTerminal.js"; exit 1; }
echo "[OK]"

# 13. Verificar predeterminación de API 36 en post-process.js (Criterio LIB-01)
echo -n "13. Verificando default api = 36 en post-process.js... "
grep -q 'let api = "36"' "$POST_PROCESS_JS" || { echo "FALLO: post-process.js no inicializa api en 36"; exit 1; }
echo "[OK]"

# 14. Verificar detección en NATIVE_DIR y ausencia de chmod ciego (Criterios LIB-02 y LIB-03)
echo -n "14. Verificando detección en NATIVE_DIR y ausencia de chmod +x PREFIX/*... "
grep -q 'if \[ -f "\$NATIVE_DIR/libproot-xed.so" \]' "$INIT_SANDBOX_SH" || {
    echo "FALLO: init-sandbox.sh no prioriza $NATIVE_DIR/libproot-xed.so"; exit 1;
}
grep -q 'chmod +x \$PREFIX/\*' "$INIT_SANDBOX_SH" && {
    echo "FALLO: init-sandbox.sh todavía contiene chmod +x $PREFIX/* ciego"; exit 1;
}
echo "[OK]"

# 15. Verificar symlink incondicional en ProcessManager.java (Criterio LIB-04)
echo -n "15. Verificando symlink incondicional en ProcessManager.java... "
grep -A 5 "refreshAxsSymlink" "$PROCESS_MANAGER_JAVA" | grep -q "isFdroidBuild()" && {
    echo "FALLO: refreshAxsSymlink sigue filtrando por isFdroidBuild()"; exit 1;
}
echo "[OK]"

# 16. Verificar versionado v2.2.2 y android-versionCode 20202 en configuración (Criterio PREM-06)
echo -n "16. Verificando versión 2.2.2 y versionCode 20202 en configuración... "
grep -q 'version="2.2.2"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versión 2.2.2"; exit 1; }
grep -q 'android-versionCode="20202"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene android-versionCode 20202"; exit 1; }
grep -q '"version": "2.2.2"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene versión 2.2.2"; exit 1; }
echo "[OK]"

# 17. Verificar SPEC-040 (Criterios PREM-01 a PREM-04)
SPEC_FILE_040="specs/40-premium-zero-leak-transition-and-session-persistence.md"
TERMINAL_SERVICE_JAVA="nova-src/src/plugins/terminal/src/android/TerminalService.java"
echo -n "17. Verificando documento SPEC-040... "
[ -f "$SPEC_FILE_040" ] || { echo "FALLO: No existe $SPEC_FILE_040"; exit 1; }
echo "[OK]"

echo -n "18. Verificando persistencia de sesión y cortina en CleanAgentTerminal.js (PREM-01/02/03)... "
grep -q "antigravity_active_session_pid" "$CLEAN_TERM_JS" || { echo "FALLO: antigravity_active_session_pid ausente en CleanAgentTerminal.js"; exit 1; }
grep -q "restartSession" "$CLEAN_TERM_JS" || { echo "FALLO: restartSession ausente en CleanAgentTerminal.js"; exit 1; }
echo "[OK]"

echo -n "19. Verificando setContentIntent en TerminalService.java (PREM-04)... "
grep -q "setContentIntent" "$TERMINAL_SERVICE_JAVA" || { echo "FALLO: setContentIntent ausente en TerminalService.java"; exit 1; }
grep -q "FLAG_ACTIVITY_SINGLE_TOP" "$TERMINAL_SERVICE_JAVA" || { echo "FALLO: FLAG_ACTIVITY_SINGLE_TOP ausente en TerminalService.java"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-036, SPEC-037, SPEC-038, SPEC-039 Y SPEC-040 HAN SIDO SUPERADAS EXITOSAMENTE ==="
