#!/bin/bash
# Test Harness - Automated Verification for SPEC-016
set -e

echo "=== INICIANDO VERIFICACIÓN DE RESILIENCIA EN FRÍO Y RUNTIME V1.5.0 (SPEC-016) ==="

INSTALLER_JAVA="termux-src/app/src/main/java/com/termux/app/TermuxInstaller.java"
ACTIVITY_JAVA="termux-src/app/src/main/java/com/termux/app/TermuxActivity.java"
BUILD_GRADLE="termux-src/app/build.gradle"

# 1. Verificar creación de $PREFIX/tmp y Polling Handler en TermuxActivity.java
echo -n "Verificando ColdBootUrlBridgeContract en TermuxActivity.java... "
grep -q 'new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "tmp")' "$ACTIVITY_JAVA" || {
    echo "FALLO: Creación física de tmp ausente en TermuxActivity"; exit 1;
}
grep -q "mUrlPollingHandler" "$ACTIVITY_JAVA" || {
    echo "FALLO: Polling Handler redundante ausente en TermuxActivity"; exit 1;
}
echo "[OK]"

# 2. Verificar inyección de Silent Onboarding post-extracción en TermuxInstaller.java
echo -n "Verificando FirstBootSilentOnboardingContract en TermuxInstaller.java... "
grep -q "securityAgreed" "$INSTALLER_JAVA" || { echo "FALLO: securityAgreed ausente en installer"; exit 1; }
grep -q "workspaceTrust" "$INSTALLER_JAVA" || { echo "FALLO: workspaceTrust ausente en installer"; exit 1; }
grep -q "colorSchemeIndex" "$INSTALLER_JAVA" || { echo "FALLO: colorSchemeIndex ausente en installer"; exit 1; }
echo "[OK]"

# 3. Verificar enlace de proyectos en New Session
echo -n "Verificando ProjectSessionBindingContract en TermuxActivity.java... "
grep -A 10 "setNewSessionButtonView" "$ACTIVITY_JAVA" | grep -q "showProjectSelectionDialog" || {
    echo "FALLO: new_session_button no enlaza con showProjectSelectionDialog"; exit 1;
}
echo "[OK]"

# 4. Verificar configuración de versión v1.5.0
echo -n "Verificando VersionBumpContract a v1.5.0... "
grep -q "1.5.0" "$BUILD_GRADLE" || grep -q "1.5.0" "app/build.gradle.kts" || {
    echo "FALLO: Versión 1.5.0 no configurada en build.gradle"; exit 1;
}
echo "[OK]"

echo "=== TODAS LAS ASERCIONES DE SPEC-016 SE HAN CUMPLIDO SATISFACTORIAMENTE ==="
