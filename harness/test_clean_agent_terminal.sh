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
PILL_JS="nova-src/src/antigravity2/FloatingInputPill.js"
SPEC_FILE_050="specs/50-floating-input-pill.md"
SPEC_FILE_051="specs/51-model-selector-autoclear-banner-polished-pill.md"

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

# 16. Verificar versionado v2.7.0 y android-versionCode 20700 en configuración (Criterio de Versionado)
echo -n "16. Verificando versión 2.7.0 y versionCode 20700 en configuración... "
grep -q 'version="2.7.0"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versión 2.7.0"; exit 1; }
grep -q 'android-versionCode="20700"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene android-versionCode 20700"; exit 1; }
grep -q '"version": "2.7.0"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene versión 2.7.0"; exit 1; }
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

# 20. Verificar SPEC-041 (Criterios AUTH-01 a AUTH-06)
SPEC_FILE_041="specs/41-native-google-auth-card-and-session-state.md"
AUTH_CARD_JS="nova-src/src/antigravity2/GoogleAuthCard.js"

echo -n "20. Verificando documento SPEC-041... "
[ -f "$SPEC_FILE_041" ] || { echo "FALLO: No existe $SPEC_FILE_041"; exit 1; }
echo "[OK]"

echo -n "21. Verificando componente GoogleAuthCard.js (AUTH-01)... "
[ -f "$AUTH_CARD_JS" ] || { echo "FALLO: No existe GoogleAuthCard.js"; exit 1; }
grep -q "google-auth-card" "$AUTH_CARD_JS" || { echo "FALLO: Contenedor google-auth-card ausente"; exit 1; }
grep -q "Continuar con Google" "$AUTH_CARD_JS" || { echo "FALLO: Botón de login ausente en GoogleAuthCard.js"; exit 1; }
echo "[OK]"

echo -n "22. Verificando integración de GoogleAuthCard y Account Badge (AUTH-02 y AUTH-04)... "
grep -q "GoogleAuthCard" "$CLEAN_TERM_JS" || { echo "FALLO: Import o uso de GoogleAuthCard ausente en CleanAgentTerminal"; exit 1; }
grep -q "clean-agent-account-badge" "$CLEAN_TERM_JS" || { echo "FALLO: Badge de cuenta ausente en CleanAgentTerminal"; exit 1; }
echo "[OK]"

echo -n "23. Verificando estilos en clean-terminal.scss (AUTH-01 y AUTH-04)... "
grep -q "google-auth-overlay" "$SCSS_FILE" || { echo "FALLO: .google-auth-overlay ausente en SCSS"; exit 1; }
grep -q "clean-agent-account-badge" "$SCSS_FILE" || { echo "FALLO: .clean-agent-account-badge ausente en SCSS"; exit 1; }
echo "[OK]"

# 24. Verificar SPEC-042 (Criterios AUTO-01 a AUTO-06)
SPEC_FILE_042="specs/42-stream-auto-responder-and-auto-clipboard-oauth.md"

echo -n "24. Verificando documento SPEC-042... "
[ -f "$SPEC_FILE_042" ] || { echo "FALLO: No existe $SPEC_FILE_042"; exit 1; }
echo "[OK]"

echo -n "25. Verificando auto-respondedor de stream y banderas one-shot (AUTO-01, AUTO-02, AUTO-06)... "
grep -q "_handleAutoResponderStream" "$CLEAN_TERM_JS" || { echo "FALLO: _handleAutoResponderStream ausente"; exit 1; }
grep -q "_hasAutoSelectedLogin" "$CLEAN_TERM_JS" || { echo "FALLO: _hasAutoSelectedLogin ausente"; exit 1; }
grep -q "Select login method" "$CLEAN_TERM_JS" || { echo "FALLO: Patrón 'Select login method' ausente"; exit 1; }
echo "[OK]"

echo -n "26. Verificando inyección automática desde portapapeles (AUTO-03)... "
grep -q "_setupClipboardAutoInjection" "$CLEAN_TERM_JS" || { echo "FALLO: _setupClipboardAutoInjection ausente"; exit 1; }
grep -q '\^4\/' "$CLEAN_TERM_JS" || { echo "FALLO: Expresión regular para token 4/... ausente"; exit 1; }
echo "[OK]"

