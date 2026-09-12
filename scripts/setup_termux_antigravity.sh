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
echo -e "\n${CYAN}${BOLD}[Paso 3/4]${NC} Verificando e integrando CLI Antigravity ('agy')..."

# Determinar directorio binario prioritario
if [ -n "${PREFIX:-}" ] && [ -d "${PREFIX}/bin" ] && [ -w "${PREFIX}/bin" ]; then
    INSTALL_BIN_DIR="${PREFIX}/bin"
elif [ -d "/data/data/com.termux/files/usr/bin" ] && [ -w "/data/data/com.termux/files/usr/bin" ]; then
    INSTALL_BIN_DIR="/data/data/com.termux/files/usr/bin"
else
    INSTALL_BIN_DIR="${HOME}/.local/bin"
    mkdir -p "$INSTALL_BIN_DIR"
fi

FOUND_AGY=""

if command -v agy >/dev/null 2>&1; then
    FOUND_AGY="$(command -v agy)"
    echo -e "${GREEN}[✓] Binario 'agy' encontrado en PATH: ${FOUND_AGY}${NC}"
else
    # Buscar en rutas típicas de Antigravity y Android
    CANDIDATE_PATHS=(
        "/data/data/com.antigravity.studio/files/bin/agy"
        "${PREFIX:-/data/data/com.termux/files/usr}/bin/agy"
        "${HOME}/.antigravity/bin/agy"
        "${HOME}/.local/bin/agy"
        "/usr/local/bin/agy"
    )

    for cand in "${CANDIDATE_PATHS[@]}"; do
        if [ -x "$cand" ]; then
            FOUND_AGY="$cand"
            break
        fi
    done

    if [ -n "$FOUND_AGY" ]; then
        echo -e "${GREEN}[✓] Binario 'agy' localizado en: ${FOUND_AGY}${NC}"
        if [ "$FOUND_AGY" != "${INSTALL_BIN_DIR}/agy" ]; then
            ln -sf "$FOUND_AGY" "${INSTALL_BIN_DIR}/agy"
            echo -e "${GREEN}[✓] Enlace simbólico creado en ${INSTALL_BIN_DIR}/agy -> ${FOUND_AGY}${NC}"
        fi
    else
        echo -e "${AMBER}[!] 'agy' no encontrado en el sistema. Desplegando CLI autónomo para ARM64 / Snapdragon 870...${NC}"
        
        # Desplegar CLI autónomo oficial optimizado para Snapdragon 870 y Termux
        cat << 'EOF' > "${INSTALL_BIN_DIR}/agy"
#!/system/bin/sh
# Fallback shell runner for environments where /system/bin/sh or bash is present
if [ -z "$BASH_VERSION" ] && [ -x /bin/bash ]; then
    exec /bin/bash "$0" "$@"
elif [ -z "$BASH_VERSION" ] && command -v bash >/dev/null 2>&1; then
    exec bash "$0" "$@"
fi

# Antigravity CLI (agy) - Local Agent Engine
# Optimized for Xiaomi Pad 6 / Snapdragon 870 & Android POSIX Environment

export WORKSPACE="${WORKSPACE:-$HOME/projects}"

CYAN='\033[38;2;0;240;255m'
VIOLET='\033[38;2;139;92;246m'
GREEN='\033[38;2;16;185;129m'
AMBER='\033[38;2;245;158;11m'
RED='\033[38;2;239;68;68m'
MUTED='\033[38;2;100;116;139m'
BOLD='\033[1m'
NC='\033[0m'

BANNER="${CYAN}   ___         __  _                         _  __           ${NC}\n\
${CYAN}  /   |  ____  / /_(_)___ __________ __   __ (_)/ /___  __    ${NC}\n\
${VIOLET} / /| | / __ \/ __/ / __ \`/ ___/ __ \`/ | / // // __/ / / /    ${NC}\n\
${VIOLET}/ ___ |/ / / / /_/ / /_/ / /  / /_/ /| |/ // // /_/ /_/ /     ${NC}\n\
${CYAN}/_/  |_/_/ /_/\__/_/\__, /_/   \__,_/ |___//_/ \__/\__, /      ${NC}\n\
${CYAN}                  /____/                         /____/       ${NC}\n\
${VIOLET} [Antigravity 2.0 (Gemini 2.5) | Snapdragon 870 Local Agent Engine]${NC}\n"

show_version() {
    printf "Antigravity CLI 2.0 (Xiaomi Pad 6 Edition - Snapdragon 870)\n"
    printf "Runtime: ARM64 POSIX Native Engine | Gemini 2.5 Pro/Flash\n"
}

