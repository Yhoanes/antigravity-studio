#!/data/data/com.termux/files/usr/bin/bash
unset LD_PRELOAD
export PATH=/data/data/com.termux/files/usr/bin:
xz -dc /data/local/tmp/proot.tar.xz | tar -xf - --strip-components=6 -C /data/data/com.termux/files/usr/
chmod 0755 /data/data/com.termux/files/usr/bin/proot /data/data/com.termux/files/usr/libexec/proot/loader
ls -la /data/data/com.termux/files/usr/bin/proot /data/data/com.termux/files/usr/libexec/proot/loader