echo -n "27. Verificando copy neutral y setAuthenticating en GoogleAuthCard (AUTO-04, AUTO-05)... "
grep -q "Inicia sesión para sincronizar tus proyectos" "$AUTH_CARD_JS" || {
    echo "FALLO: Copy neutral ausente en GoogleAuthCard.js"; exit 1;
}
grep -q "Gemini 3.8 Flash" "$AUTH_CARD_JS" && {
    echo "FALLO: GoogleAuthCard.js aún menciona 'Gemini 3.8 Flash'"; exit 1;
}
grep -q "setAuthenticating" "$AUTH_CARD_JS" || {
    echo "FALLO: setAuthenticating ausente en GoogleAuthCard.js"; exit 1;
}
echo "[OK]"

# 28. Verificar SPEC-043 (Criterios WIZ-01 a WIZ-06)
SPEC_FILE_043="specs/43-native-material3-multi-step-onboarding-wizard.md"
WIZARD_JS="nova-src/src/antigravity2/OnboardingWizard.js"

echo -n "28. Verificando documento SPEC-043... "
[ -f "$SPEC_FILE_043" ] || { echo "FALLO: No existe $SPEC_FILE_043"; exit 1; }
echo "[OK]"

echo -n "29. Verificando fondo sólido opaco en SCSS (WIZ-01)... "
grep -q "onboarding-wizard-overlay" "$SCSS_FILE" || { echo "FALLO: .onboarding-wizard-overlay ausente en SCSS"; exit 1; }
grep -q "background-color: #0b0f19" "$SCSS_FILE" || { echo "FALLO: Fondo sólido #0b0f19 ausente en SCSS"; exit 1; }
echo "[OK]"

echo -n "30. Verificando OnboardingWizard.js y secuencia [Done] (WIZ-02 a WIZ-05)... "
[ -f "$WIZARD_JS" ] || { echo "FALLO: No existe OnboardingWizard.js"; exit 1; }
grep -q "step-auth-method" "$WIZARD_JS" || { echo "FALLO: step-auth-method ausente"; exit 1; }
grep -q "step-theme-selector" "$WIZARD_JS" || { echo "FALLO: step-theme-selector ausente"; exit 1; }
grep -q "step-terms-telemetry" "$WIZARD_JS" || { echo "FALLO: step-terms-telemetry ausente"; exit 1; }
grep -q "\\t\\\\x1b\\[C\\\\r" "$WIZARD_JS" || grep -q "\t\x1b\[C\r" "$WIZARD_JS" || {
    echo "FALLO: Secuencia Tab->Flecha Der->Enter para [Done] ausente en OnboardingWizard.js"; exit 1;
}
echo "[OK]"

echo -n "31. Verificando integración de OnboardingWizard en CleanAgentTerminal.js (WIZ-06)... "
grep -q "OnboardingWizard" "$CLEAN_TERM_JS" || { echo "FALLO: OnboardingWizard ausente en CleanAgentTerminal.js"; exit 1; }
echo "[OK]"

# 32. Verificar SPEC-044 (Criterios LIFECYCLE-01 a LIFECYCLE-06)
SPEC_FILE_044="specs/44-fix-provisioning-loader-lifecycle-and-wizard-mount-order.md"

echo -n "32. Verificando documento SPEC-044... "
[ -f "$SPEC_FILE_044" ] || { echo "FALLO: No existe $SPEC_FILE_044"; exit 1; }
echo "[OK]"

echo -n "33. Verificando desacoplamiento de OnboardingWizard en mount() (LIFECYCLE-01)... "
grep -A 30 "mount(parentEl)" "$CLEAN_TERM_JS" | grep -q "_setupOnboardingWizard" && {
    echo "FALLO: _setupOnboardingWizard aún se invoca síncronamente en mount()"; exit 1;
}
echo "[OK]"

echo -n "34. Verificando elevación de z-index a 1000010 en SCSS (LIFECYCLE-02)... "
grep -q "1000010" "$SCSS_FILE" || {
    echo "FALLO: z-index 1000010 ausente en clean-terminal.scss"; exit 1;
}
echo "[OK]"

echo -n "35. Verificando montaje diferido tras openWebSocket (LIFECYCLE-03)... "
grep -A 35 "openWebSocket" "$CLEAN_TERM_JS" | grep -q "_setupOnboardingWizard" || {
    echo "FALLO: _setupOnboardingWizard no se invoca tras openWebSocket en connect()"; exit 1;
}
echo "[OK]"

# 36. Verificar SPEC-045 (Criterios THEME-01 a THEME-06)
SPEC_FILE_045="specs/45-pure-google-auth-and-multi-theme-palette-selector.md"

