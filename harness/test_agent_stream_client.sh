#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-054 Interfaz Nativa de Agente sobre `stream-json`
#
# A diferencia de test_clean_agent_terminal.sh, que comprueba PRESENCIA de codigo
# mediante grep, esta suite exige ASERCIONES DE COMPORTAMIENTO. Es la respuesta al
# fallo de SPEC-051: 77/77 compuertas en verde con dos de tres mejoras rotas en
# dispositivo, porque los strings existian.
#
# Los criterios AC-STREAM-* y AC-VIEW-* se verifican ejecutando vitest contra el
# corpus real capturado en harness/fixtures/. Las compuertas estaticas de este
# archivo se limitan a lo que un test no puede cubrir: existencia de artefactos,
# ausencia de emojis y versionado.
# ==============================================================================
set -e

SPEC_FILE_054="specs/54-native-agent-ui-over-stream-json.md"
FIXTURES_DIR="harness/fixtures"
STREAM_CLIENT_JS="nova-src/src/antigravity2/AgentStreamClient.js"
SESSION_JS="nova-src/src/antigravity2/AgentSession.js"
CHAT_VIEW_JS="nova-src/src/antigravity2/AgentChatView.js"
CHAT_SCSS="nova-src/src/antigravity2/agent-chat.scss"
CLEAN_TERM_JS="nova-src/src/antigravity2/CleanAgentTerminal.js"
CONFIG_XML="nova-src/config.xml"
PACKAGE_JSON="nova-src/package.json"

echo "=== INICIANDO VALIDACION FORMAL DE SPEC-054 (INTERFAZ NATIVA SOBRE stream-json) ==="

# ------------------------------------------------------------------------------
# 1-3. Artefactos y corpus
# ------------------------------------------------------------------------------
echo -n "1. Verificando documento SPEC-054... "
[ -f "$SPEC_FILE_054" ] || { echo "FALLO: No existe $SPEC_FILE_054"; exit 1; }
echo "[OK]"

echo -n "2. Verificando corpus normativo de fixtures... "
[ -d "$FIXTURES_DIR" ] || { echo "FALLO: No existe $FIXTURES_DIR"; exit 1; }
for f in agy-print-stream-json.ndjson agy-print-stream-json-tools.ndjson agy-print-json.json agy-help-1.2.4.txt README.md; do
    [ -f "$FIXTURES_DIR/$f" ] || { echo "FALLO: Falta fixture $f"; exit 1; }
done
echo "[OK]"

echo -n "3. Verificando que cada linea del corpus NDJSON es JSON valido... "
python - <<'PYEOF' || { echo "FALLO: Corpus con lineas invalidas"; exit 1; }
import glob, io, json, sys
bad = 0
for p in sorted(glob.glob("harness/fixtures/*.ndjson")):
    for i, line in enumerate(io.open(p, encoding="utf-8"), 1):
        if not line.strip():
            continue
        try:
            json.loads(line)
        except Exception as e:
            print("  %s:%d %s" % (p, i, e))
            bad += 1
sys.exit(1 if bad else 0)
PYEOF
echo "[OK]"

# ------------------------------------------------------------------------------
# 4-6. Modulos nuevos
# ------------------------------------------------------------------------------
echo -n "4. Verificando modulos de SPEC-054... "
for f in "$STREAM_CLIENT_JS" "$SESSION_JS" "$CHAT_VIEW_JS" "$CHAT_SCSS"; do
    [ -f "$f" ] || { echo "FALLO: No existe $f"; exit 1; }
done
echo "[OK]"

echo -n "5. Verificando AC-VIEW-005 (cero Xterm.js en la vista nativa)... "
# Busca IMPORTS y USO real, no la palabra en prosa: los propios comentarios de
# estos modulos mencionan "Xterm.js" precisamente para declarar su ausencia.
XTERM_USE='(from|require\()\s*.?@xterm|new[[:space:]]+Xterm|Xterm\(|xterm/css'
for f in "$CHAT_VIEW_JS" "$STREAM_CLIENT_JS" "$SESSION_JS"; do
    if grep -qE "$XTERM_USE" "$f"; then
        echo "FALLO: $f importa o instancia Xterm.js"
        grep -nE "$XTERM_USE" "$f"
        exit 1
    fi
