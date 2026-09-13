#!/bin/bash
# Test Harness - Automated Verification for SPEC-015
set -e

echo "=== INICIANDO VERIFICACIÓN DE OAUTH SMART CARD, SILENT ONBOARDING Y FILE TREE (SPEC-015) ==="

INSTALLER_JAVA="termux-src/app/src/main/java/com/termux/app/TermuxInstaller.java"
ACTIVITY_JAVA="termux-src/app/src/main/java/com/termux/app/TermuxActivity.java"
PROJECTS_CONTROLLER="termux-src/app/src/main/java/com/termux/app/terminal/TermuxProjectsListViewController.java"
ACTIVITY_XML="termux-src/app/src/main/res/layout/activity_termux.xml"
ROOTFS_DIR="/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu"

# 1. Verificar definición de OAuth Smart Card en layout XML
echo -n "Verificando oauth_smart_card en activity_termux.xml... "
grep -q 'android:id="@+id/oauth_smart_card"' "$ACTIVITY_XML" || { echo "FALLO: oauth_smart_card no encontrado en layout"; exit 1; }
grep -q 'android:id="@+id/oauth_action_primary"' "$ACTIVITY_XML" || { echo "FALLO: oauth_action_primary ausente"; exit 1; }
echo "[OK]"

# 2. Verificar lógica de control de tarjeta en TermuxActivity.java
echo -n "Verificando lógica de inyección de código OAuth en TermuxActivity.java... "
grep -q "oauth_smart_card" "$ACTIVITY_JAVA" || { echo "FALLO: Referencia a oauth_smart_card ausente en TermuxActivity"; exit 1; }
grep -q "session.write" "$ACTIVITY_JAVA" || { echo "FALLO: Inyección de sesión ausente"; exit 1; }
echo "[OK]"

# 3. Verificar pre-sembrado completo en TermuxInstaller.java (Silent Onboarding)
echo -n "Verificando claves de Silent Onboarding en TermuxInstaller.java... "
grep -q "securityAgreed" "$INSTALLER_JAVA" || { echo "FALLO: securityAgreed ausente en TermuxInstaller"; exit 1; }
grep -q "colorSchemeIndex" "$INSTALLER_JAVA" || { echo "FALLO: colorSchemeIndex ausente en TermuxInstaller"; exit 1; }
grep -q "workspaceTrust" "$INSTALLER_JAVA" || { echo "FALLO: workspaceTrust ausente en TermuxInstaller"; exit 1; }
grep -q "trustedWorkspaces" "$INSTALLER_JAVA" || { echo "FALLO: trustedWorkspaces ausente en TermuxInstaller"; exit 1; }
grep -F -q '\"*\"' "$INSTALLER_JAVA" || { echo "FALLO: Comodín '*' ausente en trustedWorkspaces"; exit 1; }
echo "[OK]"

# 4. Verificar transformación a Explorador de Archivos en TermuxProjectsListViewController.java
echo -n "Verificando árbol de archivos de proyecto en TermuxProjectsListViewController.java... "
grep -q "setActiveProjectDirectory" "$PROJECTS_CONTROLLER" || { echo "FALLO: setActiveProjectDirectory ausente"; exit 1; }
grep -q "isDirectory" "$PROJECTS_CONTROLLER" || { echo "FALLO: Distinción de directorios/archivos ausente"; exit 1; }
echo "[OK]"

# 5. Comprobación física de rootfs si está presente
if [ -d "$ROOTFS_DIR/root/.gemini/antigravity-cli" ]; then
    echo "=== RootFS físico detectado. Verificando JSONs de Onboarding ==="
    ONBOARD_JSON="$ROOTFS_DIR/root/.gemini/antigravity-cli/cache/onboarding.json"
    SETTINGS_JSON="$ROOTFS_DIR/root/.gemini/antigravity-cli/settings.json"

    echo -n "Verificando $ONBOARD_JSON... "
    grep -q '"securityAgreed": true' "$ONBOARD_JSON" || { echo "FALLO: securityAgreed no está en true"; exit 1; }
    echo "[OK]"

    echo -n "Verificando $SETTINGS_JSON... "
    grep -q '"workspaceTrust": true' "$SETTINGS_JSON" || { echo "FALLO: workspaceTrust no está en true"; exit 1; }
    echo "[OK]"
fi

echo "=== TODAS LAS ASERCIONES DE SPEC-015 SE HAN CUMPLIDO SATISFACTORIAMENTE ==="
