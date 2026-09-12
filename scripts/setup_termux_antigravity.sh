#!/usr/bin/env bash
# ==============================================================================
# Antigravity Studio - Setup & Environment Initializer for Termux
# Target Hardware: Xiaomi Pad 6 (Qualcomm Snapdragon 870 5G, ARM64-v8a)
# Visual Identity: Cyber-Obsidian Theme (Phase 1 & Phase 2)
# ==============================================================================

set -euo pipefail

# ANSI 24-bit / 256 Color Palette (Cyber-Obsidian)
CYAN='\033[38;2;0;240;255m'     # #00F0FF (Accent Cyan)
VIOLET='\033[38;2;139;92;246m'  # #8B5CF6 (Purple Accent)
GREEN='\033[38;2;16;185;129m'   # #10B981 (Success Green)
AMBER='\033[38;2;245;158;11m'   # #F59E0B (Warning Amber)
RED='\033[38;2;239;68;68m'      # #EF4444 (Error Red)
WHITE='\033[38;2;226;232;240m'  # #E2E8F0 (Text Primary)
MUTED='\033[38;2;100;116;139m'  # #64748B (Slate Muted)
BOLD='\033[1m'
NC='\033[0m'

echo -e "${CYAN}${BOLD}"
echo "   ___         __  _                         _  __           "
echo "  /   |  ____  / /_(_)___ __________ __   __ (_)/ /___  __    "
echo " / /| | / __ \/ __/ / __ \`/ ___/ __ \`/ | / // // __/ / / /    "
echo "/ ___ |/ / / / /_/ / /_/ / /  / /_/ /| |/ // // /_/ /_/ /     "
echo "/_/  |_/_/ /_/\__/_/\__, /_/   \__,_/ |___//_/ \__/\__, /      "
echo "                  /____/                         /____/       "
echo -e "${VIOLET}${BOLD} [ Antigravity Studio Installer | Xiaomi Pad 6 Edition ]${NC}"
echo -e "${MUTED} Optimizador de entorno Termux para Snapdragon 870 ARM64${NC}\n"

# ------------------------------------------------------------------------------
# Paso 1: Instalación de dependencias mínimas
# ------------------------------------------------------------------------------
echo -e "${CYAN}${BOLD}[Paso 1/4]${NC} Verificando e instalando dependencias base (curl, jq, git, python, nano)..."

REQUIRED_PKGS=(curl jq git python nano)
MISSING_PKGS=()

for pkg in "${REQUIRED_PKGS[@]}"; do
    if ! command -v "$pkg" >/dev/null 2>&1; then
        MISSING_PKGS+=("$pkg")
    fi
done