done
# Contraprueba: el patron debe detectar el uso real en la vista de terminal.
grep -qE "$XTERM_USE" "$CLEAN_TERM_JS" || {
    echo "FALLO: El patron anti-Xterm no detecta el uso real en $CLEAN_TERM_JS (compuerta inutil)"
    exit 1
}
echo "[OK]"

echo -n "6. Verificando que el decodificador no toca el DOM (logica pura)... "
if grep -qE "document\.|window\.[a-zA-Z]" "$STREAM_CLIENT_JS"; then
    echo "FALLO: AgentStreamClient referencia el DOM; debe ser logica pura"; exit 1;
fi
echo "[OK]"

echo -n "7. Verificando coexistencia con la terminal (AC-COEX-002)... "
grep -q "switchToChatView" "$CLEAN_TERM_JS" || { echo "FALLO: Falta switchToChatView"; exit 1; }
grep -q "switchToTerminalView" "$CLEAN_TERM_JS" || { echo "FALLO: Falta switchToTerminalView"; exit 1; }
grep -q "top-bar-to-chat" "$CLEAN_TERM_JS" || { echo "FALLO: Falta el boton conmutador en la Top App Bar"; exit 1; }
echo "[OK]"

echo -n "8. Verificando que no se anadieron dependencias de produccion... "
for dep in markdown-it dompurify; do
    grep -q "\"$dep\"" "$PACKAGE_JSON" || { echo "FALLO: $dep no esta en package.json"; exit 1; }
done
# La vista nativa solo puede importar lo que ya estaba en el bundle.
for imp in $(grep -oE '^import .* from "[^.][^"]*"' "$CHAT_VIEW_JS" | grep -oE '"[^"]*"$' | tr -d '"'); do
    grep -q "\"$imp\"" "$PACKAGE_JSON" || { echo "FALLO: import externo no declarado: $imp"; exit 1; }
done
echo "[OK]"

# ------------------------------------------------------------------------------
# 9. ASERCIONES DE COMPORTAMIENTO — el nucleo de esta suite
# ------------------------------------------------------------------------------
echo "9. Ejecutando aserciones de comportamiento contra el corpus (AC-STREAM-001..006)..."
(
    cd nova-src
    npx vitest run tests/unit/agentStreamClient.test.js tests/unit/agentSession.test.js --reporter=dot
) || { echo "FALLO: Las aserciones de comportamiento no pasaron"; exit 1; }
echo "   [OK]"

# ------------------------------------------------------------------------------
# 10. Anti-regresion de la terminal (AC-COEX-001)
# ------------------------------------------------------------------------------
echo "10. Verificando ausencia de regresion en la vista de terminal (AC-COEX-001)..."
bash harness/test_clean_agent_terminal.sh > /dev/null || {
    echo "FALLO: La suite de la terminal reporta regresion"; exit 1;
}
echo "   [OK]"

# ------------------------------------------------------------------------------
# 11-12. UIX y versionado
# ------------------------------------------------------------------------------
echo -n "11. Verificando ausencia total de emojis (AC-UIX-001)... "
for f in "$STREAM_CLIENT_JS" "$SESSION_JS" "$CHAT_VIEW_JS" "$CHAT_SCSS"; do
    if LC_ALL=C grep -qP '\xF0\x9F[\x8C-\xAB]|\xE2[\x98-\x9E]|\xEF\xB8\x8F' "$f"; then
        echo "FALLO: Emoji detectado en $f"; exit 1;
    fi
done
echo "[OK]"

echo -n "12. Verificando version 2.8.1 y versionCode 20801 (AC-BUILD-001)... "
grep -q 'version="2.8.1"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene version 2.8.1"; exit 1; }
grep -q 'android-versionCode="20801"' "$CONFIG_XML" || { echo "FALLO: config.xml no tiene versionCode 20801"; exit 1; }
grep -q '"version": "2.8.1"' "$PACKAGE_JSON" || { echo "FALLO: package.json no tiene version 2.8.1"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS DE SPEC-054 HAN SIDO SUPERADAS EXITOSAMENTE ==="