echo -n "36. Verificando documento SPEC-045... "
[ -f "$SPEC_FILE_045" ] || { echo "FALLO: No existe $SPEC_FILE_045"; exit 1; }
echo "[OK]"

echo -n "37. Verificando exclusión de botón API Key en OnboardingWizard.js (THEME-01)... "
grep -q "btn-auth-token" "$WIZARD_JS" && {
    echo "FALLO: btn-auth-token aún está presente en OnboardingWizard.js"; exit 1;
}
echo "[OK]"

echo -n "38. Verificando temas 'tokyo-night' y 'dark' (índice 4) en OnboardingWizard.js (THEME-02)... "
grep -q "tokyo-night" "$WIZARD_JS" || { echo "FALLO: 'tokyo-night' no encontrado en OnboardingWizard.js"; exit 1; }
grep -q 'data-theme-index="4"' "$WIZARD_JS" || { echo "FALLO: Índice 4 para Dark no encontrado"; exit 1; }
echo "[OK]"

echo -n "39. Verificando secuencia ANSI de Dark en auto-responder de CleanAgentTerminal.js (THEME-04)... "
grep -A 10 "_hasAutoConfirmedTheme" "$CLEAN_TERM_JS" | grep -F -q "\x1b[B\x1b[B\x1b[B\x1b[B\r" || \
grep -A 10 "_hasAutoConfirmedTheme" "$CLEAN_TERM_JS" | grep -q "\\\\x1b\\\\[B\\\\x1b\\\\[B\\\\x1b\\\\[B\\\\x1b\\\\[B\\\\r" || {
    echo "FALLO: Secuencia de auto-responder para Dark no contiene 4 flechas abajo + Enter"; exit 1;
}
echo "[OK]"

echo -n "40. Verificando z-index 1000010 y fondo opaco (THEME-05)... "
grep -q "1000010" "$SCSS_FILE" || { echo "FALLO: z-index 1000010 del loader ausente"; exit 1; }
grep -q "background: #0b0f19 !important" "$SCSS_FILE" || { echo "FALLO: Opacidad sólida del wizard ausente"; exit 1; }
echo "[OK]"

# 41. Verificar SPEC-046 (Criterios AC-UX-01 a AC-UX-06)
SPEC_FILE_046="specs/46-smooth-optimistic-wizard-transitions-and-emoji-purging.md"

echo -n "41. Verificando documento SPEC-046... "
[ -f "$SPEC_FILE_046" ] || { echo "FALLO: No existe $SPEC_FILE_046"; exit 1; }
echo "[OK]"

echo -n "42. Verificando erradicación de emojis y spinner CSS en OnboardingWizard.js y SCSS (AC-UX-01)... "
grep -q "auth-spinner-dot" "$WIZARD_JS" || { echo "FALLO: .auth-spinner-dot ausente en OnboardingWizard.js"; exit 1; }
grep -q "auth-spinner-dot" "$SCSS_FILE" || { echo "FALLO: .auth-spinner-dot ausente en SCSS"; exit 1; }
grep -q "auth-spin" "$SCSS_FILE" || { echo "FALLO: @keyframes auth-spin ausente en SCSS"; exit 1; }
grep -q "Comenzar a programar" "$WIZARD_JS" || { echo "FALLO: Texto sobrio 'Comenzar a programar' ausente en OnboardingWizard.js"; exit 1; }
echo "[OK]"

echo -n "43. Verificando transición optimista en Paso 3 (AC-UX-02)... "
grep -A 10 "btn-confirm-theme" "$WIZARD_JS" | grep -q "step-terms-telemetry" || {
    echo "FALLO: Transición optimista a step-terms-telemetry ausente en click de btnTheme"; exit 1;
}
echo "[OK]"

echo -n "44. Verificando despacho único sin llamadas duplicadas a onAction (AC-UX-03)... "
grep -A 20 "btn-confirm-theme" "$WIZARD_JS" | grep -q "this.onAction(\"confirm_theme\"" && {
    echo "FALLO: Invocación duplicada a this.onAction detectada en btnTheme"; exit 1;
}
echo "[OK]"

