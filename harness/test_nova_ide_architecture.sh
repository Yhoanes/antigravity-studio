#!/bin/bash
# Test Harness - Automated Verification for SPEC-017 (Nova IDE Architecture)
set -e

SPEC_FILE="specs/17-nova-ide-architecture-and-ui.md"

echo "=== INICIANDO VERIFICACIÓN DE ARQUITECTURA DE NOVA IDE (SPEC-017) ==="

# 1. Verificar existencia y legibilidad de la especificación
echo -n "1. Verificando existencia de SPEC-017... "
if [ ! -f "$SPEC_FILE" ]; then
    echo "FALLO: El archivo $SPEC_FILE no existe en el repositorio."
    exit 1
fi
echo "[OK]"

# 2. Verificar identidad visual y nomenclatura
echo -n "2. Verificando identidad visual y marca Nova IDE... "
grep -q "Nova IDE" "$SPEC_FILE" || { echo "FALLO: Nombre 'Nova IDE' ausente"; exit 1; }
grep -q "‹ ✦ ›" "$SPEC_FILE" || { echo "FALLO: Isotipo '‹ ✦ ›' ausente"; exit 1; }
grep -q "#090d16" "$SPEC_FILE" || { echo "FALLO: Color Deep Void #090d16 ausente"; exit 1; }
grep -q "#00f0ff" "$SPEC_FILE" || { echo "FALLO: Color Supernova Cyan #00f0ff ausente"; exit 1; }
grep -q "#8b5cf6" "$SPEC_FILE" || { echo "FALLO: Color Stellar Violet #8b5cf6 ausente"; exit 1; }
echo "[OK]"

# 3. Verificar arquitectura Thin Client y aprovisionamiento bajo demanda
echo -n "3. Verificando arquitectura Thin Client y On-Demand Provisioning... "
grep -q "Thin Client" "$SPEC_FILE" || { echo "FALLO: Mención a Thin Client ausente"; exit 1; }
grep -q "OnDemandProvisioner" "$SPEC_FILE" || { echo "FALLO: OnDemandProvisioner ausente"; exit 1; }
grep -q "SHA-256" "$SPEC_FILE" || { echo "FALLO: Verificación SHA-256 ausente"; exit 1; }
grep -q "securityAgreed" "$SPEC_FILE" || { echo "FALLO: Inyección silenciosa de onboarding ausente"; exit 1; }
echo "[OK]"

# 4. Verificar layout de tres columnas y terminal Xterm.js
echo -n "4. Verificando layout de 3 columnas y capacidades de Xterm.js... "
grep -q "Acode" "$SPEC_FILE" || { echo "FALLO: Motor táctil Acode ausente"; exit 1; }
grep -q "Xterm.js" "$SPEC_FILE" || { echo "FALLO: Componente Xterm.js ausente"; exit 1; }
grep -q "Toggle Maximize" "$SPEC_FILE" || { echo "FALLO: Función maximizar terminal ausente"; exit 1; }
grep -q "144Hz" "$SPEC_FILE" || { echo "FALLO: Optimización 144Hz ausente"; exit 1; }
echo "[OK]"

# 5. Verificar integración con gobernanza empresarial (agent.md, skills, MCP)
echo -n "5. Verificando gobernanza empresarial (agent.md, skills, MCP)... "
grep -q "agent.md" "$SPEC_FILE" || { echo "FALLO: Soporte de agent.md ausente"; exit 1; }
grep -q "enterprise-orchestrator" "$SPEC_FILE" || { echo "FALLO: Soporte de skills ausente"; exit 1; }
grep -q "mcp_config.json" "$SPEC_FILE" || { echo "FALLO: Soporte de servidores MCP ausente"; exit 1; }
echo "[OK]"

# 6. Verificar contratos de interfaces y criterios de aceptación
echo -n "6. Verificando contratos de interfaces y matriz de aceptación... "
grep -q "NovaUIEventsContract" "$SPEC_FILE" || { echo "FALLO: NovaUIEventsContract ausente"; exit 1; }
grep -q "NovaPtyBridgeContract" "$SPEC_FILE" || { echo "FALLO: NovaPtyBridgeContract ausente"; exit 1; }
grep -q "NovaOnDemandProvisioningContract" "$SPEC_FILE" || { echo "FALLO: NovaOnDemandProvisioningContract ausente"; exit 1; }
grep -q "NovaFileWatcherContract" "$SPEC_FILE" || { echo "FALLO: NovaFileWatcherContract ausente"; exit 1; }
grep -q "AC-NOVA-001" "$SPEC_FILE" || { echo "FALLO: Criterio AC-NOVA-001 ausente"; exit 1; }
grep -q "AC-NOVA-010" "$SPEC_FILE" || { echo "FALLO: Criterio AC-NOVA-010 ausente"; exit 1; }
echo "[OK]"

# 7. Verificar código base de Nova IDE en nova-src
echo -n "7. Verificando configuración base en nova-src... "
grep -q 'id="io.nova.ide"' "nova-src/config.xml" || { echo "FALLO: id io.nova.ide ausente en config.xml"; exit 1; }
grep -q '<name>Nova IDE</name>' "nova-src/config.xml" || { echo "FALLO: name Nova IDE ausente en config.xml"; exit 1; }
grep -q '"name": "io.nova.ide"' "nova-src/package.json" || { echo "FALLO: name io.nova.ide ausente en package.json"; exit 1; }
grep -q '"displayName": "Nova IDE"' "nova-src/package.json" || { echo "FALLO: displayName Nova IDE ausente en package.json"; exit 1; }
grep -q '<title>Nova IDE</title>' "nova-src/www/index.html" || { echo "FALLO: title Nova IDE ausente en index.html"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS DE VALIDACIÓN DE SPEC-017 HAN PASADO EXITOSAMENTE ==="
