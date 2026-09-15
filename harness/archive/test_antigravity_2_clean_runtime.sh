#!/bin/bash
# Test Harness - Automated Verification for SPEC-023 (Clean Runtime and Zero-Mock)
set -e

SPEC_FILE="specs/23-antigravity-2-mobile-clean-runtime-and-zero-mock.md"
TYPES_FILE="nova-src/src/antigravity2/types.js"
SIDEBAR_FILE="nova-src/src/antigravity2/SidebarDrawer.js"
CANVAS_FILE="nova-src/src/antigravity2/ChatCanvas.js"
BRIDGE_FILE="nova-src/src/antigravity2/AgentBridge.js"
APP_FILE="nova-src/src/antigravity2/AntigravityApp.js"
CONFIG_FILE="nova-src/config.xml"
PACKAGE_FILE="nova-src/package.json"

echo "================================================================================"
echo "  SUITE DE CERTIFICACIÓN FORMAL DE SPEC-023: CLEAN RUNTIME & ZERO-MOCK"
echo "================================================================================"

# 1. Comprobar existencia de SPEC-023
echo -n "[AC-SPEC-023] Verificando especificación formal... "
if [ ! -f "$SPEC_FILE" ]; then
  echo "FALLO: No existe $SPEC_FILE"; exit 1;
fi
echo "PASÓ"

# 2. AC-CLN-001: types.js Zero-Mock
echo -n "[AC-CLN-001] types.js: Invariante Zero-Mock (perfil neutral Invitado)... "
if grep -q "shadrick1212@gmail.com" "$TYPES_FILE"; then
  echo "FALLO: Correo personal detectado en types.js"; exit 1;
fi
grep -q "Invitado" "$TYPES_FILE" || { echo "FALLO: Perfil Invitado ausente en types.js"; exit 1; }
grep -q "email: null" "$TYPES_FILE" || { echo "FALLO: email: null ausente en types.js"; exit 1; }
echo "PASÓ"

# 3. AC-CLN-002: SidebarDrawer.js Estado Invitado y vinculación Google
echo -n "[AC-CLN-002] SidebarDrawer.js: Estado de usuario no autenticado... "
grep -q "Invitado" "$SIDEBAR_FILE" || { echo "FALLO: Invitado no referenciado en SidebarDrawer"; exit 1; }
grep -q "Vincular Cuenta" "$SIDEBAR_FILE" || { echo "FALLO: Botón de vinculación Google ausente"; exit 1; }
echo "PASÓ"

# 4. AC-CLN-003: SidebarDrawer.js Descubrimiento dinámico de proyectos
echo -n "[AC-CLN-003] SidebarDrawer.js: Descubrimiento dinámico sin mocks... "
if grep -q "calculadora" "$SIDEBAR_FILE"; then
  echo "FALLO: Mock calculadora detectado en SidebarDrawer.js"; exit 1;
fi
if grep -q "ecommerce-api" "$SIDEBAR_FILE"; then
  echo "FALLO: Mock ecommerce-api detectado en SidebarDrawer.js"; exit 1;
fi
grep -q "/storage/emulated/0/Projects" "$SIDEBAR_FILE" || { echo "FALLO: Ruta canónica de proyectos ausente"; exit 1; }
echo "PASÓ"

# 5. AC-CLN-004: SidebarDrawer.js Historial de chats vacío limpio
echo -n "[AC-CLN-004] SidebarDrawer.js: Historial de chats limpio (Zero-Mock)... "
if grep -q "chat-001" "$SIDEBAR_FILE"; then
  echo "FALLO: Mock chat-001 detectado en SidebarDrawer.js"; exit 1;
fi
grep -q "this.chatHistory = \[\]" "$SIDEBAR_FILE" || { echo "FALLO: this.chatHistory no inicializado vacío"; exit 1; }
grep -q "Sin conversaciones previas" "$SIDEBAR_FILE" || { echo "FALLO: Empty state de chats ausente"; exit 1; }
echo "PASÓ"

# 6. AC-CLN-005: ChatCanvas.js Erradicación de mensajes simulados
echo -n "[AC-CLN-005] ChatCanvas.js: Erradicación de renderWelcomeConversation... "
if grep -q "renderWelcomeConversation" "$CANVAS_FILE"; then
  echo "FALLO: renderWelcomeConversation aún existe en ChatCanvas.js"; exit 1;
fi
if grep -q "renderWelcomeConversation" "$APP_FILE"; then
  echo "FALLO: renderWelcomeConversation aún existe en AntigravityApp.js"; exit 1;
fi
echo "PASÓ"

