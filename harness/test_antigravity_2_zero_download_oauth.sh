#!/bin/bash
# Test Harness - Automated Verification for SPEC-024
set -e

SPEC_FILE="specs/24-antigravity-2-mobile-zero-download-and-oauth-bridge.md"
TERMINAL_FILE="nova-src/src/plugins/terminal/www/Terminal.js"
BRIDGE_FILE="nova-src/src/antigravity2/AgentBridge.js"
CANVAS_FILE="nova-src/src/antigravity2/ChatCanvas.js"
SIDEBAR_FILE="nova-src/src/antigravity2/SidebarDrawer.js"
CONFIG_FILE="nova-src/config.xml"
PACKAGE_FILE="nova-src/package.json"

echo "=== INICIANDO VERIFICACIÓN FORMAL DE ESPECIFICACIÓN ZERO-DOWNLOAD Y OAUTH (SPEC-024) ==="

# 1. Verificar existencia de la especificación
echo -n "1. Comprobando existencia de SPEC-024... "
if [ ! -f "$SPEC_FILE" ]; then
  echo "FALLO: No existe $SPEC_FILE"; exit 1;
fi
echo "[OK]"

# 2. Verificar contrato Zero-Download y extracción local
echo -n "2. Comprobando ZeroDownloadAssetsContract... "
grep -q "ZeroDownloadAssetsContract" "$SPEC_FILE" || { echo "FALLO: ZeroDownloadAssetsContract ausente"; exit 1; }
grep -q "system.extractAsset" "$SPEC_FILE" || { echo "FALLO: Referencia a system.extractAsset ausente"; exit 1; }
grep -q "ubuntu_arm64.tar.gz" "$SPEC_FILE" || { echo "FALLO: Referencia a ubuntu_arm64.tar.gz ausente"; exit 1; }
grep -q "cli_linux_arm64.tar.gz" "$SPEC_FILE" || { echo "FALLO: Referencia a cli_linux_arm64.tar.gz ausente"; exit 1; }
echo "[OK]"

# 3. Verificar auto-aprovisionamiento Zero-Click
echo -n "3. Comprobando ZeroClickBootContract... "
grep -q "ZeroClickBootContract" "$SPEC_FILE" || { echo "FALLO: ZeroClickBootContract ausente"; exit 1; }
grep -q "Terminal.isInstalled" "$SPEC_FILE" || { echo "FALLO: Verificación de instalación ausente"; exit 1; }
grep -q "READY" "$SPEC_FILE" || { echo "FALLO: Transición a READY ausente"; exit 1; }
echo "[OK]"

# 4. Verificar puente de autenticación Google OAuth
echo -n "4. Comprobando GoogleOAuthBridgeContract... "
grep -q "GoogleOAuthBridgeContract" "$SPEC_FILE" || { echo "FALLO: GoogleOAuthBridgeContract ausente"; exit 1; }
grep -q "triggerGoogleLogin" "$SPEC_FILE" || { echo "FALLO: triggerGoogleLogin ausente"; exit 1; }
grep -q "xdg-open" "$SPEC_FILE" || { echo "FALLO: xdg-open ausente"; exit 1; }
grep -q "FLAG_ACTIVITY_NEW_TASK" "$SPEC_FILE" || { echo "FALLO: FLAG_ACTIVITY_NEW_TASK ausente"; exit 1; }
echo "[OK]"

# 5. AC-OFFLINE-001: Presencia física de activos en el árbol de assets
echo -n "5. Comprobando AC-OFFLINE-001 (Presencia de assets offline)... "
ASSET_ROOTFS="nova-src/platforms/android/app/src/main/assets/antigravity/rootfs/ubuntu_arm64.tar.gz"
ASSET_CLI="nova-src/platforms/android/app/src/main/assets/antigravity/cli_linux_arm64.tar.gz"
if [ ! -f "$ASSET_ROOTFS" ]; then
  echo "FALLO: No existe $ASSET_ROOTFS"; exit 1;
fi
if [ ! -f "$ASSET_CLI" ]; then
  echo "FALLO: No existe $ASSET_CLI"; exit 1;
fi
echo "[OK]"