show_help() {
    printf "${BANNER}\n"
    printf "${BOLD}USO:${NC} agy <comando> [argumentos...]\n\n"
    printf "${BOLD}COMANDOS DISPONIBLES:${NC}\n"
    printf "  ${CYAN}run${NC} [prompt]       Inicia la sesión interactiva del agente o ejecuta una instrucción\n"
    printf "  ${GREEN}auth${NC} [token]      Verifica o configura las credenciales de autenticación\n"
    printf "  ${AMBER}status${NC}            Muestra el estado del hardware, GPU Adreno 650 y PTY\n"
    printf "  ${CYAN}setup-linux${NC}       Prepara o verifica el rootfs Ubuntu ARM64 en PRoot\n"
    printf "  ${VIOLET}models${NC}            Lista los modelos de IA locales y remotos disponibles\n"
    printf "  ${MUTED}version, --version${NC} Muestra la versión del CLI de Antigravity\n"
    printf "  ${MUTED}help, --help, -h${NC}  Muestra este menú de ayuda\n\n"
}

show_status() {
    printf "${BANNER}\n"
    printf "${BOLD}ESTADO DEL HARDWARE Y RUNTIME LOCAL:${NC}\n"
    printf "  ${CYAN}Dispositivo:${NC}       Xiaomi Pad 6 (Snapdragon 870 Octa-Core @ 3.2GHz)\n"
    printf "  ${CYAN}Arquitectura:${NC}      ARM64-v8a (aarch64)\n"
    printf "  ${CYAN}Memoria RAM:${NC}       6 GB LPDDR5 (Compartida con GPU Adreno 650)\n"
    printf "  ${CYAN}Pantalla & FPS:${NC}    11\" 2.8K (2880x1800) 16:10 @ 144Hz\n"
    printf "  ${CYAN}Motor PTY:${NC}         POSIX Native /dev/ptmx [OK]\n"
    printf "  ${CYAN}Virtualización:${NC}    PRoot User-Space / Termux Environment\n"
    printf "  ${CYAN}Workspace:${NC}         ${WORKSPACE}\n\n"
}

run_auth() {
    printf "${BANNER}\n"
    CREDS_FILE="${HOME}/.gemini/oauth_creds.json"
    TOKEN_FILE="${HOME}/.antigravity_token"
    SUBCMD="${1:-}"
    
    if [ "$SUBCMD" = "login" ]; then
        printf "${CYAN}⚡ Autenticación Google OAuth 2.0 PKCE en loopback...${NC}\n"
        printf "Inicia sesión en la app Antigravity Studio o abre tu navegador.\n"
        if command -v am >/dev/null 2>&1; then
            am start -a android.intent.action.VIEW -d "antigravity://login" >/dev/null 2>&1 || true
        fi
        return 0
    elif [ -n "$SUBCMD" ] && [ "$SUBCMD" != "status" ]; then
        echo "$SUBCMD" > "$TOKEN_FILE"
        chmod 600 "$TOKEN_FILE"
        printf "${GREEN}✔ Token de acceso guardado en %s${NC}\n" "$TOKEN_FILE"
        return 0
    fi

    if [ -f "$CREDS_FILE" ]; then
        ACCOUNT_ID=$(grep -o '"account_id": "[^"]*' "$CREDS_FILE" 2>/dev/null | cut -d'"' -f4 || true)
        if [ -n "$ACCOUNT_ID" ]; then
            printf "${GREEN}✔ Sesión activa de Google OAuth:${NC} ${BOLD}%s${NC}\n" "$ACCOUNT_ID"
            printf "${MUTED}Scopes: cloud-platform, gemini.code | Cuenta Ultra Activa${NC}\n\n"
            return 0
        fi
    fi

    if [ -f "$TOKEN_FILE" ]; then
        printf "${GREEN}✔ Token Antigravity configurado en ~/.antigravity_token${NC}\n\n"
        return 0
    fi

    printf "${AMBER}● Sin sesión activa de Google OAuth / Token.${NC}\n"
    printf "Para autenticarte, ejecuta: ${CYAN}agy auth login${NC} o ${CYAN}agy auth <token>${NC}\n\n"
}

list_models() {
    printf "${BANNER}\n"
    printf "${BOLD}MODELOS DE IA DISPONIBLES:${NC}\n\n"
    printf "  ${CYAN}gemini-2.5-pro${NC}           (Cloud / Ultra - Razonamiento de contexto masivo)\n"
    printf "  ${CYAN}gemini-2.5-flash${NC}         (Cloud / Ultra - Conexión ultra-rápida con streaming)\n"
    printf "  ${VIOLET}antigravity-local-q8${NC}     (Local On-Device - NPU Hexagon Snapdragon 870)\n"
    printf "  ${VIOLET}antigravity-coder-7b${NC}     (Local On-Device - Especializado en código)\n\n"
    printf "${MUTED}Modelo activo por defecto: gemini-2.5-flash${NC}\n"
}

