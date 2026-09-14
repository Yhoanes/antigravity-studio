/**
 * CleanAgentTerminal - Terminal Agéntica Minimalista para Google Antigravity
 * Conforme a SPEC-028: Arquitectura Base sin ChatCanvas, directa a Xterm.js y PTY/AXS.
 */
import { Terminal as Xterm } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import { AttachAddon } from "@xterm/addon-attach";
import { Unicode11Addon } from "@xterm/addon-unicode11";
import { WebLinksAddon } from "@xterm/addon-web-links";
import { WebglAddon } from "@xterm/addon-webgl";
import "@xterm/xterm/css/xterm.css";
import "./clean-terminal.scss";

export class CleanAgentTerminal {
    constructor(options = {}) {
        this.port = options.port || 8767;
        this.autoCommand = options.autoCommand !== undefined ? options.autoCommand : "agy\r";
        this.showTopBar = options.showTopBar !== undefined ? options.showTopBar : true;
        this.terminal = null;
        this.fitAddon = null;
        this.attachAddon = null;
        this.websocket = null;
        this.pid = null;
        this.containerEl = null;
        this.viewportEl = null;
        this.badgeEl = null;
        this.btnRestart = null;
        this.isConnecting = false;
        this.isConnected = false;
        this._resizeObserver = null;
        this._cleanupResize = null;
    }

    mount(parentEl) {
        this.containerEl = document.createElement("div");
        this.containerEl.className = "clean-agent-container";

        // 1. Construir TopBar Minimalista si está habilitada
        if (this.showTopBar) {
            const topBar = document.createElement("header");
            topBar.className = "clean-agent-topbar";
            topBar.innerHTML = `
                <div class="clean-agent-brand">
                    <span class="brand-dot"></span>
                    <span class="clean-agent-title">Google Antigravity Agent</span>
                </div>
                <div class="clean-agent-actions">
                    <span class="clean-agent-badge status-connecting" id="agent-status-badge">CONECTANDO...</span>
                    <button class="clean-agent-btn" id="btn-restart" title="Reiniciar sesión">
                        <span class="btn-icon">↻</span>
                        <span class="btn-text">Reiniciar</span>
                    </button>
                </div>
            `;
            this.containerEl.appendChild(topBar);

            this.badgeEl = topBar.querySelector("#agent-status-badge");
            this.btnRestart = topBar.querySelector("#btn-restart");
            this.btnRestart.addEventListener("click", () => this.restartSession());
        }

        // 2. Construir Viewport de Terminal a Pantalla Completa
        this.viewportEl = document.createElement("main");
        this.viewportEl.className = "clean-agent-viewport";
        this.viewportEl.id = "terminal-viewport";
        this.containerEl.appendChild(this.viewportEl);

        parentEl.appendChild(this.containerEl);

        // 3. Inicializar Xterm.js
        this.initTerminal();

        // 4. Iniciar Conexión PTY en segundo plano
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
            allowProposedApi: true
        });

        this.fitAddon = new FitAddon();
        this.terminal.loadAddon(this.fitAddon);
        this.terminal.loadAddon(new Unicode11Addon());
        this.terminal.loadAddon(new WebLinksAddon((evt, uri) => {
            if (window.system?.openInBrowser) {
                window.system.openInBrowser(uri);
            } else {
                window.open(uri, "_blank");
            }
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
        this.updateStatus("CONECTANDO...", "connecting");

        try {
            await this.ensureAxsRunning();
            await this.waitForServerReady();
            this.pid = await this.createSession();
            await this.openWebSocket(this.pid);

            this.isConnected = true;
            this.isConnecting = false;
            this.updateStatus("READY", "ready");

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
            this.updateStatus("ERROR CONEXIÓN", "error");
            if (this.terminal) {
                this.terminal.writeln(`\r\n\x1b[31m[ERROR]\x1b[0m No se pudo conectar con el daemon AXS: ${error?.message || error}`);
                this.terminal.writeln(`\x1b[33mPresione 'Reiniciar' en la barra superior para reintentar.\x1b[0m\r\n`);
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
                this.updateStatus("DESCONECTADO", "error");
                this.terminal?.writeln("\r\n\x1b[33m[SESIÓN TERMINADA]\x1b[0m Conexión WebSocket con la terminal cerrada.");
            };

            this.websocket.onerror = (err) => {
                console.error("WebSocket error:", err);
                reject(err);
            };
        });
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
        if (this.badgeEl) {
            this.badgeEl.textContent = text;
            this.badgeEl.className = `clean-agent-badge status-${type}`;
        }
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
        await this.connect();
    }

    destroy() {
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
