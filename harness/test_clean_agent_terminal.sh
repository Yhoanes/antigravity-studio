#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-037 Cordova Terminal Wrapper & Splash Dismissal Verification
# ==============================================================================
set -e

SPEC_FILE_036="specs/36-smooth-provisioning-loader-and-stream-logger.md"
SPEC_FILE_037="specs/37-fix-cordova-terminal-wrapper-and-splash-dismissal.md"
PROV_LOADER_JS="nova-src/src/antigravity2/ProvisioningLoader.js"
SCSS_FILE="nova-src/src/antigravity2/clean-terminal.scss"
TERM_SRC="nova-src/src/plugins/terminal/www/Terminal.js"
TERM_PLUGIN="nova-src/plugins/com.foxdebug.acode.rk.exec.terminal/www/Terminal.js"
TERM_PLATFORM="nova-src/platforms/android/platform_www/plugins/com.foxdebug.acode.rk.exec.terminal/www/Terminal.js"
TERM_ASSETS="nova-src/platforms/android/app/src/main/assets/www/plugins/com.foxdebug.acode.rk.exec.terminal/www/Terminal.js"
MAIN_JS="nova-src/src/main.js"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"

echo "=== INICIANDO VALIDACIÓN FORMAL DE TERMINAL Y BOOTSTRAP (SPEC-036 / SPEC-037) ==="

# 1. Verificar documentos SPEC
echo -n "1. Verificando documentos de especificación... "
[ -f "$SPEC_FILE_036" ] || { echo "FALLO: No existe $SPEC_FILE_036"; exit 1; }
[ -f "$SPEC_FILE_037" ] || { echo "FALLO: No existe $SPEC_FILE_037"; exit 1; }
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

# 10. Verificar versionado v2.1.9 y android-versionCode 20109 (Criterio VER-05)
echo -n "10. Verificando versión 2.1.9 y versionCode 20109 en configuración... "
grep -q 'version="2.1.9"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versión 2.1.9"; exit 1; }
grep -q 'android-versionCode="20109"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene android-versionCode 20109"; exit 1; }
grep -q '"version": "2.1.9"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene versión 2.1.9"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-036 Y SPEC-037 HAN SIDO SUPERADAS EXITOSAMENTE ==="
