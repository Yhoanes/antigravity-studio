const Executor = require("./Executor");

const Terminal = {
    /**
     * Starts the AXS environment by writing init scripts and executing the sandbox.
     * @param {boolean} [installing=false] - Whether AXS is being started during installation.
     * @param {Function} [logger=console.log] - Function to log standard output.
     * @param {Function} [err_logger=console.error] - Function to log errors.
     * @returns {Promise<boolean>} - Returns true if installation completes with exit code 0, void if not installing
     */
    async startAxs(installing = false, logger = console.log, err_logger = console.error,failsafe = false) {
        const filesDir = await new Promise((resolve, reject) => {
            system.getFilesDir(resolve, reject);
        });

        const arch = await new Promise((resolve, reject) => {
            system.getArch(resolve, reject);
        });

        const failsafeArg = failsafe ? "--failsafe" : "";

        const [initAlpine, rmWrapper, initSandbox] = await Promise.all([
            readAsset("init-alpine.sh"),
            readAsset("rm-wrapper.sh"),
            readAsset("init-sandbox.sh"),
        ]);

        await this.migrateLegacyHome();

        const isFdroid = await Executor.execute("echo $FDROID");

        if(isFdroid !== "true"){
//the symlink must be updated everytime because the symlinks to native libs can break after app updates
        await Executor.execute("rm -f $PREFIX/axs && ln -s $NATIVE_DIR/libaxs.so $PREFIX/axs")
}
        

        await writeText(`${filesDir}/init-alpine.sh`, initAlpine);
        await writeText(`${filesDir}/init-sandbox.sh`, initSandbox);

        try {
            const caCertContent = await readAsset("cacert.pem");
            if (caCertContent) {
                await writeText(`${filesDir}/cacert.pem`, caCertContent);
                if (await fileExists(`${filesDir}/alpine`)) {
                    await ensureDir(`${filesDir}/alpine/etc/ssl/certs`);
                    await ensureDir(`${filesDir}/alpine/etc/pki/tls/certs`);
                    await writeText(`${filesDir}/alpine/etc/ssl/certs/ca-certificates.crt`, caCertContent);
                    await Executor.execute(`ln -sf certs/ca-certificates.crt "${filesDir}/alpine/etc/ssl/cert.pem" 2>/dev/null || true`);
                    await Executor.execute(`cp -f "${filesDir}/cacert.pem" "${filesDir}/alpine/etc/pki/tls/certs/ca-bundle.crt" 2>/dev/null || true`);
                    await Executor.execute(`chmod 644 "${filesDir}/alpine/etc/ssl/certs/ca-certificates.crt" 2>/dev/null || true`);
                }
            }
        } catch (caErr) {
            console.warn("CA certs startAxs injection warning:", caErr);
        }

        if (arch !== "arm64-v8a") {
            await deleteFile(`${filesDir}/alpine/bin/rm`).catch(() => {});
            await writeText(`${filesDir}/alpine/bin/rm`, rmWrapper);
            await setExec(`${filesDir}/alpine/bin/rm`, true);
        }

        if (installing) {
            return new Promise((resolve, reject) => {
                let lastError = "";

                Executor.start("sh", (type, data) => {
                    //console[type === "stderr" ? "error" : "log"](`[AXS] ${data}`);
                    logger(`${type} ${data}`);

                    if (type === "stderr" && data) {
                        lastError = lastError ? `${lastError}\n${data}` : data;
                    }

                    // Check for exit code during installation
                    if (type === "exit") {
                        const success = data === "0";
                        if (!success) {
                            this.lastInstallError = lastError
                                ? `Sandbox configuration failed with exit code ${data}: ${lastError}`
                                : `Sandbox configuration failed with exit code ${data}`;
                        }
                        resolve(success);
                    }
                }).then(async (uuid) => {
                    await Executor.write(uuid, `source ${filesDir}/init-sandbox.sh ${installing ? "--installing" : ""} ${failsafeArg}; exit`);
                }).catch((error) => {
                    const message = `Failed to start AXS: ${formatError(error)}`;
                    this.lastInstallError = message;
                    err_logger(message);
                    resolve(false);
                });
            });
        } else {
            try {
                const uuid = await Executor.start("sh", (type, data) => {
                    //console[type === "stderr" ? "error" : "log"](`[AXS] ${data}`);
                    logger(`${type} ${data}`);
                });
                await Executor.write(uuid, `source ${filesDir}/init-sandbox.sh ${installing ? "--installing" : ""} ${failsafeArg}; exit`);
            } catch (error) {
                const message = `Failed to start AXS: ${formatError(error)}`;
                err_logger(message);
                throw new Error(message);
            }
        }
    },

    /**
     * Stops the AXS process by forcefully killing it.
     * @returns {Promise<void>}
     */
    async stopAxs() {
        await Executor.execute(`kill -KILL $(cat $PREFIX/pid)`);
    },

    /**
     * Checks if the AXS process is currently running.
     * @returns {Promise<boolean>} - `true` if AXS is running, `false` otherwise.
     */
    async isAxsRunning() {
        const filesDir = await new Promise((resolve, reject) => {
            system.getFilesDir(resolve, reject);
        });

        const pidExists = await new Promise((resolve, reject) => {
            system.fileExists(`${filesDir}/pid`, false, (result) => {
                resolve(result == 1);
            }, reject);
        });

        if (!pidExists) return false;

        const result = await Executor.BackgroundExecutor.execute(`kill -0 $(cat $PREFIX/pid) 2>/dev/null && echo "true" || echo "false"`);
        return String(result).toLowerCase() === "true";
    },

    /**
     * Installs Alpine by downloading binaries and extracting the root filesystem.
     * Also sets up additional dependencies for F-Droid variant.
     * @param {Function} [logger=console.log] - Function to log standard output.
     * @param {Function} [err_logger=console.error] - Function to log errors.
     * @returns {Promise<boolean>} - Returns true if installation completes with exit code 0
     */
    async install(logger = console.log, err_logger = console.error) {
        if (!(await this.isSupported())) return false;

        const isFdroid = await Executor.execute("echo $FDROID");

        this.lastInstallError = "";

        try {
            //cleanup before insatll
            await this.uninstall();
        } catch (e) {
            //supress error
        }

        const filesDir = await new Promise((resolve, reject) => {
            system.getFilesDir(resolve, reject);
        });

        const arch = await new Promise((resolve, reject) => {
            system.getArch(resolve, reject);
        });

        try {

            const architectures = {
                "arm64-v8a": {
                    libraryDirectory: "arm64",
                    axsArchitecture: "arm64",
                    alpineDirectory: "aarch64",
                    alpineFilename: "alpine-minirootfs-3.21.0-aarch64.tar.gz",
                    hasLibproot32: true
                },

                "armeabi-v7a": {
                    libraryDirectory: "arm32",
                    axsArchitecture: "armv7",
                    alpineDirectory: "armhf",
                    alpineFilename: "alpine-minirootfs-3.21.0-armhf.tar.gz",
                    hasLibproot32: false
                },

                "x86_64": {
                    libraryDirectory: "x64",
                    axsArchitecture: "x86_64",
                    alpineDirectory: "x86_64",
                    alpineFilename: "alpine-minirootfs-3.21.0-x86_64.tar.gz",
                    hasLibproot32: true
                }
            };

            const architecture = architectures[arch];

            if (!architecture) {
                throw new Error(`Unsupported architecture: ${arch}`);
            }

            if (arch === "arm64-v8a") {
                logger("📦  Extrayendo Ubuntu ARM64 glibc rootfs desde assets locales...");
                await new Promise((resolve, reject) => {
                    system.extractAsset(
                        "antigravity/rootfs/ubuntu_arm64.tar.gz",
                        `${filesDir}/rootfs.tar.gz`,
                        resolve,
                        () => {
                            system.extractAsset(
                                "antigravity/rootfs/ubuntu_arm64.tar",
                                `${filesDir}/rootfs.tar.gz`,
                                resolve,
                                () => {
                                    system.extractAsset(
                                        "antigravity/ubuntu_arm64.tar.gz",
                                        `${filesDir}/rootfs.tar.gz`,
                                        resolve,
                                        () => {
                                            system.extractAsset(
                                                "antigravity/ubuntu_arm64.tar",
                                                `${filesDir}/rootfs.tar.gz`,
                                                resolve,
                                                reject
                                            );
                                        }
                                    );
                                }
                            );
                        }
                    );
                });

                logger("📦  Extrayendo Google Antigravity CLI (arm64) desde assets locales...");
                await new Promise((resolve, reject) => {
                    system.extractAsset(
                        "antigravity/cli_linux_arm64.tar.gz",
                        `${filesDir}/cli_linux_arm64.tar.gz`,
                        resolve,
                        () => {
                            system.extractAsset(
                                "antigravity/cli_linux_arm64.tar",
                                `${filesDir}/cli_linux_arm64.tar.gz`,
                                resolve,
                                reject
                            );
                        }
                    );
                });

                logger("✅  Todos los activos locales fueron extraídos con éxito (CERO descargas de red).");
            } else {
                logger("📦  Extrayendo sandbox filesystem...");
                await new Promise((resolve, reject) => {
                    system.extractAsset(`alpine_assets/${architecture.libraryDirectory}/alpine.rootfs`, `${filesDir}/alpine.tar.gz`, resolve, (e)=>{
                        console.error(`Failed to extract alpine.tar.gz: ${formatError(e)}`);
                        reject(e);
                    });
                });
            }

            try {
                await Executor.execute("rm -f $PREFIX/axs && ln -s $NATIVE_DIR/libaxs.so $PREFIX/axs");
            } catch(e) {
                err_logger(`${formatError(e)}`);
            }

            logger("📁  Setting up directories...");

            await ensureDir(`${filesDir}/.downloaded`);

            const alpineDir = `${filesDir}/alpine`;

            await ensureDir(alpineDir);

            if (arch === "arm64-v8a") {
                logger("📦  Extracting Ubuntu ARM64 filesystem...");
                await Executor.execute(`tar --no-same-owner -xf ${filesDir}/rootfs.tar.gz -C ${alpineDir} || [ -f ${alpineDir}/bin/sh ]`);
                await Executor.execute(`ln -sf perl ${alpineDir}/usr/bin/perl5.38.2 2>/dev/null || true`);
                await Executor.execute(`ln -sf gunzip ${alpineDir}/usr/bin/uncompress 2>/dev/null || true`);

                logger("📦  Installing Google Antigravity CLI (agy)...");
                await ensureDir(`${alpineDir}/usr/local/bin`);
                await Executor.execute(`tar --no-same-owner -xf ${filesDir}/cli_linux_arm64.tar.gz -C ${alpineDir}/usr/local/bin`);
                await Executor.execute(`chmod +x ${alpineDir}/usr/local/bin/antigravity`);
                await Executor.execute(`ln -sf antigravity ${alpineDir}/usr/local/bin/agy`);

                // Cleanup temporary archives to optimize device storage (SPEC-024 §2.3)
                await deleteFile(`${filesDir}/rootfs.tar.gz`).catch(() => {});
                await deleteFile(`${filesDir}/cli_linux_arm64.tar.gz`).catch(() => {});
            } else {
                logger("📦  Extracting sandbox filesystem...");
                await Executor.execute(`tar --no-same-owner -xf ${filesDir}/alpine.tar.gz -C ${alpineDir}`);
                await deleteFile(`${filesDir}/alpine.tar.gz`).catch(() => {});
            }

            // Silent onboarding injection & settings
            logger("⚙️  Injecting silent onboarding & workspace configuration...");
            const onboardingConfig = JSON.stringify({
                consumerOnboardingComplete: true,
                enterpriseOnboardingComplete: false,
                onboardingComplete: true
            }, null, 2);

            const settingsConfig = JSON.stringify({
                trustedWorkspaces: [
                    "/home/studio/workspace",
                    "/storage/emulated/0/Projects",
                    "/sdcard/Projects"
                ]
            }, null, 2);

            await ensureDir(`${alpineDir}/root/.gemini/antigravity-cli/cache`);
            await writeText(`${alpineDir}/root/.gemini/antigravity-cli/cache/onboarding.json`, onboardingConfig);
            await writeText(`${alpineDir}/root/.gemini/antigravity-cli/settings.json`, settingsConfig);

            await ensureDir(`${alpineDir}/public/.gemini/antigravity-cli/cache`);
            await writeText(`${alpineDir}/public/.gemini/antigravity-cli/cache/onboarding.json`, onboardingConfig);
            await writeText(`${alpineDir}/public/.gemini/antigravity-cli/settings.json`, settingsConfig);

            // Create studio workspace
            await ensureDir(`${alpineDir}/home/studio/workspace`);
            await ensureDir(`${alpineDir}/public/workspace`);

            // Create /usr/local/bin/xdg-open
            const xdgOpenScript = `#!/bin/sh
URL="$1"
if [ -x /system/bin/am ]; then
    exec /system/bin/am start -a android.intent.action.VIEW --activity-new-task -d "$URL" >/dev/null 2>&1
else
    echo "Abra en su navegador: $URL"
fi
`;
            await writeText(`${alpineDir}/usr/local/bin/xdg-open`, xdgOpenScript);
            await setExec(`${alpineDir}/usr/local/bin/xdg-open`, true);
            await Executor.execute(`ln -sf xdg-open ${alpineDir}/usr/local/bin/x-www-browser`);

            logger("⚙️  Applying basic configuration...");
            await ensureDir(`${alpineDir}/etc`);
            await ensureDir(`${alpineDir}/etc/ssl/certs`);
            await ensureDir(`${alpineDir}/etc/pki/tls/certs`);
            await Executor.execute(`rm -f "${alpineDir}/etc/resolv.conf" && echo "nameserver 8.8.8.8" > "${alpineDir}/etc/resolv.conf" && echo "nameserver 8.8.4.4" >> "${alpineDir}/etc/resolv.conf"`);
            await Executor.execute(`echo "127.0.0.1 localhost" > "${alpineDir}/etc/hosts" && echo "::1 localhost ip6-localhost ip6-loopback" >> "${alpineDir}/etc/hosts"`);
            await Executor.execute(`echo "hosts: files dns" > "${alpineDir}/etc/nsswitch.conf"`);
            await Executor.execute(`echo -e "aid_sdcard_rw:x:1015:root,studio\naid_media_rw:x:1023:root,studio\naid_inet:x:3003:root,studio\naid_net_raw:x:3004:root,studio\naid_admin:x:3005:root,studio\naid_everybody:x:9997:root,studio\naid_app:x:20399:root,studio\naid_app2:x:50399:root,studio\naid_isolated:x:99909997:root,studio" >> "${alpineDir}/etc/group"`);

            // Inyección de certificados CA Mozilla / Google Trust Services (GTS)
            try {
                const caCertContent = await readAsset("cacert.pem");
                if (caCertContent) {
                    await writeText(`${alpineDir}/etc/ssl/certs/ca-certificates.crt`, caCertContent);
                    await writeText(`${filesDir}/cacert.pem`, caCertContent);
                    await Executor.execute(`ln -sf certs/ca-certificates.crt "${alpineDir}/etc/ssl/cert.pem"`);
                    await Executor.execute(`cp -f "${alpineDir}/etc/ssl/certs/ca-certificates.crt" "${alpineDir}/etc/pki/tls/certs/ca-bundle.crt" 2>/dev/null || true`);
                    await Executor.execute(`chmod 644 "${alpineDir}/etc/ssl/certs/ca-certificates.crt"`);
                }
            } catch (caErr) {
                console.warn("CA certs injection warning:", caErr);
            }

            if (arch !== "arm64-v8a") {
                const rmWrapper = await readAsset("rm-wrapper.sh");
                await deleteFile(`${alpineDir}/bin/rm`).catch(() => {});
                await writeText(`${alpineDir}/bin/rm`, rmWrapper);
                await setExec(`${alpineDir}/bin/rm`, true);
            }

            logger("✅  Extraction complete");
            await ensureDir(`${filesDir}/.extracted`);

            logger("⚙️  Updating sandbox enviroment...");
            const installResult = await this.startAxs(true, logger, err_logger);
            if (!installResult) {
                throw new Error(this.lastInstallError || "Sandbox configuration failed.");
            }
            return installResult;

        } catch (e) {
            const message = formatError(e);
            this.lastInstallError = message;
            err_logger(`Installation failed: ${message}`);
            console.error("Installation failed:", e);
            return false;
        }
    },

    /**
     * Checks if alpine is already installed.
     * @returns {Promise<boolean>} - Returns true if all required files and directories exist.
     */
    isInstalled() {
        return new Promise(async (resolve, reject) => {
            const filesDir = await new Promise((resolve, reject) => {
                system.getFilesDir(resolve, reject);
            });

            const alpineExists = await new Promise((resolve, reject) => {
                system.fileExists(`${filesDir}/alpine`, false, (result) => {
                    resolve(result == 1);
                }, reject);
            });

            const downloaded = alpineExists && await new Promise((resolve, reject) => {
                system.fileExists(`${filesDir}/.downloaded`, false, (result) => {
                    resolve(result == 1);
                }, reject);
            });

            const extracted = alpineExists && await new Promise((resolve, reject) => {
                system.fileExists(`${filesDir}/.extracted`, false, (result) => {
                    resolve(result == 1);
                }, reject);
            });

            const configured = alpineExists && await new Promise((resolve, reject) => {
                system.fileExists(`${filesDir}/.configured`, false, (result) => {
                    resolve(result == 1);
                }, reject);
            });

            resolve(alpineExists && downloaded && extracted && configured);
        });
    },

    /**
     * Checks if the current device architecture is supported.
     * @returns {Promise<boolean>} - `true` if architecture is supported, otherwise `false`.
     */
    isSupported() {
        return new Promise((resolve, reject) => {
            system.getArch((arch) => {
                resolve(["arm64-v8a", "armeabi-v7a", "x86_64"].includes(arch));
            }, reject);
        });
    },
    /**
     * Creates a backup of the Alpine Linux installation
     * @async
     * @function backup
     * @description Creates a compressed tar archive of the Alpine installation
     * @returns {Promise<string>} Promise that resolves to the file URI of the created backup file (aterm_backup.tar)
     * @throws {string} Rejects with "Alpine is not installed." if Alpine is not currently installed
     * @throws {string} Rejects with command output if backup creation fails
     * @example
     * try {
     *   const backupPath = await backup();
     *   console.log(`Backup created at: ${backupPath}`);
     * } catch (error) {
     *   console.error(`Backup failed: ${error}`);
     * }
     */
    backup() {
        return new Promise(async (resolve, reject) => {
            if (!await this.isInstalled()) {
                reject("Alpine is not installed.");
                return;
            }
            const cmd = `
            set -e
            INCLUDE_FILES="alpine .downloaded .extracted .configured axs"
            if [ "$FDROID" = "true" ]; then
                INCLUDE_FILES="$INCLUDE_FILES libtalloc.so.2 libproot-xed.so"
            fi
            EXCLUDE="--exclude=alpine/data --exclude=alpine/system --exclude=alpine/vendor --exclude=alpine/sdcard --exclude=alpine/storage --exclude=alpine/public --exclude=alpine/apex --exclude=alpine/odm --exclude=alpine/product --exclude=alpine/system_ext --exclude=alpine/linkerconfig --exclude=alpine/proc --exclude=alpine/sys --exclude=alpine/dev --exclude=alpine/run --exclude=alpine/tmp"
            tar -cf "$PREFIX/aterm_backup.tar" -C "$PREFIX" $EXCLUDE $INCLUDE_FILES
            echo "ok"
            `;
            const result = await Executor.execute(cmd);
            if (result === "ok") {
                resolve(cordova.file.dataDirectory + "aterm_backup.tar");
            } else {
                reject(result);
            }
        });
    },
    /**
     * Restores Alpine Linux installation from a backup file
     * @async
     * @function restore
     * @description Restores the Alpine installation from a previously created backup file (aterm_backup.tar).
     * This function stops any running Alpine processes, removes existing installation files, and extracts
     * the backup to restore the previous state. The backup file must exist in the expected location.
     * @returns {Promise<string>} Promise that resolves to "ok" when restoration completes successfully
     * @throws {string} Rejects with "Backup File does not exist" if aterm_backup.tar is not found
     * @throws {string} Rejects with command output if restoration fails
     * @example
     * try {
     *   await restore();
     *   console.log("Alpine installation restored successfully");
     * } catch (error) {
     *   console.error(`Restore failed: ${error}`);
     * }
     */
    restore() {
        return new Promise(async (resolve, reject) => {
            if (await this.isAxsRunning()) {
                await this.stopAxs();
            }

            const cmd = `
            set -e

            INCLUDE_FILES="$PREFIX/alpine $PREFIX/.downloaded $PREFIX/.extracted $PREFIX/.configured $PREFIX/axs"

            if [ "$FDROID" = "true" ]; then
                INCLUDE_FILES="$INCLUDE_FILES $PREFIX/libtalloc.so.2 $PREFIX/libproot-xed.so"
            fi

            for item in $INCLUDE_FILES; do
                rm -rf -- "$item"
            done

            tar -xf $PREFIX/aterm_backup.* -C "$PREFIX"
            echo "ok"
            `;

            const result = await Executor.BackgroundExecutor.execute(cmd);
            if (result === "ok") {
                resolve(result);
            } else {
                reject(result);
            }
        });
    },
    /**
     * Uninstalls the Alpine Linux installation
     * @async
     * @function uninstall
     * @description Completely removes the Alpine Linux installation from the device by deleting all
     * Alpine-related files and directories. This function stops any running Alpine processes before
     * removal. NOTE: This does not perform cleanup of $PREFIX
     * @returns {Promise<string>} Promise that resolves to "ok" when uninstallation completes successfully
     * @throws {string} Rejects with command output if uninstallation fails
     * @example
     * try {
     *   await uninstall();
     *   console.log("Alpine installation removed successfully");
     * } catch (error) {
     *   console.error(`Uninstall failed: ${error}`);
     * }
     */
    uninstall() {
        return new Promise(async (resolve, reject) => {
            if (await this.isAxsRunning()) {
                await this.stopAxs();
            }

            const cmd = `
            set -e

            INCLUDE_FILES="$PREFIX/alpine $PREFIX/.downloaded $PREFIX/.extracted $PREFIX/.configured $PREFIX/axs"

            if [ "$FDROID" = "true" ]; then
                INCLUDE_FILES="$INCLUDE_FILES $PREFIX/libtalloc.so.2 $PREFIX/libproot-xed.so"
            fi

            for item in $INCLUDE_FILES; do
                rm -rf -- "$item"
            done

            echo "ok"
            `;
            const result = await Executor.BackgroundExecutor.execute(cmd);
            if (result === "ok") {
                resolve(result);
            } else {
                reject(result);
            }
        });
    },

    /**
     * Migrates the legacy terminal home directories into public/MIGRATE.
     * Older builds stored user files under alpine/home and alpine/root.
     * After /home, /root and /public were merged into a single public
     * directory, any files still left in the old locations are copied
     * into public/MIGRATE (keeping their source structure) so nothing is
     * hidden or lost. This is a no-op once the migration has run.
     * @returns {Promise<void>}
     */
    async migrateLegacyHome() {
        if (this._legacyHomeMigrated) return;
        try {
            const cmd = `
                MIGRATE="$PREFIX/public/MIGRATE"

                # Already migrated
                [ -e "$MIGRATE/.migrated" ] && exit 0

                COPIED=false

                if [ -d "$PREFIX/alpine/home" ] && [ -n "$(find "$PREFIX/alpine/home" -mindepth 1 -maxdepth 1 2>/dev/null | head -n 1)" ]; then
                    mkdir -p "$MIGRATE/home"
                    if cp -a "$PREFIX/alpine/home/." "$MIGRATE/home/"; then
                        COPIED=true
                    else
                        exit 1
                    fi
                fi

                if [ -d "$PREFIX/alpine/root" ] && [ -n "$(find "$PREFIX/alpine/root" -mindepth 1 -maxdepth 1 2>/dev/null | head -n 1)" ]; then
                    mkdir -p "$MIGRATE/root"
                    if cp -a "$PREFIX/alpine/root/." "$MIGRATE/root/"; then
                        COPIED=true
                    else
                        exit 1
                    fi
                fi

                # Mark as migrated so this only runs once
                if [ "$COPIED" = "true" ]; then
                    touch "$MIGRATE/.migrated"
                fi
            `;
            await Executor.BackgroundExecutor.execute(cmd);
            this._legacyHomeMigrated = true;
        } catch (error) {
            console.error("Failed to migrate legacy terminal home:", formatError(error));
        }
    },

    formatError
};


