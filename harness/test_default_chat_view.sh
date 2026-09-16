#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-055 Vista de Chat Nativa por Defecto y Persistencia de Modo
# Formato: TAP v12 (Test Anything Protocol) / POSIX Shell
# ==============================================================================
set -e

SPEC_FILE_055="specs/55-default-chat-view-and-persistence.md"
CLEAN_TERM_JS="nova-src/src/antigravity2/CleanAgentTerminal.js"
TEST_FILE="nova-src/tests/unit/viewModePersistence.test.js"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"

TOTAL_TESTS=10
CURRENT_TEST=1

tap_ok() {
    local desc="$1"
    echo "ok $CURRENT_TEST - $desc"
    CURRENT_TEST=$((CURRENT_TEST + 1))
}

tap_fail() {
    local desc="$1"
    echo "not ok $CURRENT_TEST - $desc"
    exit 1
}

echo "TAP version 13"
echo "1..$TOTAL_TESTS"

# ------------------------------------------------------------------------------
# 1. Existencia del documento de especificación SPEC-055
# ------------------------------------------------------------------------------
if [ -f "$SPEC_FILE_055" ]; then
    tap_ok "Documento SPEC-055 presente"
else
    tap_fail "Falta documento $SPEC_FILE_055"
fi

# ------------------------------------------------------------------------------
# 2. Exportación de constantes de persistencia (AC-PERSIST-001)
# ------------------------------------------------------------------------------
if grep -q 'export const VIEW_MODE_STORAGE_KEY = "antigravity_view_mode";' "$CLEAN_TERM_JS" && \
   grep -q 'export const VIEW_MODE_CHAT = "chat";' "$CLEAN_TERM_JS" && \
   grep -q 'export const VIEW_MODE_TERMINAL = "terminal";' "$CLEAN_TERM_JS"; then
    tap_ok "Constantes VIEW_MODE_STORAGE_KEY, VIEW_MODE_CHAT y VIEW_MODE_TERMINAL exportadas"
else
    tap_fail "Faltan constantes de modo de pantalla en $CLEAN_TERM_JS"
fi

# ------------------------------------------------------------------------------
# 3. Exportación de resolveInitialViewMode pura (AC-PERSIST-001)
# ------------------------------------------------------------------------------
if grep -q 'export function resolveInitialViewMode' "$CLEAN_TERM_JS"; then
    tap_ok "Función resolveInitialViewMode exportada"
else
    tap_fail "Falta export function resolveInitialViewMode en $CLEAN_TERM_JS"
fi

# ------------------------------------------------------------------------------
# 4. Métodos de conmutación switchToChatView y switchToTerminalView
# ------------------------------------------------------------------------------
if grep -q 'switchToChatView()' "$CLEAN_TERM_JS" && \
   grep -q 'switchToTerminalView()' "$CLEAN_TERM_JS"; then
    tap_ok "Métodos switchToChatView y switchToTerminalView implementados"
else
    tap_fail "Faltan métodos switchToChatView o switchToTerminalView en $CLEAN_TERM_JS"
fi

# ------------------------------------------------------------------------------
# 5. Presencia y soporte de selectores #chat-view-btn y #top-bar-to-chat (AC-TOGGLE-002)
# ------------------------------------------------------------------------------
if grep -q 'chat-view-btn' "$CLEAN_TERM_JS" && \
   grep -q 'top-bar-to-chat' "$CLEAN_TERM_JS"; then
    tap_ok "Soporte unificado de selectores #chat-view-btn y #top-bar-to-chat verificado"
else
    tap_fail "Falta selector #chat-view-btn o retrocompatible #top-bar-to-chat en $CLEAN_TERM_JS"
fi

# ------------------------------------------------------------------------------
# 6. Cero emojis en archivos fuente de producción y test (AC-UIX-001)
# ------------------------------------------------------------------------------
EMOJI_DETECTED=0
for f in "$CLEAN_TERM_JS" "$TEST_FILE"; do
    if LC_ALL=C grep -qP '\xF0\x9F[\x8C-\xAB]|\xE2[\x98-\x9E]|\xEF\xB8\x8F' "$f"; then
        EMOJI_DETECTED=1
        echo "# Emoji encontrado en $f"
    fi
done

if [ "$EMOJI_DETECTED" -eq 0 ]; then
    tap_ok "Cero emojis en archivos modificados (AC-UIX-001)"
else
    tap_fail "Se detectaron emojis prohibidos"
fi

# ------------------------------------------------------------------------------
# 7. Verificación de versión v2.9.0 y versionCode 20900 (AC-BUILD-001)
# ------------------------------------------------------------------------------
if grep -q 'version="2.9.0"' "$CONFIG_XML" && \
   grep -q 'android-versionCode="20900"' "$CONFIG_XML" && \
   grep -q '"version": "2.9.0"' "$PACKAGE_JSON"; then
    tap_ok "Versión 2.9.0 y versionCode 20900 verificados (AC-BUILD-001)"
else
    tap_fail "Inconsistencia de versión en config.xml o package.json"
fi

# ------------------------------------------------------------------------------
# 8. Compuerta de Aserciones de Comportamiento Vitest (AC-STARTUP / AC-TOGGLE / AC-PERSIST)
# ------------------------------------------------------------------------------
if ( cd nova-src && npx vitest run tests/unit/viewModePersistence.test.js --reporter=dot > /dev/null ); then
    tap_ok "Aserciones de comportamiento Vitest superadas (12/12)"
else
    tap_fail "Fallaron las aserciones de comportamiento en viewModePersistence.test.js"
fi

# ------------------------------------------------------------------------------
# 9. Anti-regresión: SPEC-054 Interfaz Nativa de Agente (test_agent_stream_client.sh)
# ------------------------------------------------------------------------------
if bash harness/test_agent_stream_client.sh > /dev/null; then
    tap_ok "Anti-regresión: Suite SPEC-054 superada al 100%"
else
    tap_fail "Regresión detectada en harness/test_agent_stream_client.sh"
fi

# ------------------------------------------------------------------------------
# 10. Anti-regresión: Suite de Terminal Clásica (test_clean_agent_terminal.sh)
# ------------------------------------------------------------------------------
if bash harness/test_clean_agent_terminal.sh > /dev/null; then
    tap_ok "Anti-regresión: Suite de Terminal Clásica superada al 100%"
else
    tap_fail "Regresión detectada en harness/test_clean_agent_terminal.sh"
fi

echo "# TODAS LAS COMPUERTAS DE SPEC-055 HAN SIDO SUPERADAS EXITOSAMENTE"
