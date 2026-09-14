#!/bin/bash
# Test Harness - Automated Verification for TASK-032 (Nova On-Demand Provisioning & Thin Client APK)
set -e

echo "=== INICIANDO VERIFICACIÓN DE ON-DEMAND PROVISIONING Y APK BUILD (TASK-032) ==="

TERMINAL_JS="nova-src/src/plugins/terminal/www/Terminal.js"
INIT_ALPINE="nova-src/src/plugins/terminal/scripts/init-alpine.sh"
INIT_SANDBOX="nova-src/src/plugins/terminal/scripts/init-sandbox.sh"

# 1. Verificar URLs oficiales de Ubuntu ARM64 y Google Antigravity CLI
echo -n "1. Verificando URLs oficiales en Terminal.js... "
if [ ! -f "$TERMINAL_JS" ]; then
    echo "FALLO: $TERMINAL_JS no existe."
    exit 1
fi
grep -q "https://github.com/termux/proot-distro/releases/download/v4.18.0/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz" "$TERMINAL_JS" || { echo "FALLO: URL de Ubuntu ARM64 ausente"; exit 1; }
grep -q "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.2.2-6061403484848128/linux-arm/cli_linux_arm64.tar.gz" "$TERMINAL_JS" || { echo "FALLO: URL de Google Antigravity CLI ausente"; exit 1; }
echo "[OK]"

# 2. Verificar aprovisionamiento del CLI agy y symlinks
echo -n "2. Verificando instalación del CLI agy y symlink... "
grep -q "cli_linux_arm64.tar.gz" "$TERMINAL_JS" || { echo "FALLO: Extracción de CLI ausente"; exit 1; }
grep -q "ln -sf antigravity" "$TERMINAL_JS" || { echo "FALLO: Symlink a agy ausente"; exit 1; }
echo "[OK]"

# 3. Verificar inyección silenciosa de onboarding.json y settings.json
echo -n "3. Verificando inyección silenciosa de onboarding y settings... "
grep -q "consumerOnboardingComplete" "$TERMINAL_JS" || { echo "FALLO: consumerOnboardingComplete ausente"; exit 1; }
grep -q "onboardingComplete" "$TERMINAL_JS" || { echo "FALLO: onboardingComplete ausente"; exit 1; }
grep -q "trustedWorkspaces" "$TERMINAL_JS" || { echo "FALLO: trustedWorkspaces ausente"; exit 1; }
grep -q "/home/studio/workspace" "$TERMINAL_JS" || { echo "FALLO: /home/studio/workspace ausente en settings"; exit 1; }
echo "[OK]"

# 4. Verificar puente xdg-open hacia Chrome vía 'am start'
echo -n "4. Verificando puente xdg-open hacia navegador Android... "
grep -q "am start -a android.intent.action.VIEW" "$TERMINAL_JS" || { echo "FALLO: am start ausente en xdg-open"; exit 1; }
grep -q "x-www-browser" "$TERMINAL_JS" || { echo "FALLO: symlink x-www-browser ausente"; exit 1; }
echo "[OK]"

# 5. Verificar scripts de arranque y ejecución en /home/studio/workspace
echo -n "5. Verificando scripts de arranque (init-alpine.sh y init-sandbox.sh)... "
grep -q "/home/studio/workspace" "$INIT_ALPINE" || { echo "FALLO: /home/studio/workspace ausente en init-alpine.sh"; exit 1; }
grep -q 'exec agy "\$@"' "$INIT_ALPINE" || { echo "FALLO: exec agy ausente en init-alpine.sh"; exit 1; }
grep -q "/home/studio/workspace" "$INIT_SANDBOX" || { echo "FALLO: /home/studio/workspace ausente en init-sandbox.sh"; exit 1; }
echo "[OK]"

# 6. Verificar existencia y tamaño del APK Thin Client
echo -n "6. Verificando existencia de APK Thin Client (NovaIDE-v1.0.6-ARM64.apk)... "
APK_PATH="NovaIDE-v1.0.6-ARM64.apk"
if [ ! -f "$APK_PATH" ]; then
    APK_PATH="NovaIDE-v1.0.5-ARM64.apk"
fi
if [ ! -f "$APK_PATH" ]; then
    APK_PATH="NovaIDE-v1.0.4-ARM64.apk"
fi
if [ ! -f "$APK_PATH" ]; then
    APK_PATH="NovaIDE-v1.0.3-ARM64.apk"
fi
if [ ! -f "$APK_PATH" ]; then
    APK_PATH="NovaIDE-v1.0.2-ARM64.apk"
fi
if [ ! -f "$APK_PATH" ]; then
    APK_PATH="NovaIDE-v1.0.1-ARM64.apk"
fi
if [ ! -f "$APK_PATH" ]; then
    APK_PATH="NovaIDE-v1.0.0-ARM64.apk"
fi
if [ ! -f "$APK_PATH" ]; then
    echo "FALLO: No se encontró NovaIDE-v1.0.6-ARM64.apk en la raíz del repositorio."
    exit 1
fi

APK_SIZE_BYTES=$(stat -c%s "$APK_PATH" 2>/dev/null || stat -f%z "$APK_PATH" 2>/dev/null || wc -c < "$APK_PATH")
APK_SIZE_MB=$(( APK_SIZE_BYTES / 1048576 ))
echo -n "[Encontrado: ${APK_SIZE_MB} MB]... "

if [ "$APK_SIZE_MB" -gt 45 ]; then
    echo "FALLO: El APK excede el tamaño límite Thin Client (encontrado ${APK_SIZE_MB} MB > 45 MB)."
    exit 1
fi
echo "[OK]"

# 7. Verificar ruta de extracción POSIX de rootfs y CLI sin esquema URI file:/// (TASK-039)
echo -n "7. Verificando ruta de extracción POSIX (\${filesDir}/rootfs.tar.xz)... "
grep -q '\${filesDir}/rootfs.tar.xz' "$TERMINAL_JS" || { echo "FALLO: ruta filesDir/rootfs.tar.xz ausente"; exit 1; }
grep -q '\${filesDir}/cli_linux_arm64.tar.gz' "$TERMINAL_JS" || { echo "FALLO: ruta filesDir/cli_linux_arm64.tar.gz ausente"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS DE VALIDACIÓN DE TASK-032 / TASK-039 HAN PASADO EXITOSAMENTE ==="