function readAsset(assetPath, callback) {
    const assetUrl = "file:///android_asset/" + assetPath;

    const promise = new Promise((resolve, reject) => {
        window.resolveLocalFileSystemURL(assetUrl, fileEntry => {
            fileEntry.file(file => {
                const reader = new FileReader();
                reader.onloadend = () => resolve(reader.result);
                reader.onerror = () => reject(reader.error || new Error(`Failed to read ${assetPath}`));
                reader.readAsText(file);
            }, reject);
        }, reject);
    });

    if (callback) {
        promise.then(callback).catch(console.error);
    }

    return promise;
}

function fileExists(path) {
    return new Promise((resolve, reject) => {
        system.fileExists(path, false, (result) => {
            resolve(result == 1);
        }, reject);
    });
}

async function ensureDir(path) {
    if (await fileExists(path)) return;

    await new Promise((resolve, reject) => {
        system.mkdirs(path, resolve, reject);
    });
}

function writeText(path, content) {
    return new Promise((resolve, reject) => {
        system.writeText(path, content, resolve, reject);
    });
}

function deleteFile(path) {
    return new Promise((resolve, reject) => {
        system.deleteFile(path, resolve, reject);
    });
}

function setExec(path, executable) {
    return new Promise((resolve, reject) => {
        system.setExec(path, executable, resolve, reject);
    });
}


function formatError(error) {
    if (error == null) return "Unknown error";
    if (error instanceof Error) return error.message || String(error);
    if (typeof error === "string") return error || "Unknown error";
    if (typeof error === "object") {
        const parts = [];
        if (error.status != null) parts.push(`status ${error.status}`);
        if (error.error) parts.push(String(error.error));
        if (error.message) parts.push(String(error.message));
        if (error.exception) parts.push(String(error.exception));
        if (error.url) parts.push(`URL: ${error.url}`);
        if (parts.length) return parts.join(" - ");

        try {
            return JSON.stringify(error);
        } catch (jsonError) {
            return String(error);
        }
    }

    return String(error);
}

module.exports = Terminal;