echo -n "45. Verificando secuencia espaciada de Inquirer en onAcceptTerms (AC-UX-04)... "
grep -A 25 "onAcceptTerms" "$CLEAN_TERM_JS" | grep -q "setTimeout" || {
    echo "FALLO: Retardo setTimeout espaciado ausente en onAcceptTerms"; exit 1;
}
grep -A 15 "btn-start-coding" "$WIZARD_JS" | grep -q "onAcceptTerms" || {
    echo "FALLO: onAcceptTerms ausente en click de btnTerms"; exit 1;
}
echo "[OK]"

echo -n "46. Verificando soporte de secuencia ANSI en onSelectTheme de CleanAgentTerminal.js (AC-UX-05 / AC-UX-06)... "
grep -A 5 "onSelectTheme" "$CLEAN_TERM_JS" | grep -q "seq" || {
    echo "FALLO: Parámetro seq ausente en onSelectTheme de CleanAgentTerminal.js"; exit 1;
}
echo "[OK]"

# 47. Verificar SPEC-047 (Criterios LAUNCH-01 a LAUNCH-06)
SPEC_FILE_047="specs/47-auto-advance-google-auth-and-instant-browser-launch.md"

echo -n "47. Verificando documento SPEC-047... "
[ -f "$SPEC_FILE_047" ] || { echo "FALLO: No existe $SPEC_FILE_047"; exit 1; }
echo "[OK]"

echo -n "48. Verificando auto-despacho incondicional de Enter para Opción 1 (LAUNCH-01)... "
grep -A 10 "Select login method" "$CLEAN_TERM_JS" | grep -q "_hasAutoSelectedLogin" || {
    echo "FALLO: Auto-despacho incondicional ausente en _handleAutoResponderStream"; exit 1;
}
echo "[OK]"

echo -n "49. Verificando precarga de URL y apertura instantánea de navegador (LAUNCH-02 y LAUNCH-03)... "
grep -A 15 "btn-auth-google" "$WIZARD_JS" | grep -q "this.authUrl" || {
    echo "FALLO: Apertura instantánea con this.authUrl ausente en OnboardingWizard.js"; exit 1;
}
grep -A 10 "setAuthUrl" "$WIZARD_JS" | grep -q "onOpenBrowser" || {
    echo "FALLO: Lanzamiento reactivo ausente en setAuthUrl de OnboardingWizard.js"; exit 1;
}
echo "[OK]"

echo -n "50. Verificando estado de carga del botón en clean-terminal.scss (LAUNCH-03)... "
grep -A 25 "btn-primary-auth" "$SCSS_FILE" | grep -q "loading" || {
    echo "FALLO: Selector .loading ausente en .btn-primary-auth de clean-terminal.scss"; exit 1;
}
echo "[OK]"

echo -n "51. Verificando ausencia total de emojis en UI (LAUNCH-04)... "
grep -q "⏳" "$WIZARD_JS" && { echo "FALLO: Emoji reloj de arena detectado en OnboardingWizard.js"; exit 1; }
grep -q "🚀" "$WIZARD_JS" && { echo "FALLO: Emoji cohete detectado en OnboardingWizard.js"; exit 1; }
echo "[OK]"

echo -n "52. Verificando secuencia espaciada en onAcceptTerms y botón de términos (LAUNCH-05)... "
grep -A 25 "onAcceptTerms" "$CLEAN_TERM_JS" | grep -q "setTimeout" || {
    echo "FALLO: Retardo espaciado ausente en onAcceptTerms"; exit 1;
}
grep -A 10 "btn-start-coding" "$WIZARD_JS" | grep -q "onAcceptTerms" || {
    echo "FALLO: onAcceptTerms ausente en botón de términos"; exit 1;
}
echo "[OK]"

# 53. Verificar SPEC-048 (Criterios BAR-01 a BAR-06)
SPEC_FILE_048="specs/48-top-app-bar-and-material3-account-menu.md"
ACCOUNT_MODAL_JS="nova-src/src/antigravity2/AccountMenuModal.js"

echo -n "53. Verificando documento SPEC-048... "
[ -f "$SPEC_FILE_048" ] || { echo "FALLO: No existe $SPEC_FILE_048"; exit 1; }
echo "[OK]"

echo -n "54. Verificando .clean-agent-top-bar de 48px en SCSS y CleanAgentTerminal.js (BAR-01)... "
grep -q "clean-agent-top-bar" "$SCSS_FILE" || { echo "FALLO: clean-agent-top-bar ausente en SCSS"; exit 1; }
grep -q "height: 48px" "$SCSS_FILE" || { echo "FALLO: Altura 48px ausente en SCSS"; exit 1; }
grep -q "clean-agent-top-bar" "$CLEAN_TERM_JS" || { echo "FALLO: clean-agent-top-bar ausente en CleanAgentTerminal.js"; exit 1; }
echo "[OK]"

