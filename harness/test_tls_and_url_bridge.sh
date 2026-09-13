#!/bin/bash
# Test Harness - Automated Verification for SPEC-014
set -e

echo "=== INICIANDO VERIFICACIÓN DE CADENA TLS Y PUENTE DE DESPACHO DE URLS (SPEC-014) ==="

INSTALLER_JAVA="termux-src/app/src/main/java/com/termux/app/TermuxInstaller.java"
ACTIVITY_JAVA="termux-src/app/src/main/java/com/termux/app/TermuxActivity.java"
PROPERTIES_FILE="termux-src/app/src/main/assets/termux.properties"
ROOTFS_DIR="/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu"

# 1. Verificar replicación de certificados CA en TermuxInstaller.java
echo -n "Verificando copia de ca-certificates.crt en TermuxInstaller.java... "
grep -q "ca-certificates.crt" "$INSTALLER_JAVA" || { echo "FALLO: ca-certificates.crt no referenciado en TermuxInstaller"; exit 1; }
grep -q "etc/ssl/certs" "$INSTALLER_JAVA" || { echo "FALLO: Directorio etc/ssl/certs no gestionado"; exit 1; }
echo "[OK]"

# 2. Verificar rediseño de xdg-open hacia /tmp/antigravity_open_url
echo -n "Verificando puente IPC en xdg-open... "
grep -q "antigravity_open_url" "$INSTALLER_JAVA" || { echo "FALLO: xdg-open no escribe en antigravity_open_url"; exit 1; }
echo "[OK]"

# 3. Verificar implementación de FileObserver y Clipboard en TermuxActivity.java
echo -n "Verificando UrlDispatcherBridge y Clipboard en TermuxActivity.java... "
grep -q "setupUrlDispatcherBridge" "$ACTIVITY_JAVA" || { echo "FALLO: setupUrlDispatcherBridge no encontrado en TermuxActivity"; exit 1; }
grep -q "antigravity_open_url" "$ACTIVITY_JAVA" || { echo "FALLO: TermuxActivity no supervisa antigravity_open_url"; exit 1; }
grep -q "ClipboardManager" "$ACTIVITY_JAVA" || { echo "FALLO: Integración con ClipboardManager ausente"; exit 1; }
grep -q "ACTION_VIEW" "$ACTIVITY_JAVA" || { echo "FALLO: Despacho de Intent ACTION_VIEW ausente"; exit 1; }
echo "[OK]"

# 4. Verificar activación de terminal-onclick-url-open en termux.properties
echo -n "Verificando terminal-onclick-url-open en termux.properties... "
grep -q "terminal-onclick-url-open = true" "$PROPERTIES_FILE" || {
    echo "FALLO: terminal-onclick-url-open no habilitado en termux.properties"; exit 1;
}
echo "[OK]"

# 5. Comprobación física si el rootfs existe en el entorno
if [ -d "$ROOTFS_DIR/etc/ssl/certs" ]; then
    echo "=== RootFS físico presente. Verificando inodos de certificados ==="
    echo -n "Comprobando existencia y tamaño de ca-certificates.crt... "
    test -f "$ROOTFS_DIR/etc/ssl/certs/ca-certificates.crt" || { echo "FALLO: ca-certificates.crt no existe físicamente"; exit 1; }
    SIZE=$(stat -c %s "$ROOTFS_DIR/etc/ssl/certs/ca-certificates.crt" 2>/dev/null || stat -f %z "$ROOTFS_DIR/etc/ssl/certs/ca-certificates.crt")
    if [ "$SIZE" -lt 100000 ]; then
        echo "FALLO: Tamaño de ca-certificates.crt insuficiente ($SIZE bytes)"; exit 1;
    fi
    echo "[OK] ($SIZE bytes)"
fi

echo "=== TODAS LAS ASERCIONES DE SPEC-014 SE HAN CUMPLIDO SATISFACTORIAMENTE ==="
