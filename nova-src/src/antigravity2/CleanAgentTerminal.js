/**
 * CleanAgentTerminal - Terminal Agéntica Minimalista para Google Antigravity
 * Conforme a SPEC-028, SPEC-029 & SPEC-030: Terminal Agéntica Pura de Borde a Borde (Zero-Decoration),
 * Motor Gestual Táctil de Navegación (Arrow Emulation), Pegado por Long-Press y Auto-Bridge OAuth.
 */
import { Terminal as Xterm } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import { AttachAddon } from "@xterm/addon-attach";
import { Unicode11Addon } from "@xterm/addon-unicode11";
import { WebLinksAddon } from "@xterm/addon-web-links";
import { WebglAddon } from "@xterm/addon-webgl";
import TerminalTouchNavigation from "./TerminalTouchNavigation";
import { ProvisioningLoader } from "./ProvisioningLoader.js";
import "@xterm/xterm/css/xterm.css";
import "./clean-terminal.scss";

// Google OAuth 2.0 PKCE detection regex (SPEC-029 / SPEC-030: AC-CORE-07): accounts.google.com/o/oauth2/
const OAUTH_REGEX = /https:\/\/accounts\.google\.com\/o\/oauth2\/[^\s"'>\x1b\x00-\x1f\)]+/;

function formatErrorMessage(error) {
    if (!error) return "Error desconocido";
    if (typeof error === "string") return error;
    if (error.message) return error.message;
    if (error.error) return String(error.error);
    if (error.status) return `HTTP ${error.status} - ${error.data || "Fallo de conexión"}`;
    try {
        return JSON.stringify(error);
    } catch (e) {
        return String(error);
    }
}

export class CleanAgentTerminal {
    constructor(options = {}) {
        this.port = options.port || 8767;
        this.autoCommand = options.autoCommand !== undefined ? options.autoCommand : "clear && exec agy\r";
        this.terminal = null;
        this.fitAddon = null;
        this.attachAddon = null;
        this.websocket = null;
        this.pid = null;
        this.containerEl = null;
        this.viewportEl = null;
        this.touchNav = null;
        this.isConnecting = false;
        this.isConnected = false;
        this._resizeObserver = null;
        this._cleanupResize = null;

        // Estado de sesión OAuth y sniffer silencioso (SPEC-029 / SPEC-035: Anti-404 Stabilizer)
        this._lastOAuthUrl = null;
        this.activeOAuthUrl = null;
        this._hasAutoOpenedBrowser = false;
        this._streamBuffer = "";
        this._oauthDebounceTimer = null;

        // Persistencia de Sesión y Cortina Zero-Leak (SPEC-040)
        this.sessionStorageKey = "antigravity_active_session_pid";
        this.activeLoader = null;
    }

    mount(parentEl) {
        this.containerEl = document.createElement("div");
        this.containerEl.className = "clean-agent-container";

        // Viewport de Terminal a Pantalla Completa (SPEC-030: Zero-Decoration Architecture)
        this.viewportEl = document.createElement("main");
        this.viewportEl.className = "clean-agent-viewport fullscreen";
        this.viewportEl.id = "terminal-viewport";
        this.containerEl.appendChild(this.viewportEl);

        parentEl.appendChild(this.containerEl);

        // Inicializar Xterm.js
        this.initTerminal();

        // Enlazar Motor Gestual Táctil Híbrido (SPEC-030, SPEC-031 & SPEC-032: Momentum Scroll + Contextual D-Pad)
        this.touchNav = new TerminalTouchNavigation(this.terminal, this.viewportEl, {
            terminal: this.terminal,
            swipeThreshold: 28,
            deadzone: 10,
            longPressMs: 400,
            friction: 0.92,
            minVelocity: 0.5,
            onArrowUp: () => {
                if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                    this.websocket.send("\x1b[A");
                }
            },
            onArrowDown: () => {
                if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                    this.websocket.send("\x1b[B");
                }
            },
            onArrowLeft: () => {
                if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                    this.websocket.send("\x1b[D");
                }
            },
            onArrowRight: () => {
                if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                    this.websocket.send("\x1b[C");
                }
            },
            onPaste: async () => {
                await this.pasteFromClipboard();
            }
        });

        // PREM-01: Cortina Zero-Leak montada durante la inicialización
        if (!this.activeLoader) {
            this.activeLoader = new ProvisioningLoader();
            this.activeLoader.mount(this.containerEl || document.body);
            this.activeLoader.update(100, "Iniciando Google Antigravity...");
        }

        // Listener para reinicio de sesión interactivo si existe #btn-restart
        const btnRestart = document.getElementById("btn-restart");
        if (btnRestart) {
            btnRestart.addEventListener("click", () => this.restartSession());
        }

        // Iniciar Conexión PTY en segundo plano
        setTimeout(() => this.connect(), 100);
    }

    initTerminal() {
        this.terminal = new Xterm({
            cursorBlink: true,
            cursorStyle: "block",
            fontSize: 14,
            fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
            theme: {
                background: "#0b0f19",
                foreground: "#e2e8f0",
                cursor: "#60a5fa",
                selectionBackground: "rgba(96, 165, 250, 0.3)",
                scrollbarSliderBackground: "transparent",
                scrollbarSliderHoverBackground: "transparent",
                scrollbarSliderActiveBackground: "transparent",
            },
            overviewRulerWidth: 0,
            allowProposedApi: true,
            // SPEC-029 / SPEC-030: Soporte nativo de hipervínculos OSC 8 para clics/toques táctiles
            linkHandler: {
                activate: (event, uri) => {
                    console.log("OSC 8 Link activado táctilmente:", uri);
                    this.openInBrowser(uri);
                }
            }
        });

        this.fitAddon = new FitAddon();
        this.terminal.loadAddon(this.fitAddon);
        this.terminal.loadAddon(new Unicode11Addon());
        this.terminal.loadAddon(new WebLinksAddon((evt, uri) => {
            this.openInBrowser(uri);
        }));

        this.terminal.open(this.viewportEl);

        const pruneScrollbars = () => {
            this.viewportEl.querySelectorAll(".scrollbar, .slider").forEach(el => {
                el.style.display = "none";
                el.style.width = "0px";
                el.style.opacity = "0";
            });
        };
        pruneScrollbars();
        setTimeout(pruneScrollbars, 300);
        setTimeout(pruneScrollbars, 1000);

        try {
            const webgl = new WebglAddon();
            this.terminal.loadAddon(webgl);
        } catch (e) {
            console.warn("WebGL renderer no disponible, usando canvas estándar:", e);
        }

        this.setupAdaptiveResize();
    }

    setupAdaptiveResize() {
        let debounceTimer = null;
        const doFit = () => {
            if (!this.terminal || !this.fitAddon) return;
            try {
                this.fitAddon.fit();
                if (this.isConnected && this.pid) {
                    this.notifyServerResize(this.terminal.cols, this.terminal.rows);
                }
            } catch (err) {
                console.warn("Error en fit terminal:", err);
            }
        };

        const debouncedFit = () => {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(doFit, 100);
        };

        window.addEventListener("resize", debouncedFit);
        if (window.visualViewport) {
            window.visualViewport.addEventListener("resize", debouncedFit);
        }

        if (typeof ResizeObserver !== "undefined" && this.viewportEl) {
            this._resizeObserver = new ResizeObserver(debouncedFit);
            this._resizeObserver.observe(this.viewportEl);
        }

        this._cleanupResize = () => {
            clearTimeout(debounceTimer);
            window.removeEventListener("resize", debouncedFit);
            if (window.visualViewport) {
                window.visualViewport.removeEventListener("resize", debouncedFit);
            }
            if (this._resizeObserver) {
                this._resizeObserver.disconnect();
                this._resizeObserver = null;
            }
        };
    }

    async connect() {
        if (this.isConnecting || this.isConnected) return;
        this.isConnecting = true;

        // PREM-01: Cortina Zero-Leak montada durante la inicialización
        if (!this.activeLoader) {
            this.activeLoader = new ProvisioningLoader();
            this.activeLoader.mount(this.containerEl || document.body);
            this.activeLoader.update(100, "Iniciando Google Antigravity...");
        }

        try {
            await this.ensureAxsRunning();
            await this.waitForServerReady();

            let reattached = false;
            const savedPid = localStorage.getItem(this.sessionStorageKey);

            if (savedPid) {
                try {
                    console.log(`[SESSION] Intentando reenganche a PID previo: ${savedPid}`);
                    await this.openWebSocket(savedPid);
                    this.pid = savedPid;
                    reattached = true;
                    console.log("[SESSION] Reenganche a sesión persistente completado con éxito.");
                } catch (reconnectErr) {
                    console.warn("[SESSION] Sesión previa caducada. Creando nueva sesión...", reconnectErr);
                    localStorage.removeItem(this.sessionStorageKey);
                }
            }

            if (!reattached) {
                this.pid = await this.createSession();
                localStorage.setItem(this.sessionStorageKey, String(this.pid));
                await this.openWebSocket(this.pid);

                if (this.autoCommand) {
                    setTimeout(() => {
                        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                            this.websocket.send(this.autoCommand);
                        }
                    }, 200);
                }
            }

            this.isConnected = true;
            this.isConnecting = false;

            // Retiro suave de la cortina Zero-Leak tras conexión lista (AC-PREM-01)
            setTimeout(async () => {
                if (this.activeLoader) {
                    await this.activeLoader.finish();
                    this.activeLoader = null;
                }
            }, 350);
        } catch (error) {
            console.error("Fallo en inicialización de sesión:", error);
            this.isConnecting = false;
            this.isConnected = false;
            if (this.activeLoader) {
                await this.activeLoader.finish();
                this.activeLoader = null;
            }
            if (this.terminal) {
                this.terminal.writeln(`\r\n\x1b[31m[ERROR]\x1b[0m ${formatErrorMessage(error)}`);
            }
        }
    }

    async ensureAxsRunning() {
        const terminalPlugin = typeof Terminal !== "undefined" ? Terminal : (window.Terminal || null);
        if (terminalPlugin) {
            if (typeof terminalPlugin.isInstalled === "function" && !(await terminalPlugin.isInstalled())) {
                const loader = this.activeLoader || new ProvisioningLoader();
                if (!this.activeLoader) {
                    loader.mount(this.containerEl || document.body);
                }
                let installSuccess = false;
                try {
                    installSuccess = await terminalPlugin.install((percent, msg) => {
                        loader.update(percent, msg);
                    });
                    if (!installSuccess) {
                        const errMsg = terminalPlugin.lastInstallError || "Fallo en la descarga o aprovisionamiento del subsistema Linux";
                        throw new Error(errMsg);
                    }
                } finally {
                    if (!this.activeLoader) {
                        await loader.finish();
                    } else {
                        this.activeLoader.update(100, "Iniciando Google Antigravity...");
                    }
                }
            }
            if (typeof terminalPlugin.isAxsRunning === "function" && !(await terminalPlugin.isAxsRunning())) {
                // AC-PREM-01: No escribir [SISTEMA] Iniciando daemon AXS... en terminal para evitar fugas visuales
                await terminalPlugin.startAxs();
            }
        }
    }

    async waitForServerReady(maxAttempts = 30, delayMs = 300) {
        const url = `http://127.0.0.1:${this.port}/status`;
        for (let i = 0; i < maxAttempts; i++) {
            try {
                if (window.cordova?.plugin?.http) {
                    const res = await new Promise((resolve, reject) => {
                        cordova.plugin.http.sendRequest(url, { method: "GET", responseType: "text" }, resolve, reject);
                    });
                    if (res.status >= 200 && res.status < 300) return true;
                } else {
                    const res = await window.fetch(url).catch(() => null);
                    if (res && res.ok) return true;
                }
            } catch (e) {}
            await new Promise((r) => setTimeout(r, delayMs));
        }
        return true;
    }

    async createSession() {
        const url = `http://127.0.0.1:${this.port}/terminals`;
        const cols = this.terminal?.cols || 80;
        const rows = this.terminal?.rows || 24;

        if (window.cordova?.plugin?.http) {
            const res = await new Promise((resolve, reject) => {
                cordova.plugin.http.sendRequest(url, {
                    method: "POST",
                    serializer: "json",
                    data: { cols, rows },
                    responseType: "text"
                }, resolve, reject);
            });
            return String(res.data).trim();
        } else {
            const res = await window.fetch(url, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ cols, rows })
            });
            return (await res.text()).trim();
        }
    }

    openWebSocket(pid) {
        return new Promise((resolve, reject) => {
            const wsUrl = `ws://127.0.0.1:${this.port}/terminals/${pid}`;
            let opened = false;
            this.websocket = new WebSocket(wsUrl);

            // SPEC-029 / SPEC-030: Sniffer silencioso de WebSocket para detección de URL OAuth
            this.websocket.addEventListener("message", (event) => {
                this.detectOAuthUrl(event.data);
            });

            this.websocket.onopen = () => {
                opened = true;
                if (this.attachAddon) {
                    try { this.attachAddon.dispose(); } catch (e) {}
                    this.attachAddon = null;
                }
                this.attachAddon = new AttachAddon(this.websocket);
                this.terminal.loadAddon(this.attachAddon);
                this.terminal.focus();
                this.fitAddon.fit();
                resolve();
            };

            this.websocket.onclose = () => {
                this.isConnected = false;
                this.isConnecting = false;
                if (!opened) {
                    reject(new Error(`No se pudo conectar al WebSocket para PID ${pid}`));
                } else {
                    this.terminal?.writeln("\r\n\x1b[33m[SESIÓN TERMINADA]\x1b[0m Conexión WebSocket con la terminal cerrada.");
                }
            };

            this.websocket.onerror = (err) => {
                console.error("WebSocket error:", err);
                if (!opened) {
                    reject(err);
                }
            };
        });
    }

    /**
     * SPEC-035: AC-OAUTH-01 & AC-OAUTH-02 Validador estricto de parámetros PKCE.
     * Previene despachar fragmentos truncados hacia Google Chrome erradicando el error 404.
     */
    validateOAuthUrl(url) {
        if (!url || url.length < 300) return false;
        const requiredParams = ["client_id=", "redirect_uri=", "scope=", "code_challenge="];
        const hasAllRequired = requiredParams.every((param) => url.includes(param));
        const hasStateOrResponse = url.includes("state=") || url.includes("response_type=code");
        return hasAllRequired && hasStateOrResponse;
    }

    /**
     * SPEC-029, SPEC-030 & SPEC-035: Sniffer silencioso de flujo de datos en el WebSocket.
     * Acumula fragmentos TCP y utiliza ventana de debounce de 350ms para validar URL completa antes de abrir Chrome.
     */
    detectOAuthUrl(chunk) {
        try {
            const text = typeof chunk === "string"
                ? chunk
                : (chunk instanceof ArrayBuffer
                    ? new TextDecoder().decode(chunk)
                    : String(chunk || ""));

            if (text.includes("accounts.google.com") || (this._streamBuffer && !this._hasAutoOpenedBrowser)) {
                this._streamBuffer = (this._streamBuffer || "") + text;
                if (this._streamBuffer.length > 16384) {
                    this._streamBuffer = this._streamBuffer.slice(-16384);
                }

                if (this._oauthDebounceTimer) {
                    clearTimeout(this._oauthDebounceTimer);
                }

                this._oauthDebounceTimer = setTimeout(() => {
                    this.processOAuthBuffer();
                }, 350);
            }
        } catch (err) {
            console.warn("Error en detectOAuthUrl:", err);
        }
    }

    processOAuthBuffer() {
        if (!this._streamBuffer) return;
        const match = this._streamBuffer.match(OAUTH_REGEX);
        if (match) {
            const candidateUrl = match[0];
            if (this.validateOAuthUrl(candidateUrl)) {
                this._lastOAuthUrl = candidateUrl;
                this.activeOAuthUrl = candidateUrl;
                if (!this._hasAutoOpenedBrowser) {
                    this._hasAutoOpenedBrowser = true;
                    this.openInBrowser(candidateUrl);
                }
            }
        }
    }

    sniffWebSocketMessage(rawData) {
        this.detectOAuthUrl(rawData);
    }

    openInBrowser(uri) {
        if (!uri) return;
        if (window.system?.openInBrowser) {
            window.system.openInBrowser(uri);
        } else {
            window.open(uri, "_blank");
        }
    }

    openOAuthUrl(url) {
        this.openInBrowser(url);
    }

    /**
     * SPEC-030: AC-TOUCH-03 Pegado directo desde el portapapeles
     * Invocado por gestos de pulsación prolongada (long-press 400ms) o doble toque.
     */
    async pasteFromClipboard() {
        let text = "";
        try {
            if (window.cordova?.plugins?.clipboard) {
                text = await new Promise((resolve) => {
                    cordova.plugins.clipboard.paste((t) => resolve(t || ""), () => resolve(""));
                });
            } else if (navigator.clipboard?.readText) {
                text = await navigator.clipboard.readText().catch(() => "");
            }
        } catch (err) {
            console.warn("Fallo leyendo portapapeles:", err);
        }

        if (text && text.trim() && this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            this.websocket.send(`${text.trim()}\r`);
        }
    }

    async pasteAuthCode() {
        return this.pasteFromClipboard();
    }

    async handlePasteOAuthCode() {
        return this.pasteFromClipboard();
    }

    notifyServerResize(cols, rows) {
        if (!this.pid) return;
        const resizeUrl = `http://127.0.0.1:${this.port}/terminals/${this.pid}/size?cols=${cols}&rows=${rows}`;
        if (window.cordova?.plugin?.http) {
            cordova.plugin.http.sendRequest(resizeUrl, { method: "POST" }, () => {}, () => {});
        } else {
            window.fetch(resizeUrl, { method: "POST" }).catch(() => {});
        }
    }

    updateStatus(text, type) {
        // En SPEC-030 no hay barra ni badge visual para mantener terminal 100% pura
    }

    async restartSession() {
        localStorage.removeItem(this.sessionStorageKey);
        if (this.attachAddon) {
            try { this.attachAddon.dispose(); } catch (e) {}
            this.attachAddon = null;
        }
        if (this.websocket) {
            try { this.websocket.close(); } catch (_) {}
            this.websocket = null;
        }
        this.isConnected = false;
        this.isConnecting = false;
        this.pid = null;
        this._hasAutoOpenedBrowser = false;
        this._streamBuffer = "";
        if (this.terminal) {
            this.terminal.clear();
        }
        if (!this.activeLoader) {
            this.activeLoader = new ProvisioningLoader();
            this.activeLoader.mount(this.containerEl || document.body);
        }
        this.activeLoader.update(100, "Reiniciando Google Antigravity...");
        await this.connect();
    }

    destroy() {
        if (this.touchNav) {
            this.touchNav.destroy();
            this.touchNav = null;
        }
        if (this._cleanupResize) {
            this._cleanupResize();
            this._cleanupResize = null;
        }
        if (this.attachAddon) {
            try { this.attachAddon.dispose(); } catch (e) {}
            this.attachAddon = null;
        }
        if (this.websocket) {
            try { this.websocket.close(); } catch (e) {}
            this.websocket = null;
        }
        if (this.terminal) {
            try { this.terminal.dispose(); } catch (e) {}
            this.terminal = null;
        }
        if (this.containerEl && this.containerEl.parentNode) {
            this.containerEl.parentNode.removeChild(this.containerEl);
            this.containerEl = null;
        }
    }
}

export default CleanAgentTerminal;