echo -n "55. Verificando desplazamiento anti-colisión calc(100vh - 48px) en viewport (BAR-02)... "
grep -q "calc(100vh - 48px)" "$SCSS_FILE" || { echo "FALLO: Ajuste anti-colisión ausente en SCSS"; exit 1; }
grep -q "has-top-bar" "$CLEAN_TERM_JS" || { echo "FALLO: Clase has-top-bar ausente en CleanAgentTerminal.js"; exit 1; }
echo "[OK]"

echo -n "56. Verificando erradicación total de window.confirm (BAR-04)... "
grep -q "window\.confirm" "$CLEAN_TERM_JS" && {
    echo "FALLO: window.confirm aún presente en CleanAgentTerminal.js"; exit 1;
}
echo "[OK]"

echo -n "57. Verificando componente AccountMenuModal.js y su integración (BAR-04 y BAR-05)... "
[ -f "$ACCOUNT_MODAL_JS" ] || { echo "FALLO: No existe AccountMenuModal.js"; exit 1; }
grep -q "AccountMenuModal" "$CLEAN_TERM_JS" || { echo "FALLO: AccountMenuModal no referenciado en CleanAgentTerminal.js"; exit 1; }
grep -q "account-menu-overlay" "$ACCOUNT_MODAL_JS" || { echo "FALLO: account-menu-overlay ausente en AccountMenuModal.js"; exit 1; }
grep -q "account-menu-overlay" "$SCSS_FILE" || { echo "FALLO: account-menu-overlay ausente en SCSS"; exit 1; }
echo "[OK]"

echo -n "58. Verificando ausencia de emojis en AccountMenuModal.js... "
grep -q "⏳" "$ACCOUNT_MODAL_JS" && { echo "FALLO: Emoji reloj de arena detectado en AccountMenuModal.js"; exit 1; }
grep -q "🚀" "$ACCOUNT_MODAL_JS" && { echo "FALLO: Emoji cohete detectado en AccountMenuModal.js"; exit 1; }
echo "[OK]"

# ==============================================================================
# 59-64. Verificar SPEC-049 (Criterios SEAM-01 a SEAM-06)
# ==============================================================================
SPEC_FILE_049="specs/49-seamless-onboarding-transition-and-deep-logout.md"

echo -n "59. Verificando documento SPEC-049... "
[ -f "$SPEC_FILE_049" ] || { echo "FALLO: No existe $SPEC_FILE_049"; exit 1; }
echo "[OK]"

echo -n "60. Verificando retención de velo protector en OnboardingWizard.js (SEAM-01)... "
grep -A 10 "btn-start-coding" "$WIZARD_JS" | grep -q "fadeOut(350)" && {
    echo "FALLO: btn-start-coding aún invoca fadeOut(350) directamente en el click"; exit 1;
}
echo "[OK]"

echo -n "61. Verificando detección de ready prompt con _termsAccepted y safety timeout (1400ms) (SEAM-02)... "
grep -q "_termsAccepted" "$CLEAN_TERM_JS" || {
    echo "FALLO: Guardia _termsAccepted ausente en CleanAgentTerminal.js"; exit 1;
}
grep -q "What would you like to do" "$CLEAN_TERM_JS" || {
    echo "FALLO: Prompt 'What would you like to do' no monitoreado en CleanAgentTerminal.js"; exit 1;
}
grep -A 25 "onAcceptTerms" "$CLEAN_TERM_JS" | grep -q "1400" || {
    echo "FALLO: Timeout de seguridad de 1400ms ausente en onAcceptTerms"; exit 1;
}
echo "[OK]"

echo -n "62. Verificando purga diferida de credenciales Linux con _pendingCredentialPurge (SEAM-04)... "
grep -q "_pendingCredentialPurge" "$CLEAN_TERM_JS" || {
    echo "FALLO: Bandera _pendingCredentialPurge ausente en CleanAgentTerminal.js"; exit 1;
}
grep -A 15 "_pendingCredentialPurge" "$CLEAN_TERM_JS" | grep -q "rm -rf" || {
    echo "FALLO: Comando rm -rf ausente en bloque de purga diferida"; exit 1;
}
echo "[OK]"

