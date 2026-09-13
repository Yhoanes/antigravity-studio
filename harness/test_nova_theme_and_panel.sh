#!/bin/bash
# Test Harness - Verification for TASK-030: Nova Theme and Agent Panel Integration
set -e

echo "=== INICIANDO VERIFICACIÓN DE TEMA NOVA Y PANEL DE AGENTE (TASK-030) ==="

# 1. Verificar tema oficial 'Nova' en preInstalled.js
echo -n "1. Verificando tema oficial 'Nova' en preInstalled.js... "
THEME_FILE="nova-src/src/theme/preInstalled.js"
if [ ! -f "$THEME_FILE" ]; then
    echo "FALLO: $THEME_FILE no existe."
    exit 1
fi
grep -q 'createBuiltInTheme("Nova", "dark", "free")' "$THEME_FILE" || { echo "FALLO: createBuiltInTheme('Nova') ausente"; exit 1; }
grep -q 'nova.primaryColor = "rgb(9, 13, 22)"' "$THEME_FILE" || { echo "FALLO: primaryColor #090d16 ausente"; exit 1; }
grep -q 'nova.darkenedPrimaryColor = "rgb(3, 7, 18)"' "$THEME_FILE" || { echo "FALLO: darkenedPrimaryColor #030712 ausente"; exit 1; }
grep -q 'nova.secondaryColor = "rgb(17, 24, 39)"' "$THEME_FILE" || { echo "FALLO: secondaryColor #111827 ausente"; exit 1; }
grep -q 'nova.activeColor = "rgb(0, 240, 255)"' "$THEME_FILE" || { echo "FALLO: activeColor #00f0ff ausente"; exit 1; }
grep -q 'nova.linkTextColor = "rgb(139, 92, 246)"' "$THEME_FILE" || { echo "FALLO: linkTextColor #8b5cf6 ausente"; exit 1; }
grep -q 'nova.primaryTextColor = "rgb(226, 232, 240)"' "$THEME_FILE" || { echo "FALLO: primaryTextColor #e2e8f0 ausente"; exit 1; }
grep -A 3 "export default \[" "$THEME_FILE" | grep -q "nova" || { echo "FALLO: 'nova' no está exportado en export default"; exit 1; }
echo "[OK]"

# 2. Verificar que 'nova' es el appTheme predeterminado en settings.js
echo -n "2. Verificando appTheme predeterminado 'nova' en settings.js... "
SETTINGS_FILE="nova-src/src/lib/settings.js"
grep -q 'appTheme: "nova"' "$SETTINGS_FILE" || { echo "FALLO: appTheme: 'nova' ausente en defaultSettings"; exit 1; }
grep -q 'this.#defaultSettings.appTheme = "nova"' "$SETTINGS_FILE" || { echo "FALLO: appTheme = 'nova' ausente en init()"; exit 1; }
echo "[OK]"

# 3. Verificar branding soberano en main.js
echo -n "3. Verificando branding soberano '‹ ✦ › Nova IDE' en cabecera principal... "
MAIN_FILE="nova-src/src/main.js"
grep -q 'text: "‹ ✦ › Nova IDE"' "$MAIN_FILE" || { echo "FALLO: text: '‹ ✦ › Nova IDE' ausente en $MAIN_FILE"; exit 1; }
echo "[OK]"

# 4. Verificar botón de alternancia del agente en la barra superior
echo -n "4. Verificando botón de alternancia del agente en cabecera... "
grep -q 'id="agent-toggler"' "$MAIN_FILE" || { echo "FALLO: #agent-toggler ausente en $MAIN_FILE"; exit 1; }
grep -q 'attr-action="toggle-agent"' "$MAIN_FILE" || { echo "FALLO: attr-action='toggle-agent' ausente"; exit 1; }
grep -q 'title="Nova Agent (Ask)"' "$MAIN_FILE" || { echo "FALLO: title='Nova Agent (Ask)' ausente"; exit 1; }
echo "[OK]"

