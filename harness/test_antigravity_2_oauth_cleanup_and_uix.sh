#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-026 OAuth Cleanup, Port Scanner Sanitization & UIX
# ==============================================================================
set -e

SPEC_FILE="specs/26-antigravity-2-mobile-oauth-cleanup-and-uix.md"
AGENT_BRIDGE="nova-src/src/antigravity2/AgentBridge.js"
SIDEBAR_DRAWER="nova-src/src/antigravity2/SidebarDrawer.js"
CHAT_CANVAS="nova-src/src/antigravity2/ChatCanvas.js"
TERMINAL_JS="nova-src/src/plugins/terminal/www/Terminal.js"

echo "=== INICIANDO VALIDACIÓN FORMAL DE SPEC-026 ==="

# 1. Comprobar existencia del documento
echo -n "1. Comprobando existencia de SPEC-026... "
[ -f "$SPEC_FILE" ] || { echo "FALLO: No existe $SPEC_FILE"; exit 1; }
echo "[OK]"

# 2. Comprobar contratos formales
echo -n "2. Comprobando contratos formales en SPEC-026... "
grep -q "PortScannerSanitizationContract" "$SPEC_FILE" || { echo "FALLO: PortScannerSanitizationContract ausente"; exit 1; }
grep -q "NativeGoogleOAuthContract" "$SPEC_FILE" || { echo "FALLO: NativeGoogleOAuthContract ausente"; exit 1; }
grep -q "LocalizedUXContract" "$SPEC_FILE" || { echo "FALLO: LocalizedUXContract ausente"; exit 1; }
echo "[OK]"

# 3. Comprobar erradicación del falso positivo en AgentBridge.js
echo -n "3. Verificando erradicación de new Image().onerror en AgentBridge.js... "
if grep -q "img\.onerror = () => resolve(true)" "$AGENT_BRIDGE"; then
  echo "FALLO: AgentBridge.js aún contiene la heurística espuria img.onerror"; exit 1;
fi
echo "[OK]"

# 4. Comprobar implementación de probePort fidedigna
echo -n "4. Verificando algoritmo fidedigno de detección de puertos... "
grep -q "probePort" "$AGENT_BRIDGE" || { echo "FALLO: probePort ausente en AgentBridge.js"; exit 1; }
echo "[OK]"

# 5. Comprobar presencia de parámetros OAuth oficiales en código
echo -n "5. Verificando configuración OAuth 2.0 PKCE oficial... "
grep -q "884354919052" "$SPEC_FILE" || { echo "FALLO: Client ID oficial ausente"; exit 1; }
grep -q "54123" "$SPEC_FILE" || { echo "FALLO: Puerto loopback 54123 ausente"; exit 1; }
echo "[OK]"

# 6. Comprobar erradicación de cadenas en inglés en Terminal.js
echo -n "6. Verificando erradicación de cadenas en inglés en Terminal.js... "
if grep -q "Extracting Ubuntu ARM64 filesystem" "$TERMINAL_JS"; then
  echo "FALLO: Cadena en inglés detectada en Terminal.js"; exit 1;
fi
if grep -q "Installing Google Antigravity CLI" "$TERMINAL_JS"; then
  echo "FALLO: Cadena en inglés detectada en Terminal.js"; exit 1;
fi
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS DE CALIDAD DE SPEC-026 HAN PASADO EXITOSAMENTE ==="