echo -n "63. Verificando vaciado de portapapeles Android en logout() (SEAM-05)... "
grep -A 20 "async logout()" "$CLEAN_TERM_JS" | grep -q "clipboard.copy" || {
    echo "FALLO: Vaciado de portapapeles ausente en logout()"; exit 1;
}
echo "[OK]"

# ==============================================================================
# 64-68. Verificar SPEC-050 (Criterios PILL-01 a PILL-08)
# ==============================================================================
echo -n "64. Verificando documento SPEC-050... "
[ -f "$SPEC_FILE_050" ] || { echo "FALLO: No existe $SPEC_FILE_050"; exit 1; }
echo "[OK]"

echo -n "65. Verificando componente FloatingInputPill.js (PILL-01, PILL-02, PILL-03)... "
[ -f "$PILL_JS" ] || { echo "FALLO: No existe $PILL_JS"; exit 1; }
grep -q "FloatingInputPill" "$PILL_JS" || { echo "FALLO: Clase FloatingInputPill ausente"; exit 1; }
grep -q "Pregúntale a Antigravity..." "$PILL_JS" || { echo "FALLO: Placeholder exacto ausente en FloatingInputPill.js"; exit 1; }
grep -q "isComposing" "$PILL_JS" || { echo "FALLO: Salvaguarda IME isComposing ausente"; exit 1; }
echo "[OK]"

echo -n "66. Verificando integración y ciclo de vida de FloatingInputPill en CleanAgentTerminal.js (PILL-04, PILL-05)... "
grep -q "FloatingInputPill" "$CLEAN_TERM_JS" || { echo "FALLO: FloatingInputPill no importado en CleanAgentTerminal.js"; exit 1; }
grep -q "_mountFloatingPill" "$CLEAN_TERM_JS" || { echo "FALLO: Método _mountFloatingPill ausente"; exit 1; }
grep -q "has-input-pill" "$CLEAN_TERM_JS" || { echo "FALLO: Clase has-input-pill ausente en CleanAgentTerminal.js"; exit 1; }
echo "[OK]"

echo -n "67. Verificando estilos de píldora flotante y anti-colisión en clean-terminal.scss (PILL-06)... "
grep -q "floating-input-pill" "$SCSS_FILE" || { echo "FALLO: Regla .floating-input-pill ausente en SCSS"; exit 1; }
grep -q "has-input-pill" "$SCSS_FILE" || { echo "FALLO: Regla .has-input-pill ausente en SCSS"; exit 1; }
grep -q "pill-send-btn" "$SCSS_FILE" || { echo "FALLO: Regla .pill-send-btn ausente en SCSS"; exit 1; }
echo "[OK]"

echo -n "68. Verificando versión 2.7.0 y versionCode 20700 en configuración (MODEL-FIX)... "
grep -q 'version="2.7.0"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versión 2.7.0"; exit 1; }
grep -q 'android-versionCode="20700"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene android-versionCode 20700"; exit 1; }
grep -q '"version": "2.7.0"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene versión 2.7.0"; exit 1; }
echo "[OK]"

# ==============================================================================
# 69-75. Verificar SPEC-051 (Criterios MODEL-01 a MODEL-06, CLEAR-01/02, PILL-09 a PILL-11)
# ==============================================================================
echo -n "69. Verificando documento SPEC-051... "
[ -f "$SPEC_FILE_051" ] || { echo "FALLO: No existe $SPEC_FILE_051"; exit 1; }
echo "[OK]"

echo -n "70. Verificando catálogo AGY_MODELS con IDs reales y comandos /model (MODEL-01)... "
grep -q "AGY_MODELS" "$CLEAN_TERM_JS" || { echo "FALLO: Catálogo AGY_MODELS ausente en CleanAgentTerminal.js"; exit 1; }
grep -qF '"gemini-3.8-flash-high"' "$CLEAN_TERM_JS" || { echo "FALLO: Modelo gemini-3.8-flash-high ausente"; exit 1; }
grep -qF '"gemini-3.1-pro-high"' "$CLEAN_TERM_JS" || { echo "FALLO: Modelo gemini-3.1-pro-high ausente"; exit 1; }
grep -qF '"claude-sonnet-4-6"' "$CLEAN_TERM_JS" || { echo "FALLO: Modelo claude-sonnet-4-6 ausente"; exit 1; }
grep -qF '"gpt-oss-120b-medium"' "$CLEAN_TERM_JS" || { echo "FALLO: Modelo gpt-oss-120b-medium ausente"; exit 1; }
echo "[OK]"