# 5. Verificar comando toggle-agent en commands.js
echo -n "5. Verificando comando 'toggle-agent' en commands.js... "
COMMANDS_FILE="nova-src/src/lib/commands.js"
grep -q '"toggle-agent"()' "$COMMANDS_FILE" || { echo "FALLO: toggle-agent() ausente en $COMMANDS_FILE"; exit 1; }
grep -q 'agentPanel.toggle()' "$COMMANDS_FILE" || { echo "FALLO: agentPanel.toggle() ausente en $COMMANDS_FILE"; exit 1; }
echo "[OK]"

# 6. Verificar estructura de componente agentPanel
echo -n "6. Verificando componente NovaAgentPanel (index.js y style.scss)... "
AGENT_INDEX="nova-src/src/components/agentPanel/index.js"
AGENT_STYLE="nova-src/src/components/agentPanel/style.scss"
if [ ! -f "$AGENT_INDEX" ] || [ ! -f "$AGENT_STYLE" ]; then
    echo "FALLO: Componente agentPanel incompleto."
    exit 1
fi
grep -q 'id="nova-agent-panel"' "$AGENT_INDEX" || { echo "FALLO: #nova-agent-panel ausente"; exit 1; }
grep -q 'agent-panel-header' "$AGENT_INDEX" || { echo "FALLO: agent-panel-header ausente"; exit 1; }
grep -q 'agent-status-badge' "$AGENT_INDEX" || { echo "FALLO: agent-status-badge ausente"; exit 1; }
grep -q 'id="agent-maximize-btn"' "$AGENT_INDEX" || { echo "FALLO: #agent-maximize-btn ausente"; exit 1; }
grep -q '⛶' "$AGENT_INDEX" || { echo "FALLO: Isotipo de maximizar ⛶ ausente"; exit 1; }
grep -q 'id="agent-terminal-container"' "$AGENT_INDEX" || { echo "FALLO: #agent-terminal-container ausente"; exit 1; }
grep -q '#090d16' "$AGENT_STYLE" || { echo "FALLO: #090d16 ausente en estilos del agente"; exit 1; }
grep -q '#00f0ff' "$AGENT_STYLE" || { echo "FALLO: #00f0ff ausente en estilos del agente"; exit 1; }
grep -q '#030712' "$AGENT_STYLE" || { echo "FALLO: #030712 (Obsidian Terminal) ausente en estilos"; exit 1; }
echo "[OK]"

# 7. Verificar montaje y exposición en main.js
echo -n "7. Verificando montaje de agentPanel en main.js... "
grep -q 'import agentPanel from "components/agentPanel"' "$MAIN_FILE" || { echo "FALLO: import agentPanel ausente en $MAIN_FILE"; exit 1; }
grep -q 'app.append(agentPanel.el)' "$MAIN_FILE" || { echo "FALLO: app.append(agentPanel.el) ausente en $MAIN_FILE"; exit 1; }
grep -q 'window.agentPanel = agentPanel' "$MAIN_FILE" || { echo "FALLO: window.agentPanel ausente en $MAIN_FILE"; exit 1; }
echo "[OK]"

# 8. Verificar estilos wideScreen para tablet 3-column layout
echo -n "8. Verificando reglas de layout 3 columnas en wideScreen.scss... "
WIDE_FILE="nova-src/src/styles/wideScreen.scss"
grep -q '#nova-agent-panel' "$WIDE_FILE" || { echo "FALLO: #nova-agent-panel ausente en $WIDE_FILE"; exit 1; }
grep -q 'clamp(340px, 30vw, 420px)' "$WIDE_FILE" || { echo "FALLO: clamp(340px, 30vw, 420px) ausente en $WIDE_FILE"; exit 1; }
echo "[OK]"

# 9. Verificar tema de terminal 'nova' en terminalThemeManager.js
echo -n "9. Verificando tema de terminal 'nova' en terminalThemeManager.js... "
TERM_THEME_FILE="nova-src/src/components/terminal/terminalThemeManager.js"
grep -q 'nova: {' "$TERM_THEME_FILE" || { echo "FALLO: tema 'nova' ausente en terminalThemeManager.js"; exit 1; }
grep -q 'cursor: "#00f0ff"' "$TERM_THEME_FILE" || { echo "FALLO: cursor #00f0ff ausente en terminal theme nova"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS DE VERIFICACIÓN (TASK-030) HAN PASADO EXITOSAMENTE ==="