# 6. AC-OFFLINE-002: Supresión radical de downloadFile en Terminal.js
echo -n "6. Comprobando AC-OFFLINE-002 (Cero descargas de red)... "
DOWNLOAD_COUNT=$(grep -c "downloadFile" "$TERMINAL_FILE" || true)
if [ "$DOWNLOAD_COUNT" -ne 0 ]; then
  echo "FALLO: downloadFile aún presente en Terminal.js ($DOWNLOAD_COUNT ocurrencias)"; exit 1;
fi
echo "[OK]"

# 7. AC-OFFLINE-003: Invocación de system.extractAsset en Terminal.js
echo -n "7. Comprobando AC-OFFLINE-003 (Extracción local con system.extractAsset)... "
grep -q "system.extractAsset" "$TERMINAL_FILE" || { echo "FALLO: system.extractAsset ausente en Terminal.js"; exit 1; }
grep -q "antigravity/cli_linux_arm64.tar.gz" "$TERMINAL_FILE" || { echo "FALLO: Extracción de CLI ausente en Terminal.js"; exit 1; }
echo "[OK]"

# 8. AC-BOOT-001: Zero-Click auto-provisioning en AgentBridge.js
echo -n "8. Comprobando AC-BOOT-001 (Auto-aprovisionamiento Zero-Click)... "
grep -q "ZeroClickBootContract" "$BRIDGE_FILE" || { echo "FALLO: ZeroClickBootContract ausente en AgentBridge.js"; exit 1; }
grep -q "installRuntime" "$BRIDGE_FILE" || { echo "FALLO: installRuntime ausente en AgentBridge.js"; exit 1; }
echo "[OK]"

# 9. AC-BOOT-002: Transición a READY y banner interactivo
echo -n "9. Comprobando AC-BOOT-002 (Transición y banner en ChatCanvas)... "
grep -q "INITIALIZING" "$CANVAS_FILE" || { echo "FALLO: Estado INITIALIZING no gestionado en ChatCanvas.js"; exit 1; }
grep -q "READY" "$CANVAS_FILE" || { echo "FALLO: Estado READY no gestionado en ChatCanvas.js"; exit 1; }
echo "[OK]"

# 10. AC-OAUTH-001: triggerGoogleLogin y agy auth login
echo -n "10. Comprobando AC-OAUTH-001 (Puente de autenticación OAuth)... "
grep -q "triggerGoogleLogin" "$SIDEBAR_FILE" || { echo "FALLO: triggerGoogleLogin no invocado en SidebarDrawer.js"; exit 1; }
grep -q "triggerGoogleLogin" "$BRIDGE_FILE" || { echo "FALLO: triggerGoogleLogin no definido en AgentBridge.js"; exit 1; }
grep -q "agy auth login" "$BRIDGE_FILE" || { echo "FALLO: Comando agy auth login ausente en AgentBridge.js"; exit 1; }
echo "[OK]"

# 11. AC-OAUTH-002: xdg-open con --activity-new-task
echo -n "11. Comprobando AC-OAUTH-002 (FLAG_ACTIVITY_NEW_TASK en xdg-open)... "
grep -q -- "--activity-new-task" "$TERMINAL_FILE" || { echo "FALLO: --activity-new-task ausente en xdg-open de Terminal.js"; exit 1; }
echo "[OK]"

# 12. AC-OAUTH-003: authSuccess y persistencia de perfil
echo -n "12. Comprobando AC-OAUTH-003 (Manejo de authSuccess en SidebarDrawer)... "
grep -q "authSuccess" "$BRIDGE_FILE" || { echo "FALLO: authSuccess ausente en AgentBridge.js"; exit 1; }
grep -q "authSuccess" "$SIDEBAR_FILE" || { echo "FALLO: authSuccess no escuchado en SidebarDrawer.js"; exit 1; }
echo "[OK]"

# 13. Version Bump v2.0.3 (versionCode 20003)
echo -n "13. Comprobando versión 2.0.3 (20003)... "
grep -q 'version="2.0.3"' "$CONFIG_FILE" || { echo "FALLO: version 2.0.3 ausente en config.xml"; exit 1; }
grep -q 'android-versionCode="20003"' "$CONFIG_FILE" || { echo "FALLO: versionCode 20003 ausente en config.xml"; exit 1; }
grep -q '"version": "2.0.3"' "$PACKAGE_FILE" || { echo "FALLO: version 2.0.3 ausente en package.json"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS DE VERIFICACIÓN DE SPEC-024 HAN PASADO EXITOSAMENTE ==="