echo -n "71. Verificando chip selector en el markup de la Top App Bar (MODEL-02)... "
grep -q "model-selector-btn" "$CLEAN_TERM_JS" || { echo "FALLO: model-selector-btn ausente en el markup del top bar"; exit 1; }
grep -q "model-selector-label" "$CLEAN_TERM_JS" || { echo "FALLO: model-selector-label ausente en el markup del top bar"; exit 1; }
grep -q "model-selector-chevron" "$CLEAN_TERM_JS" || { echo "FALLO: model-selector-chevron ausente en el markup del top bar"; exit 1; }
grep -q "_renderModelSelector" "$CLEAN_TERM_JS" || { echo "FALLO: Método _renderModelSelector ausente"; exit 1; }
echo "[OK]"

echo -n "72. Verificando ciclo de vida del menú de modelos y despacho PTY (MODEL-03, MODEL-04, MODEL-06)... "
grep -q "_showModelDropdown" "$CLEAN_TERM_JS" || { echo "FALLO: Método _showModelDropdown ausente"; exit 1; }
grep -q "_hideModelDropdown" "$CLEAN_TERM_JS" || { echo "FALLO: Método _hideModelDropdown ausente"; exit 1; }
grep -q "model-selector-scrim" "$CLEAN_TERM_JS" || { echo "FALLO: Scrim de cierre táctil ausente"; exit 1; }
grep -A 8 "_selectModel(model)" "$CLEAN_TERM_JS" | grep -q 'this.websocket.send(`/model ${model.id}\\r`)' || {
    echo "FALLO: Despacho de /model al WebSocket ausente en _selectModel"; exit 1;
}
grep -A 12 "_showModelDropdown()" "$CLEAN_TERM_JS" | grep -q "floatingPill?.hide()" || {
    echo "FALLO: La píldora no cede el foco al abrir el menú de modelos"; exit 1;
}
echo "[OK]"

echo -n "73. Verificando detección del modelo activo y anti-truncamiento de Flash Lite (MODEL-05)... "
grep -q "MODEL_STREAM_REGEX" "$CLEAN_TERM_JS" || { echo "FALLO: MODEL_STREAM_REGEX ausente"; exit 1; }
grep -q "_updateModelLabel" "$CLEAN_TERM_JS" || { echo "FALLO: Método _updateModelLabel ausente"; exit 1; }
grep -q "Flash Lite|Flash|Pro" "$CLEAN_TERM_JS" || {
    echo "FALLO: Orden de alternancia incorrecto. 'Flash Lite' debe preceder a 'Flash' o se truncará"; exit 1;
}
echo "[OK]"

echo -n "74. Verificando auto-limpieza one-shot del banner de arranque (CLEAR-01, CLEAR-02)... "
grep -q "_hasAutoCleared = false" "$CLEAN_TERM_JS" || { echo "FALLO: Bandera _hasAutoCleared ausente en el constructor"; exit 1; }
[ "$(grep -c '_hasAutoCleared = false' "$CLEAN_TERM_JS")" -ge 2 ] || {
    echo "FALLO: _hasAutoCleared no se rearma en restartSession (CLEAR-02)"; exit 1;
}
grep -q "cleanText" "$CLEAN_TERM_JS" || { echo "FALLO: Limpieza ANSI cleanText ausente"; exit 1; }
grep -A 15 "!this._hasAutoCleared" "$CLEAN_TERM_JS" | grep -q "this.terminal.clear()" || {
    echo "FALLO: Invocación de terminal.clear() ausente en el bloque de auto-limpieza"; exit 1;
}
echo "[OK]"

echo -n "75. Verificando anclaje visualViewport de la píldora sobre el teclado virtual (PILL-09, PILL-10)... "
grep -q "visualViewport" "$PILL_JS" || { echo "FALLO: Suscripción a visualViewport ausente en FloatingInputPill.js"; exit 1; }
grep -q "_attachViewportTracking" "$PILL_JS" || { echo "FALLO: Método _attachViewportTracking ausente"; exit 1; }
grep -q "_detachViewportTracking" "$PILL_JS" || { echo "FALLO: Método _detachViewportTracking ausente"; exit 1; }
grep -q 'addEventListener("resize"' "$PILL_JS" || { echo "FALLO: Listener resize ausente"; exit 1; }
grep -q 'addEventListener("scroll"' "$PILL_JS" || { echo "FALLO: Listener scroll ausente"; exit 1; }
grep -q 'removeEventListener("resize"' "$PILL_JS" || { echo "FALLO: Desuscripción de resize ausente en dismiss"; exit 1; }
grep -q 'removeEventListener("scroll"' "$PILL_JS" || { echo "FALLO: Desuscripción de scroll ausente en dismiss"; exit 1; }
grep -q "env(safe-area-inset-bottom" "$PILL_JS" || { echo "FALLO: Compensación de safe-area ausente en el reanclaje"; exit 1; }
echo "[OK]"