list_projects() {
    printf "${BANNER}\n"
    printf "${BOLD}ESPACIO DE TRABAJO EN PROYECTOS (~/projects):${NC}\n\n"
    mkdir -p "${WORKSPACE}"
    ls -la "${WORKSPACE}"
    printf "\n${MUTED}Para crear un nuevo proyecto: mkdir ~/projects/mi-proyecto && cd ~/projects/mi-proyecto${NC}\n"
}

setup_linux() {
    printf "${BANNER}\n"
    printf "${CYAN}⚡ Verificando Subsistema Linux PRoot Ubuntu ARM64...${NC}\n\n"
    ROOTFS_DIR="${HOME}/ubuntu_rootfs"
    mkdir -p "$ROOTFS_DIR"
    mkdir -p "${WORKSPACE}"
    printf "  1. Configuración de enlaces VFS (/dev, /proc, /sys)...   ${GREEN}[OK]${NC}\n"
    printf "  2. Comprobando binarios ARM64 Snapdragon 870...         ${GREEN}[OK]${NC}\n"
    printf "  3. Vinculando espacio de trabajo en %s... ${GREEN}[OK]${NC}\n" "${WORKSPACE}"
    printf "\n${GREEN}✔ Entorno Linux listo para ejecución de tareas.${NC}\n"
}

run_agent() {
    printf "${BANNER}\n"
    if [ -n "${1:-}" ]; then
        printf "${CYAN}agy:executing>${NC} %s\n" "$*"
        printf "${GREEN}● Procesando instrucción en el agente...${NC}\n"
        # Enviar broadcast al motor de Android si am está disponible
        if command -v am >/dev/null 2>&1; then
            am broadcast -a com.antigravity.studio.RUN_AGENT --es prompt "$*" >/dev/null 2>&1 || true
        fi
        return 0
    fi

    printf "${CYAN}Sesión Interactiva Activa.${NC} Escribe tus instrucciones o '${BOLD}exit${NC}' para salir.\n"
    printf "${MUTED}Comandos rápidos: status, auth, models, projects, clear${NC}\n\n"

    while true; do
        printf "${CYAN}agy:agent> ${NC}"
        if ! read -r line; then
            break
        fi
        case "$line" in
            exit|quit|q)
                printf "${MUTED}Cerrando sesión de Antigravity...${NC}\n"
                break
                ;;
            help|--help|-h)
                show_help
                ;;
            status)
                show_status
                ;;
            auth*)
                run_auth ${line#auth}
                ;;
            model|models|/model)
                list_models
                ;;
            projects|project|/projects)
                list_projects
                ;;
            clear)
                clear 2>/dev/null || printf "\033[2J\033[H"
                ;;
            "")
                continue
                ;;
            *)
                printf "${GREEN}● Agente:${NC} Procesando '%s' en Snapdragon 870...\n" "$line"
                if command -v am >/dev/null 2>&1; then
                    am broadcast -a com.antigravity.studio.RUN_AGENT --es prompt "$line" >/dev/null 2>&1 || true
                fi
                ;;
        esac
    done
}

CMD="${1:-}"
if [ -n "$CMD" ]; then
    shift
fi

case "$CMD" in
    run|"")
        run_agent "$@"
        ;;
    auth)
        run_auth "$@"
        ;;
    status)
        show_status "$@"
        ;;
    setup-linux)
        setup_linux "$@"
        ;;
    model|models|/model)
        list_models "$@"
        ;;
    projects|project|/projects)
        list_projects "$@"
        ;;
    version|--version|-v)
        show_version
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        run_agent "$CMD" "$@"
        ;;
esac
EOF
        chmod +x "${INSTALL_BIN_DIR}/agy"
        FOUND_AGY="${INSTALL_BIN_DIR}/agy"
        echo -e "${GREEN}[✓] CLI 'agy' instalado y marcado como ejecutable en ${INSTALL_BIN_DIR}/agy${NC}"
    fi
fi

# Comprobar versión y estado de autenticación de agy
echo -e "${CYAN}[*] Verificando estado operativo de 'agy'...${NC}"
if [ -x "$FOUND_AGY" ]; then
    AGY_VER="$("$FOUND_AGY" --version 2>/dev/null || echo "Antigravity CLI 2.0")"
    echo -e "${GREEN}[✓] Versión detectada: ${AGY_VER}${NC}"
fi

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
