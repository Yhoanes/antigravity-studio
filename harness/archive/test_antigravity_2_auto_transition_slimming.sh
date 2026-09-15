#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-025 Auto-Transition and APK Slimming Verification
# ==============================================================================
set -e

SPEC_FILE="specs/25-antigravity-2-mobile-auto-transition-and-apk-slimming.md"
AGENT_BRIDGE="nova-src/src/antigravity2/AgentBridge.js"
CHAT_CANVAS="nova-src/src/antigravity2/ChatCanvas.js"
TERMINAL_JS="nova-src/src/plugins/terminal/www/Terminal.js"
ASSETS_DIR="nova-src/platforms/android/app/src/main/assets/antigravity"

echo "=== INICIANDO VALIDACIÓN FORMAL DE SPEC-025 ==="

# 1. Verificar existencia del documento de especificación
echo -n "1. Verificando existencia de SPEC-025... "
[ -f "$SPEC_FILE" ] || { echo "FALLO: No existe $SPEC_FILE"; exit 1; }
echo "[OK]"

# 2. Verificar ausencia de recursión en installRuntime() de AgentBridge.js
echo -n "2. Verificando ausencia de recursión en installRuntime()... "
# installRuntime no debe llamar a this.connect()
if sed -n '/async installRuntime/,/^  }/p' "$AGENT_BRIDGE" | grep -q "await this\.connect()"; then
  echo "FALLO: installRuntime aún contiene la llamada recursiva a this.connect()"; exit 1;
fi
echo "[OK]"

# 3. Verificar continuación secuencial en connect() tras aprovisionamiento
echo -n "3. Verificando secuencia lineal en connect()... "
# connect() no debe hacer return inmediatamente después de installRuntime()
if grep -A 2 "await this\.installRuntime()" "$AGENT_BRIDGE" | grep -q "return;"; then
  echo "FALLO: connect() aún aborta con return; tras installRuntime()"; exit 1;
fi
echo "[OK]"

# 4. Verificar retiro de SetupCard y WelcomeHero en ChatCanvas.js
echo -n "4. Verificando retiro de SetupCard y despliegue de WelcomeHero... "
grep -q "this\.dismissSetupCard()" "$CHAT_CANVAS" || { echo "FALLO: dismissSetupCard no invocado en ChatCanvas"; exit 1; }
grep -q "this\.renderWelcomeHero()" "$CHAT_CANVAS" || { echo "FALLO: renderWelcomeHero no invocado en ChatCanvas"; exit 1; }
echo "[OK]"

# 5. Verificar optimización de peso (APK Slimming)
echo -n "5. Verificando eliminación de archivo duplicado en assets... "
if [ -f "$ASSETS_DIR/ubuntu_arm64.tar.gz" ]; then
  echo "FALLO: El archivo duplicado $ASSETS_DIR/ubuntu_arm64.tar.gz aún existe"; exit 1;
fi
if [ ! -f "$ASSETS_DIR/rootfs/ubuntu_arm64.tar.gz" ]; then
  echo "FALLO: La ruta canónica $ASSETS_DIR/rootfs/ubuntu_arm64.tar.gz no existe"; exit 1;
fi
echo "[OK]"

# 6. Verificar ruta canónica de extracción en Terminal.js
echo -n "6. Verificando ruta canónica en Terminal.js... "
grep -q "antigravity/rootfs/ubuntu_arm64\.tar\.gz" "$TERMINAL_JS" || { echo "FALLO: Terminal.js no apunta a la ruta canónica"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS DE CALIDAD DE SPEC-025 HAN SIDO VERIFICADAS EXITOSAMENTE ==="