echo -n "76. Verificando estilos del selector de modelo y pulido de la píldora en SCSS (MODEL-02, MODEL-03, PILL-10, PILL-11)... "
grep -q ".model-selector-btn" "$SCSS_FILE" || { echo "FALLO: Regla .model-selector-btn ausente en SCSS"; exit 1; }
grep -q ".model-selector-menu" "$SCSS_FILE" || { echo "FALLO: Regla .model-selector-menu ausente en SCSS"; exit 1; }
grep -q ".model-option" "$SCSS_FILE" || { echo "FALLO: Regla .model-option ausente en SCSS"; exit 1; }
grep -q "z-index: 1100" "$SCSS_FILE" || { echo "FALLO: z-index 1100 ausente en el overlay del menú de modelos"; exit 1; }
grep -q "focus-within" "$SCSS_FILE" || { echo "FALLO: Regla :focus-within ausente en SCSS (PILL-11)"; exit 1; }
grep -q "bottom 0.15s ease" "$SCSS_FILE" || { echo "FALLO: Transición de reanclaje 'bottom' ausente en SCSS (PILL-10)"; exit 1; }
echo "[OK]"

echo -n "77. Verificando ausencia total de emojis en el código de producción (AC-UIX-001)... "
for f in "$CLEAN_TERM_JS" "$PILL_JS" "$SCSS_FILE"; do
    if LC_ALL=C grep -qP '\xF0\x9F[\x8C-\xAB]|\xE2[\x98-\x9E]|\xEF\xB8\x8F' "$f"; then
        echo "FALLO: Emoji detectado en $f"; exit 1;
    fi
done
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-036 A SPEC-051 HAN SIDO SUPERADAS EXITOSAMENTE ==="

# ==============================================================================
# 78-83. Verificar SPEC-052 (Criterios de SCROLL, MENU, 2F, VISUAL y VERSIONADO)
# ==============================================================================
echo -n "78. Verificando versión 2.7.0 y versionCode 20700 en configuración (VERSIONADO)... "
grep -q 'version="2.7.0"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versión 2.7.0"; exit 1; }
grep -q 'android-versionCode="20700"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene android-versionCode 20700"; exit 1; }
grep -q '"version": "2.7.0"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene versión 2.7.0"; exit 1; }
echo "[OK]"

echo -n "79. Verificando polaridad de scroll en scrollByPixels (SCROLL)... "
grep -q "this.terminal.scrollLines(lines);" "$CLEAN_TERM_JS" || grep -q "this.terminal.scrollLines(lines);" "nova-src/src/antigravity2/TerminalTouchNavigation.js" || { echo "FALLO: scrollLines(lines) sin negación ausente"; exit 1; }
echo "[OK]"

echo -n "80. Verificando limpieza de EXPLICIT_PATTERNS (MENU)... "
grep -A 10 "EXPLICIT_PATTERNS =" "nova-src/src/antigravity2/TerminalTouchNavigation.js" | grep -q "●|○" && { echo "FALLO: EXPLICIT_PATTERNS contiene ● o ○"; exit 1; }
echo "[OK]"

echo -n "81. Verificando ventana de 100ms y guard de 15px en 2 dedos (2F)... "
grep -q "> 100" "nova-src/src/antigravity2/TerminalTouchNavigation.js" || { echo "FALLO: Ventana temporal de 100ms ausente"; exit 1; }
grep -q "< 15" "nova-src/src/antigravity2/TerminalTouchNavigation.js" || { echo "FALLO: Guard de borde de 15px ausente"; exit 1; }
echo "[OK]"

echo -n "82. Verificando scroll-indicator visual (VISUAL)... "
grep -q ".scroll-indicator" "$SCSS_FILE" || { echo "FALLO: .scroll-indicator ausente en SCSS"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS ESTÁTICAS DE SPEC-052 HAN SIDO SUPERADAS EXITOSAMENTE ==="





