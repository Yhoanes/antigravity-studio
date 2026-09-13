#!/bin/bash
# Test Harness - Automated Verification for SPEC-013
set -e

echo "=== INICIANDO VERIFICACIÓN DE DNS LOOPBACK Y AISLAMIENTO DE PATH GUEST (SPEC-013) ==="

INSTALLER_JAVA="termux-src/app/src/main/java/com/termux/app/TermuxInstaller.java"
ROOTFS_DIR="/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu"

# 1. Verificar inyección incondicional de /etc/hosts y /etc/resolv.conf en TermuxInstaller.java
echo -n "Verificando supresión de 'if [ ! -f /etc/resolv.conf ]'... "
if grep -q 'if \[ ! -f "\$ROOTFS_DIR/etc/resolv.conf" \]' "$INSTALLER_JAVA"; then
    echo "FALLO: Aún existe condición de 0 bytes para resolv.conf en TermuxInstaller.java"
    exit 1
fi
if grep -q 'if \[ ! -f "\$ROOTFS_DIR/etc/hosts" \]' "$INSTALLER_JAVA"; then
    echo "FALLO: Aún existe condición de 0 bytes para hosts en TermuxInstaller.java"
    exit 1
fi
echo "[OK]"

# 2. Verificar presencia de plantillas de hosts y resolv.conf en el generador de script
echo -n "Verificando plantillas incondicionales de hosts y DNS... "
grep -q 'EOF_RESOLV' "$INSTALLER_JAVA" || { echo "FALLO: Bloque EOF_RESOLV no encontrado"; exit 1; }
grep -q 'EOF_HOSTS' "$INSTALLER_JAVA" || { echo "FALLO: Bloque EOF_HOSTS no encontrado"; exit 1; }
grep -q '127.0.0.1 localhost' "$INSTALLER_JAVA" || { echo "FALLO: 127.0.0.1 localhost no configurado"; exit 1; }
grep -q 'nameserver 8.8.8.8' "$INSTALLER_JAVA" || { echo "FALLO: nameserver 8.8.8.8 no configurado"; exit 1; }
echo "[OK]"

# 3. Verificar inyección explícita de PATH guest en env
echo -n "Verificando PATH guest en la invocación env de proot... "
grep -q 'PATH="/usr/local/bin:/usr/local/sbin:/usr/bin:/usr/sbin:/bin:/sbin"' "$INSTALLER_JAVA" || {
    echo "FALLO: PATH guest no inyectado en la llamada env de proot"; exit 1;
}
echo "[OK]"

# 4. Validación en sistema de archivos si el rootfs está presente localmente
if [ -d "$ROOTFS_DIR/etc" ]; then
    echo "=== RootFS presente en entorno de prueba. Verificando inodos físicos ==="
    
    echo -n "Comprobando contenido de $ROOTFS_DIR/etc/hosts... "
    grep -q "127.0.0.1 localhost" "$ROOTFS_DIR/etc/hosts" || { echo "FALLO: /etc/hosts vacío o corrupto"; exit 1; }
    grep -q "::1 localhost" "$ROOTFS_DIR/etc/hosts" || { echo "FALLO: ::1 localhost ausente en /etc/hosts"; exit 1; }
    echo "[OK]"

    echo -n "Comprobando contenido de $ROOTFS_DIR/etc/resolv.conf... "
    grep -q "nameserver 8.8.8.8" "$ROOTFS_DIR/etc/resolv.conf" || { echo "FALLO: /etc/resolv.conf sin nameserver"; exit 1; }
    echo "[OK]"
else
    echo "NOTA: RootFS físico no presente en entorno de compilación estática; validación de código completada."
fi

echo "=== TODAS LAS ASERCIONES DE SPEC-013 SE HAN CUMPLIDO SATISFACTORIAMENTE ==="