# 7. AC-CLN-006: ChatCanvas.js Hero de Bienvenida Google Material 3
echo -n "[AC-CLN-006] ChatCanvas.js: Hero de Bienvenida Oficial y fade-out... "
grep -q "renderWelcomeHero" "$CANVAS_FILE" || { echo "FALLO: renderWelcomeHero ausente en ChatCanvas.js"; exit 1; }
grep -q "dismissWelcomeHero" "$CANVAS_FILE" || { echo "FALLO: dismissWelcomeHero ausente en ChatCanvas.js"; exit 1; }
grep -q "¿En qué puedo ayudarte hoy?" "$CANVAS_FILE" || { echo "FALLO: Lema del Hero ausente"; exit 1; }
grep -q "ag-welcome-hero" "$CANVAS_FILE" || { echo "FALLO: Clase ag-welcome-hero ausente"; exit 1; }
echo "PASÓ"

# 8. AC-CLN-007: AgentBridge.js Comprobación de Terminal.isInstalled()
echo -n "[AC-CLN-007] AgentBridge.js: Verificación de Terminal.isInstalled()... "
grep -q "Terminal.isInstalled" "$BRIDGE_FILE" || { echo "FALLO: Verificación Terminal.isInstalled ausente en AgentBridge.js"; exit 1; }
echo "PASÓ"

# 9. AC-CLN-008: AgentBridge.js & ChatCanvas.js Auto-aprovisionamiento y tarjeta SETUP
echo -n "[AC-CLN-008] AgentBridge & ChatCanvas: Tarjeta de instalación y estado SETUP... "
grep -q "SETUP" "$BRIDGE_FILE" || { echo "FALLO: Estado SETUP ausente en AgentBridge.js"; exit 1; }
grep -q "installRuntime" "$BRIDGE_FILE" || { echo "FALLO: Método installRuntime ausente en AgentBridge.js"; exit 1; }
grep -q "renderSetupCard" "$CANVAS_FILE" || { echo "FALLO: renderSetupCard ausente en ChatCanvas.js"; exit 1; }
grep -q "Inicializar Entorno Agéntico" "$CANVAS_FILE" || { echo "FALLO: Botón de setup ausente en ChatCanvas.js"; exit 1; }
echo "PASÓ"

# 10. AC-CLN-009: AgentBridge.js Conexión nativa y estado READY determinista
echo -n "[AC-CLN-009] AgentBridge.js: Conexión determinista a READY... "
grep -q "READY" "$BRIDGE_FILE" || { echo "FALLO: Estado READY ausente en AgentBridge.js"; exit 1; }
grep -q "launchAgy" "$BRIDGE_FILE" || { echo "FALLO: launchAgy ausente en AgentBridge.js"; exit 1; }
echo "PASÓ"

# 11. AC-CLN-010: AgentBridge.js Cola resiliente de prompts
echo -n "[AC-CLN-010] AgentBridge.js: Cola resiliente de prompts (pendingQueue)... "
grep -q "this.pendingQueue = \[\]" "$BRIDGE_FILE" || { echo "FALLO: pendingQueue no inicializada en AgentBridge.js"; exit 1; }
grep -q "flushPendingQueue" "$BRIDGE_FILE" || { echo "FALLO: flushPendingQueue ausente en AgentBridge.js"; exit 1; }
grep -q "promptQueued" "$BRIDGE_FILE" || { echo "FALLO: Evento promptQueued ausente en AgentBridge.js"; exit 1; }
grep -q "promptDispatched" "$BRIDGE_FILE" || { echo "FALLO: Evento promptDispatched ausente en AgentBridge.js"; exit 1; }
echo "PASÓ"

# 12. Version Bump v2.0.2+ y Email Institucional
echo -n "[AC-CLN-VER] Config & Package: Versión 2.0.x y soporte institucional... "
grep -qE 'version="2\.0\.[23]"' "$CONFIG_FILE" || { echo "FALLO: Versión 2.0.x ausente en config.xml"; exit 1; }
grep -qE 'android-versionCode="2000[23]"' "$CONFIG_FILE" || { echo "FALLO: versionCode 2000x ausente en config.xml"; exit 1; }
grep -q 'support@antigravity.google' "$CONFIG_FILE" || { echo "FALLO: Correo institucional ausente en config.xml"; exit 1; }
grep -qE '"version": "2\.0\.[23]"' "$PACKAGE_FILE" || { echo "FALLO: Versión 2.0.x ausente en package.json"; exit 1; }
echo "PASÓ"

echo "================================================================================"
echo "  TODOS LOS CRITERIOS DE ACEPTACIÓN (AC-CLN-001 a AC-CLN-010) CERTIFICADOS EXITOSAMENTE"
echo "================================================================================"
