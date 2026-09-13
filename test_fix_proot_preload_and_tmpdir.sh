#!/bin/bash
# Test Harness - Automated Verification for SPEC-012
set -e

echo "=== INICIANDO VERIFICACIÓN DE CORRECCIÓN PROOT: PRELOAD, TMPDIR Y RESILIENCIA (SPEC-012) ==="

INSTALLER_JAVA="termux-src/app/src/main/java/com/termux/app/TermuxInstaller.java"

# 1. Verificar presencia de sanitización de entorno
echo -n "Verificando 'unset LD_PRELOAD' y 'unset LD_LIBRARY_PATH'... "
grep -q 'unset LD_PRELOAD' "$INSTALLER_JAVA" || { echo "FALLO: unset LD_PRELOAD ausente en TermuxInstaller"; exit 1; }
grep -q 'unset LD_LIBRARY_PATH' "$INSTALLER_JAVA" || { echo "FALLO: unset LD_LIBRARY_PATH ausente en TermuxInstaller"; exit 1; }
echo "[OK]"

# 2. Verificar configuración de TMPDIR y PROOT_TMP_DIR
echo -n "Verificando creación y exportación de TMPDIR y PROOT_TMP_DIR... "
grep -q 'TMPDIR=' "$INSTALLER_JAVA" || { echo "FALLO: TMPDIR no configurado"; exit 1; }
grep -q 'PROOT_TMP_DIR=' "$INSTALLER_JAVA" || { echo "FALLO: PROOT_TMP_DIR no configurado"; exit 1; }
grep -q '0700' "$INSTALLER_JAVA" || { echo "FALLO: Permisos 0700 no asignados a TMPDIR"; exit 1; }
echo "[OK]"

# 3. Verificar invocación limpia con env -u
echo -n "Verificando invocación con 'env -u LD_PRELOAD -u LD_LIBRARY_PATH'... "
grep -q 'env -u LD_PRELOAD -u LD_LIBRARY_PATH' "$INSTALLER_JAVA" || { echo "FALLO: Invocación de PRoot sin env -u"; exit 1; }
echo "[OK]"

# 4. Verificar fallback resiliente contra cierres inesperados
echo -n "Verificando shell de rescate interactivo... "
grep -q 'PROOT_EXIT_CODE' "$INSTALLER_JAVA" || { echo "FALLO: Código de salida no capturado"; exit 1; }
grep -q 'rescue-shell' "$INSTALLER_JAVA" || { echo "FALLO: Prompt de rescue-shell ausente"; exit 1; }
grep -q 'exec \"$PREFIX/bin/bash\" -i' "$INSTALLER_JAVA" || { echo "FALLO: Shell de rescate interactivo no ejecutado"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS ASERCIONES DE SPEC-012 SE HAN CUMPLIDO SATISFACTORIAMENTE ==="