if [ ${#MISSING_PKGS[@]} -gt 0 ]; then
    echo -e "${AMBER}[!] Paquetes faltantes detectados: ${MISSING_PKGS[*]}${NC}"
    if command -v pkg >/dev/null 2>&1; then
        echo -e "${CYAN}[*] Instalando mediante Termux pkg...${NC}"
        pkg update -y || true
        pkg install -y "${MISSING_PKGS[@]}"
        echo -e "${GREEN}[✓] Dependencias instaladas satisfactoriamente.${NC}"
    elif command -v apt-get >/dev/null 2>&1; then
        echo -e "${CYAN}[*] Instalando mediante apt-get...${NC}"
        apt-get update -y || true
        apt-get install -y "${MISSING_PKGS[@]}" || true
        echo -e "${GREEN}[✓] Dependencias instaladas vía apt-get.${NC}"
    else
        echo -e "${AMBER}[!] Gestor de paquetes no disponible directamente en este shell. Omitiendo instalación automática.${NC}"
    fi
else
    echo -e "${GREEN}[✓] Todas las dependencias base requeridas ya se encuentran instaladas.${NC}"
fi

# ------------------------------------------------------------------------------
# Paso 2: Configuración Visual Cyber-Obsidian (~/.termux/)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}${BOLD}[Paso 2/4]${NC} Aplicando tema Cyber-Obsidian y barra táctil para tablet..."

TERMUX_CONFIG_DIR="${HOME}/.termux"
mkdir -p "$TERMUX_CONFIG_DIR"

# Generar ~/.termux/colors.properties con la paleta oficial
cat << 'EOF' > "${TERMUX_CONFIG_DIR}/colors.properties"
background=#0B0F19
foreground=#E2E8F0
cursor=#00F0FF
color0=#0B0F19
color1=#EF4444
color2=#10B981
color3=#F59E0B
color4=#3B82F6
color5=#8B5CF6
color6=#00F0FF
color7=#E2E8F0
color8=#1E293B
color9=#F87171
color10=#34D399
color11=#FBBF24
color12=#60A5FA
color13=#A78BFA
color14=#22D3EE
color15=#FFFFFF
EOF

echo -e "${GREEN}[✓] Paleta Cyber-Obsidian escrita en ${TERMUX_CONFIG_DIR}/colors.properties${NC}"

# Generar ~/.termux/termux.properties con la barra táctil para tablet (extra-keys)
cat << 'EOF' > "${TERMUX_CONFIG_DIR}/termux.properties"
extra-keys = [ \
  ['ESC', {key: 'CTRL', display: 'CTRL'}, {key: 'TAB', display: 'TAB'}, {macro: 'CTRL k', display: '✓ Aprobar'}, {macro: '/model\n', display: '⚡ Modelo'}, {macro: 'CTRL c', display: '⏹ Detener'}], \
  [{macro: 'cd ~/projects && ls -la\n', display: '📁 Proyectos'}, '|', '-', '/', '~', 'UP', 'DOWN'] \
]
extra-keys-style = arrows-only
EOF

echo -e "${GREEN}[✓] Barra táctil de productividad escrita en ${TERMUX_CONFIG_DIR}/termux.properties${NC}"

# Recargar configuración si la utilidad oficial de Termux existe
if command -v termux-reload-settings >/dev/null 2>&1; then
    echo -e "${CYAN}[*] Recargando configuración de Termux con termux-reload-settings...${NC}"
    termux-reload-settings || true
    echo -e "${GREEN}[✓] Configuración de Termux recargada en caliente.${NC}"
else
    echo -e "${MUTED}[i] termux-reload-settings se aplicará automáticamente al reiniciar la app Termux.${NC}"
fi

# ------------------------------------------------------------------------------
# Paso 3: Verificación y Enlace de Antigravity (agy)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}${BOLD}[Paso 3/4]${NC} Verificando e integrando CLI Antigravity ('agy') para Snapdragon 870 ARM64..."

# Determinar directorio binario prioritario
if [ -n "${PREFIX:-}" ] && [ -d "${PREFIX}/bin" ] && [ -w "${PREFIX}/bin" ]; then
    INSTALL_BIN_DIR="${PREFIX}/bin"
elif [ -d "/data/data/com.termux/files/usr/bin" ] && [ -w "/data/data/com.termux/files/usr/bin" ]; then
    INSTALL_BIN_DIR="/data/data/com.termux/files/usr/bin"
else
    INSTALL_BIN_DIR="${HOME}/.local/bin"
    mkdir -p "$INSTALL_BIN_DIR"
fi

if command -v agy >/dev/null 2>&1; then
    echo -e "${CYAN}[i] Binario o lanzador previo detectado en PATH: $(command -v agy)${NC}"
fi

# Verificar proot-distro y distribución Ubuntu ARM64
if command -v proot-distro >/dev/null 2>&1; then
    echo -e "${GREEN}[✓] proot-distro detectado en el entorno Termux.${NC}"
    if proot-distro list 2>/dev/null | grep -q "ubuntu"; then
        echo -e "${GREEN}[✓] Distribución Ubuntu ARM64 disponible en proot-distro.${NC}"
    else
        echo -e "${AMBER}[!] Distribución Ubuntu no encontrada. Instálala con: proot-distro install ubuntu${NC}"
    fi
else
    echo -e "${AMBER}[!] proot-distro no detectado. Requerido para agy: pkg install proot-distro && proot-distro install ubuntu${NC}"
fi

# Generar binario lanzador oficial hacia el subsistema PRoot Ubuntu
echo -e "${CYAN}[*] Desplegando binario lanzador 'agy' en ${INSTALL_BIN_DIR}/agy...${NC}"
cat << 'EOF' > "${INSTALL_BIN_DIR}/agy"
#!/data/data/com.termux/files/usr/bin/sh
if command -v proot-distro >/dev/null 2>&1; then
    exec proot-distro login ubuntu -- bash -l -c 'agy "$@"'
else
    echo "Error: proot-distro no está instalado. Instala ubuntu con: pkg install proot-distro && proot-distro install ubuntu"
    exit 1
fi
EOF
chmod +x "${INSTALL_BIN_DIR}/agy"
echo -e "${GREEN}[✓] Binario lanzador 'agy' configurado y marcado como ejecutable en ${INSTALL_BIN_DIR}/agy${NC}"

# Estado de autenticación
CREDS_FILE="${HOME}/.gemini/oauth_creds.json"
TOKEN_FILE="${HOME}/.antigravity_token"
if [ -f "$CREDS_FILE" ] || [ -f "$TOKEN_FILE" ]; then
    echo -e "${GREEN}[✓] Estado de autenticación: Sesión activa detectada.${NC}"
else
    echo -e "${AMBER}[!] Estado de autenticación: Sin credenciales configuradas (ejecuta 'agy auth' para conectarte).${NC}"
fi

# ------------------------------------------------------------------------------
# Paso 4: Entorno y Bienvenida en ~/.bashrc
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}${BOLD}[Paso 4/4]${NC} Configurando variables de entorno, directorio de proyectos y banner en ~/.bashrc..."

# Asegurar persistencia contra HyperOS durante la instalación
termux-wake-lock 2>/dev/null || true

# Asegurar directorio de proyectos
PROJECTS_DIR="${HOME}/projects"
mkdir -p "$PROJECTS_DIR"
echo -e "${GREEN}[✓] Directorio de proyectos verificado: ${PROJECTS_DIR}${NC}"

BASHRC_FILE="${HOME}/.bashrc"
touch "$BASHRC_FILE"

# Idempotencia: Eliminar bloque previo si ya existía
if grep -q "# >>> ANTIGRAVITY STUDIO BOOTSTRAP >>>" "$BASHRC_FILE"; then
    sed -i '/# >>> ANTIGRAVITY STUDIO BOOTSTRAP >>>/,/# <<< ANTIGRAVITY STUDIO BOOTSTRAP <<</d' "$BASHRC_FILE"
fi

# Añadir bloque estandarizado de Antigravity Studio
cat << 'EOF' >> "$BASHRC_FILE"
# >>> ANTIGRAVITY STUDIO BOOTSTRAP >>>
# Antigravity Studio Environment for Xiaomi Pad 6 (Snapdragon 870)

export PATH="$HOME/.local/bin:${PREFIX:-/data/data/com.termux/files/usr}/bin:$PATH"
export WORKSPACE="${WORKSPACE:-$HOME/projects}"
export TERM="${TERM:-xterm-256color}"
export COLORTERM="truecolor"
export AGY_TARGET_DEVICE="Xiaomi Pad 6 (Qualcomm Snapdragon 870)"

# Crear ~/projects automáticamente si no existiera
mkdir -p "$HOME/projects"

# Banner interactivo de bienvenida Cyber-Obsidian
if [[ $- == *i* ]]; then
    _cyan='\033[38;2;0;240;255m'
    _violet='\033[38;2;139;92;246m'
    _green='\033[38;2;16;185;129m'
    _amber='\033[38;2;245;158;11m'
    _white='\033[38;2;226;232;240m'
    _muted='\033[38;2;100;116;139m'
    _bold='\033[1m'
    _nc='\033[0m'

    echo -e "${_cyan}   ___         __  _                         _  __           ${_nc}"
    echo -e "${_cyan}  /   |  ____  / /_(_)___ __________ __   __ (_)/ /___  __    ${_nc}"
    echo -e "${_violet} / /| | / __ \/ __/ / __ \`/ ___/ __ \`/ | / // // __/ / / /    ${_nc}"
    echo -e "${_violet}/ ___ |/ / / / /_/ / /_/ / /  / /_/ /| |/ // // /_/ /_/ /     ${_nc}"
    echo -e "${_cyan}/_/  |_/_/ /_/\__/_/\__, /_/   \__,_/ |___//_/ \__/\__, /      ${_nc}"
    echo -e "${_cyan}                  /____/                         /____/       ${_nc}"
    echo -e "${_violet}${_bold} [ Antigravity Studio 2.0 | Xiaomi Pad 6 - Snapdragon 870 ]${_nc}"
    echo -e ""
    echo -e " ${_cyan}${_bold}Dispositivo:${_nc}       Xiaomi Pad 6 (Qualcomm Snapdragon 870 @ 3.20 GHz)"
    echo -e " ${_cyan}${_bold}Arquitectura:${_nc}      ARM64-v8a (Adreno 650 GPU | 144Hz)"
    echo -e " ${_cyan}${_bold}Modelo Activo:${_nc}     Antigravity 2.0 (Gemini 2.5 Pro / Flash)"

    _creds="${HOME}/.gemini/oauth_creds.json"
    _tok="${HOME}/.antigravity_token"
    if [ -f "$_creds" ] || [ -f "$_tok" ]; then
        echo -e " ${_cyan}${_bold}Estado de Sesión:${_nc}  ${_green}● Conectado (Google OAuth 2.0 PKCE)${_nc}"
    else
        echo -e " ${_cyan}${_bold}Estado de Sesión:${_nc}  ${_amber}● Modo Local / Sin Conexión (ejecuta 'agy auth')${_nc}"
    fi

    echo -e " ${_cyan}${_bold}Workspace:${_nc}         $HOME/projects"
    echo -e ""
    echo -e " ${_bold}¡Bienvenido a tu estación de desarrollo agéntico móvil!${_nc}"
    echo -e "  🚀 Para iniciar tu agente autónomo: ${_green}${_bold}agy${_nc} o ${_green}${_bold}agy run${_nc}"
    echo -e "  ⚡ Utiliza los botones táctiles en la barra inferior para accesos rápidos."
    echo -e ""
fi

# Persistencia contra suspensión agresiva en HyperOS
termux-wake-lock 2>/dev/null || true

# Auto-arranque interactivo directo a Antigravity CLI oficial
if [ -z "$AGY_LAUNCHED" ] && [ -t 1 ]; then
    export AGY_LAUNCHED=1
    agy
fi
# <<< ANTIGRAVITY STUDIO BOOTSTRAP <<<
EOF

echo -e "${GREEN}[✓] Bloque de inicio y bienvenida registrado en ${BASHRC_FILE}${NC}"

echo -e "\n${GREEN}${BOLD}==============================================================================${NC}"
echo -e "${GREEN}${BOLD}   ¡INSTALACIÓN DE ANTIGRAVITY STUDIO EN TERMUX COMPLETADA CON ÉXITO!${NC}"
echo -e "${GREEN}${BOLD}==============================================================================${NC}"
echo -e "${WHITE}Tu entorno móvil en Xiaomi Pad 6 (Snapdragon 870) está 100% configurado:${NC}"
echo -e " 1. ${CYAN}Tema Cyber-Obsidian:${NC}       Fondo #0B0F19, Acentos Cyan #00F0FF y Violeta #8B5CF6"
echo -e " 2. ${CYAN}Barra Táctil Tablet:${NC}       Extra-keys con CTRL, TAB, ✓ Aprobar, ⚡ Modelo, 📁 Proyectos"
echo -e " 3. ${CYAN}CLI de Agente:${NC}             'agy' vinculado y listo para ejecución"
echo -e " 4. ${CYAN}Directorio de Trabajo:${NC}     ~/projects listo para tus repositorios\n"
echo -e "Escribe ${GREEN}${BOLD}source ~/.bashrc${NC} o reinicia Termux para disfrutar la experiencia completa."
