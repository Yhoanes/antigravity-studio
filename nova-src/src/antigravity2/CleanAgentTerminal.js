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
import "@xterm/xterm/css/xterm.css";
import "./clean-terminal.scss";

// Google OAuth 2.0 PKCE detection regex (SPEC-029 / SPEC-030: AC-CORE-07): accounts.google.com/o/oauth2/
const OAUTH_REGEX = /https:\/\/accounts\.google\.com\/o\/oauth2\/[^\s"'>\x1b\x00-\x1f\)]+/;

export class CleanAgentTerminal {
    constructor(options = {}) {
        this.port = options.port || 8767;
        this.autoCommand = options.autoCommand !== undefined ? options.autoCommand : "agy\r";
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

        // Estado de sesión OAuth y sniffer silencioso
        this._lastOAuthUrl = null;
        this.activeOAuthUrl = null;
        this._hasAutoOpenedBrowser = false;
        this._streamBuffer = "";
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

        // Enlazar Motor Gestual Táctil Cuadridireccional (SPEC-030 / SPEC-031: 4-Way D-Pad)
        this.touchNav = new TerminalTouchNavigation(this.viewportEl, {
            swipeThreshold: 28,
            deadzone: 10,
            longPressMs: 400,
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
            },
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

        try {
            await this.ensureAxsRunning();
            await this.waitForServerReady();
            this.pid = await this.createSession();
            await this.openWebSocket(this.pid);

            this.isConnected = true;
            this.isConnecting = false;

            // Ejecución automática de agy al establecer la sesión (AC-CORE-04)
            if (this.autoCommand) {
                setTimeout(() => {
                    if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                        this.websocket.send(this.autoCommand);
                    }
                }, 250);
            }
        } catch (error) {
            console.error("Fallo de conexión en CleanAgentTerminal:", error);
            this.isConnecting = false;
            this.isConnected = false;
            if (this.terminal) {
                this.terminal.writeln(`\r\n\x1b[31m[ERROR]\x1b[0m No se pudo conectar con el daemon AXS: ${error?.message || error}`);
            }
        }
    }

    async ensureAxsRunning() {
        const terminalPlugin = typeof Terminal !== "undefined" ? Terminal : (window.Terminal || null);
        if (terminalPlugin) {
            if (typeof terminalPlugin.isInstalled === "function" && !(await terminalPlugin.isInstalled())) {
                this.terminal?.writeln("\r\n\x1b[33m[SISTEMA]\x1b[0m Extrayendo subsistema Linux inicial...");
                await terminalPlugin.install();
            }
            if (typeof terminalPlugin.isAxsRunning === "function" && !(await terminalPlugin.isAxsRunning())) {
                this.terminal?.writeln("\r\n\x1b[33m[SISTEMA]\x1b[0m Iniciando daemon AXS...");
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
            this.websocket = new WebSocket(wsUrl);

            // SPEC-029 / SPEC-030: Sniffer silencioso de WebSocket para detección de URL OAuth
            this.websocket.addEventListener("message", (event) => {
                this.detectOAuthUrl(event.data);
            });

            this.websocket.onopen = () => {
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
                this.terminal?.writeln("\r\n\x1b[33m[SESIÓN TERMINADA]\x1b[0m Conexión WebSocket con la terminal cerrada.");
            };

            this.websocket.onerror = (err) => {
                console.error("WebSocket error:", err);
                reject(err);
            };
        });
    }

    /**
     * SPEC-029 & SPEC-030: Sniffer silencioso de flujo de datos en el WebSocket.
     * Detecta patrones de URL Google OAuth y dispara auto-apertura en Chrome (FLAG_ACTIVITY_NEW_TASK).
     * No inyecta ni muestra barras visuales decorativas (Terminal 100% Pura).
     */
    detectOAuthUrl(chunk) {
        try {
            const text = typeof chunk === "string"
                ? chunk
                : (chunk instanceof ArrayBuffer
                    ? new TextDecoder().decode(chunk)
                    : String(chunk || ""));

            this._streamBuffer = (this._streamBuffer || "") + text;
            if (this._streamBuffer.length > 8192) {
                this._streamBuffer = this._streamBuffer.slice(-8192);
            }

            const match = this._streamBuffer.match(OAUTH_REGEX);
            if (match) {
                const detectedUrl = match[0];
                this._lastOAuthUrl = detectedUrl;
                this.activeOAuthUrl = detectedUrl;

                // Auto-apertura única en Chrome en tarea aislada
                if (!this._hasAutoOpenedBrowser) {
                    this._hasAutoOpenedBrowser = true;
                    this.openInBrowser(detectedUrl);
                }
            }
        } catch (err) {
            console.warn("Error en detectOAuthUrl:", err);
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
        if (this.terminal) {
            this.terminal.writeln("\r\n\x1b[36m[REINICIANDO]\x1b[0m Cerrando sesión previa y reconectando...");
        }
        if (this.attachAddon) {
            try { this.attachAddon.dispose(); } catch (e) {}
            this.attachAddon = null;
        }
        if (this.websocket) {
            try { this.websocket.close(); } catch (e) {}
            this.websocket = null;
        }
        this.isConnected = false;
        this.isConnecting = false;
        this.pid = null;
        this._hasAutoOpenedBrowser = false;
        this._streamBuffer = "";
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
