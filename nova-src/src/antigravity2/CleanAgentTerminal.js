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
import { GoogleAuthCard } from "./GoogleAuthCard.js";
import { OnboardingWizard, THEME_PRESETS } from "./OnboardingWizard.js";
import { AccountMenuModal } from "./AccountMenuModal.js";
import { FloatingInputPill } from "./FloatingInputPill.js";
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

        // Tarjeta de Autenticación Google y Estado de Cuenta (SPEC-041)
        this.authCard = null;
        this.accountBadgeEl = null;
        this.authenticatedUserStorageKey = "antigravity_authenticated_user";
        this.authenticatedUser = localStorage.getItem(this.authenticatedUserStorageKey) || null;

        // Asistente Visual Multi-Paso Nativo (SPEC-043: Material 3 Onboarding Wizard)
        this.onboardingWizard = null;

        // Banderas One-Shot de Onboarding y Auto-Clipboard (SPEC-042)
        this._hasAutoSelectedLogin = false;
        this._hasAutoConfirmedTheme = false;
        this._hasAutoConfirmedTrust = false;
        this._hasAutoConfirmedTerms = false;
        this._hasInjectedOAuthCode = false;
        this._termsAccepted = false;
        this._pendingCredentialPurge = false;
        this.currentThemeId = "dark";
        this.floatingPill = null;

        this._setupClipboardAutoInjection();
    }

    mount(parentEl) {
        this.containerEl = document.createElement("div");
        this.containerEl.className = "clean-agent-container";

        // SPEC-048: Top App Bar Dedicada de 48px
        this.topBarEl = document.createElement("header");
        this.topBarEl.className = "clean-agent-top-bar";
        this.topBarEl.innerHTML = `
            <div class="top-bar-left">
                <svg class="top-bar-logo" viewBox="0 0 48 48" width="22" height="22">
                    <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                    <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                    <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                    <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
                </svg>
                <span class="top-bar-title">Antigravity</span>
            </div>
            <div class="top-bar-right" id="top-bar-account-container"></div>
        `;
        this.containerEl.appendChild(this.topBarEl);

        // Viewport de terminal desplazado por debajo de la barra (SPEC-048: Anti-colisión)
        this.viewportEl = document.createElement("main");
        this.viewportEl.className = "clean-agent-viewport has-top-bar";
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

        // SPEC-041: Si el usuario ya está autenticado, renderizar Account Badge de inmediato
        // LIFECYCLE-01: NO invocar _setupOnboardingWizard() aquí de forma síncrona.
        // El asistente se montará exclusivamente de forma diferida en connect() tras openWebSocket().
        if (this.authenticatedUser) {
            this.renderAccountBadge(this.authenticatedUser, "Google AI Ultra");
        }

        // Iniciar Conexión PTY en segundo plano
        setTimeout(() => this.connect(), 50);
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

                // SPEC-049: Purga profunda de credenciales en bash fresca ANTES de lanzar agy
                if (this._pendingCredentialPurge) {
                    this._pendingCredentialPurge = false;
                    if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                        this.websocket.send("rm -rf /public/.config/antigravity /root/.config/antigravity /home/studio/.config/antigravity ~/.config/antigravity ~/.config/google* /public/.gemini /root/.gemini 2>/dev/null\r");
                        await new Promise((r) => setTimeout(r, 300));
                    }
                }

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
                // SPEC-044: Montaje diferido de OnboardingWizard exclusivamente cuando el WebSocket está OPEN
                if (!this.authenticatedUser && !this.onboardingWizard) {
                    this._setupOnboardingWizard();
                }

                // SPEC-050: Montar Floating Input Pill si el usuario ya está autenticado
                if (this.authenticatedUser && !this.floatingPill) {
                    this._mountFloatingPill();
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
                // Solo dar foco si no hay wizard ni auth card cubriendo la terminal
                if (!this.onboardingWizard && !this.authCard) {
                    this.terminal.focus();
                }
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
     * SPEC-043: Inicializa el Asistente Visual Multi-Paso Nativo (Material 3 Onboarding Wizard)
     * con enmascaramiento 100% opaco y captura de eventos.
     */
    _setupOnboardingWizard() {
        if (this.authenticatedUser || this.onboardingWizard) return;
        this.onboardingWizard = new OnboardingWizard({
            onAction: (action, data) => {
                if (action === "open_browser") {
                    this._hasAutoOpenedBrowser = true;
                    this.openInBrowser(data);
                } else if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                    this.websocket.send(data);
                }
            },
            onSelectAuthMethod: () => {
                if (this.activeOAuthUrl) {
                    this._hasAutoOpenedBrowser = true;
                    this.openInBrowser(this.activeOAuthUrl);
                    if (this.onboardingWizard) {
                        this.onboardingWizard.goToStep("step-auth-code", this.activeOAuthUrl);
                    }
                } else if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                    this.websocket.send("\r");
                }
            },
            onOpenBrowser: (url) => {
                this._hasAutoOpenedBrowser = true;
                this.openInBrowser(url);
            },
            onSelectTheme: (agyIndex, seq) => {
                if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                    const finalSeq = seq || ("\x1b[B".repeat(typeof agyIndex === "number" ? agyIndex : 4) + "\r");
                    this.websocket.send(finalSeq);
                }
            },
            onAcceptTerms: async () => {
                this._termsAccepted = true;
                if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                    // UX-04: Secuencia atómica espaciada: Tab -> 80ms -> Flecha Derecha -> 80ms -> Enter
                    this.websocket.send("\t");
                    await new Promise((r) => setTimeout(r, 80));
                    if (this.websocket?.readyState === WebSocket.OPEN) {
                        this.websocket.send("\x1b[C");
                        await new Promise((r) => setTimeout(r, 80));
                        if (this.websocket?.readyState === WebSocket.OPEN) {
                            this.websocket.send("\r");
                        }
                    }
                }

                // SEAM-02: Safety fallback timeout (1400ms) si agy no emite el prompt esperado
                setTimeout(() => {
                    if (this.onboardingWizard) {
                        console.log("[SEAMLESS] Safety fallback timeout alcanzado (1400ms). Disipando wizard...");
                        this.onboardingWizard.fadeOut(350);
                        this.onboardingWizard = null;
                    }
                }, 1400);
            },
            onDismissComplete: () => {
                if (this.terminal) {
                    this.terminal.focus();
                }
                if (!this.floatingPill) {
                    this._mountFloatingPill();
                } else {
                    this.floatingPill.show();
                }
            }
        });
        this.onboardingWizard.mount(this.containerEl || document.body);
        this.floatingPill?.hide();
    }

    /**
     * SPEC-050: Barra de Entrada Flotante (Floating Input Pill)
     */
    _mountFloatingPill() {
        if (this.floatingPill) return;
        this.floatingPill = new FloatingInputPill({
            onSend: (text) => {
                if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                    this.websocket.send(text + "\r");
                    return true;
                }
                return false;
            }
        });
        this.floatingPill.mount(this.containerEl || document.body);
        this.floatingPill.show();
        // Anti-colisión: reservar espacio abajo
        if (this.viewportEl) {
            this.viewportEl.classList.add("has-input-pill");
        }
        // Recalcular filas de la terminal
        if (this.fitAddon) {
            setTimeout(() => this.fitAddon.fit(), 50);
        }
    }

    /**
     * SPEC-042: Configura escuchadores para detectar retorno de foco desde Google Chrome
     * e inyectar automáticamente el código de autorización OAuth desde el portapapeles.
     */
    _setupClipboardAutoInjection() {
        const checkAndInject = () => this._checkAndInjectClipboardOAuth();
        window.addEventListener("focus", checkAndInject);
        document.addEventListener("resume", checkAndInject);
    }

    async _checkAndInjectClipboardOAuth() {
        if (this._hasInjectedOAuthCode || this.authenticatedUser) return;
        if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) return;
        try {
            let text = "";
            if (window.cordova?.plugins?.clipboard) {
                text = await new Promise((resolve) => {
                    cordova.plugins.clipboard.paste((t) => resolve(t || ""), () => resolve(""));
                });
            } else if (navigator.clipboard?.readText) {
                text = await navigator.clipboard.readText().catch(() => "");
            }
            text = (text || "").trim();
            // AUTO-03 / WIZ-03: Validar patrón oficial de token Google OAuth 2.0 PKCE: ^4/
            if (/^4\/[a-zA-Z0-9_-]{20,}/.test(text) || /^4\/[a-zA-Z0-9_-]+/.test(text)) {
                console.log("[OAUTH] Código de autorización detectado en portapapeles. Inyectando silenciosamente...");
                this._hasInjectedOAuthCode = true;
                if (this.onboardingWizard) {
                    this.onboardingWizard.setTokenInjected();
                }
                if (this.authCard) {
                    this.authCard.setAuthenticating();
                }
                this.websocket.send(text + "\r");
            }
        } catch (err) {
            console.warn("[OAUTH] Error leyendo portapapeles en resume/focus:", err);
        }
    }

    /**
     * SPEC-042: Inspecciona los fragmentos de texto del socket para responder automáticamente
     * a las pantallas de onboarding del CLI sin interacción manual.
     * @param {string} text Fragmento decodificado de texto PTY.
     */
    _handleAutoResponderStream(text) {
        if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) return;
        if (!text || typeof text !== "string") return;

        // SPEC-047: Auto-despacho incondicional de Google OAuth (Opción 1) y pre-cálculo de URL en <= 5ms
        if (text.includes("Select login method:") || text.includes("> 1. Google OAuth") || text.includes("1. Google OAuth") || /Select login method:/i.test(text)) {
            if (!this._hasAutoSelectedLogin) {
                console.log("[AUTO-RESPONDER] 'Select login method' detectado. Enviando Enter inmediato a opción 1...");
                this._hasAutoSelectedLogin = true;
                setTimeout(() => {
                    if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                        this.websocket.send("\r");
                    }
                }, 50);
            }
            if (this.onboardingWizard && this.onboardingWizard.currentStep !== "step-auth-code") {
                this.onboardingWizard.goToStep("step-auth-method");
            }
        }

        // SPEC-043 & SPEC-045: Sincronización reactiva con OnboardingWizard (Paso 3: Selector de Tema)
        if (text.includes("color scheme") || text.includes("Choose your color scheme") || text.includes("Select theme:") || /(?:Choose your color scheme|color scheme)/i.test(text)) {
            if (this.onboardingWizard) {
                this.onboardingWizard.goToStep("step-theme-selector");
            } else if (!this._hasAutoConfirmedTheme) {
                console.log("[AUTO-RESPONDER] Prompt de tema detectado. Enviando Dark (Índice 4: 4 flechas abajo + Enter)...");
                this._hasAutoConfirmedTheme = true;
                setTimeout(() => {
                    if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                        // SPEC-045: Índice 4 en agy = Dark (4 flechas abajo + Enter)
                        this.websocket.send("\x1b[B\x1b[B\x1b[B\x1b[B\r");
                    }
                }, 50);
            }
        }

        // AC-AUTO-02: Auto-confirmación de Confianza de Carpeta (Trust workspace)
        if (text.includes("trust this folder") || text.includes("Do you trust the authors") || text.includes("Yes, I trust") || /(?:trust this folder|Do you trust)/i.test(text)) {
            if (!this._hasAutoConfirmedTrust) {
                console.log("[AUTO-RESPONDER] Prompt de confianza detectado. Enviando Enter...");
                this._hasAutoConfirmedTrust = true;
                setTimeout(() => {
                    if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                        this.websocket.send("\r");
                    }
                }, 50);
            }
        }

        // SPEC-043: Sincronización reactiva con OnboardingWizard (Paso 4: Términos / [Done])
        if (text.includes("Terms of Service") || text.includes("[Done]") || text.includes("Security Agreement") || /(?:Terms of Service|\[Done\])/i.test(text)) {
            if (this.onboardingWizard) {
                this.onboardingWizard.goToStep("step-terms-telemetry");
            } else if (!this._hasAutoConfirmedTerms) {
                console.log("[AUTO-RESPONDER] Prompt de Términos / Done detectado. Enviando Enter...");
                this._hasAutoConfirmedTerms = true;
                setTimeout(() => {
                    if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                        this.websocket.send("\r");
                    }
                }, 50);
            }
        }

        // SPEC-049: SEAM-02 / SEAM-03 Detección reactiva de bienvenida y revelado sin destello
        if (this._termsAccepted && (
            text.includes("What would you like to do") ||
            text.includes("? What would") ||
            text.includes("agy>") ||
            /(?:What would you like to do|agy>)/i.test(text)
        )) {
            if (this.onboardingWizard) {
                console.log("[SEAMLESS] agy interactive prompt detectado. Disipando wizard con cero destello...");
                this.onboardingWizard.fadeOut(350);
                this.onboardingWizard = null;
            }
        }
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

            // SPEC-042: Auto-respondedor reactivo de onboarding
            this._handleAutoResponderStream(text);

            // SPEC-041 & SPEC-043: Detección reactiva de confirmación de usuario autenticado
            if (
                text.includes("shadrick1212@gmail.com") ||
                text.includes("Google AI Ultra") ||
                /(?:Logged in as|autenticado como|authenticated as)\s+([^\s\(\)]+@[^\s\(\)]+)/i.test(text)
            ) {
                let email = "shadrick1212@gmail.com";
                const userMatch = text.match(/(?:Logged in as|autenticado como|authenticated as)\s+([^\s\(\)]+@[^\s\(\)]+)/i);
                if (userMatch && userMatch[1]) {
                    email = userMatch[1].trim();
                }
                this.authenticatedUser = email;
                localStorage.setItem(this.authenticatedUserStorageKey, email);
                this.renderAccountBadge(email, "Google AI Ultra");
                if (this.onboardingWizard) {
                    this.onboardingWizard.fadeOut(350);
                    this.onboardingWizard = null;
                }
                if (this.authCard) {
                    this.authCard.fadeOut(250);
                    this.authCard = null;
                }
                if (this.terminal) {
                    this.terminal.focus();
                }
                if (!this.floatingPill) {
                    this._mountFloatingPill();
                } else {
                    this.floatingPill.show();
                }
            }

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

                // SPEC-041: Si el usuario ya está autenticado, no desplegar la tarjeta de login
                if (this.authenticatedUser) {
                    return;
                }

                // SPEC-047: Precargar URL en OnboardingWizard y registrar en memoria
                if (this.onboardingWizard) {
                    this.onboardingWizard.setAuthUrl(candidateUrl);
                    return;
                }

                // SPEC-041: Desplegar GoogleAuthCard con diseño Material 3 si no hay wizard
                if (!this.authCard) {
                    this.authCard = new GoogleAuthCard({
                        onSignIn: (url) => {
                            this._hasAutoOpenedBrowser = true;
                            this.openInBrowser(url);
                        }
                    });
                    this.authCard.mount(this.containerEl || document.body);
                }
                this.authCard.setAuthUrl(candidateUrl);
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

    /**
     * SPEC-048: Account Badge en la barra superior (shadrick1212@gmail.com • Google AI Ultra)
     */
    renderAccountBadge(userEmail = "shadrick1212@gmail.com", tier = "Google AI Ultra") {
        const container = this.containerEl?.querySelector("#top-bar-account-container") || this.containerEl || document.body;

        if (this.accountBadgeEl) {
            const pill = this.accountBadgeEl.querySelector(".account-pill-text, .account-pill");
            if (pill) pill.textContent = `${userEmail} • ${tier}`;
            return;
        }
        this.accountBadgeEl = document.createElement("button");
        this.accountBadgeEl.className = "clean-agent-account-pill clean-agent-account-badge";
        this.accountBadgeEl.id = "clean-agent-account-pill";
        this.accountBadgeEl.innerHTML = `
            <span class="badge-dot"></span>
            <span class="account-pill-text account-pill">${userEmail} • ${tier}</span>
        `;
        this.accountBadgeEl.addEventListener("click", () => this.handleAccountMenu());
        container.appendChild(this.accountBadgeEl);
    }

    /**
     * SPEC-048: Menú de Cuenta Material 3 (Erradicación total de confirm nativo)
     */
    handleAccountMenu() {
        this.floatingPill?.hide();
        const modal = new AccountMenuModal({
            userEmail: this.authenticatedUser || "shadrick1212@gmail.com",
            userTier: "Google AI Ultra",
            modelName: "Gemini 3.8 Flash (High)",
            workspacePath: "/home/studio/workspace",
            currentThemeId: this.currentThemeId || "dark",
            onSelectTheme: (theme) => this.applyTheme(theme),
            onRestartSession: () => this.restartSession(),
            onLogout: () => this.logout(),
            onDismiss: () => {
                this.floatingPill?.show();
            }
        });
        modal.mount(this.containerEl || document.body);
    }

    /**
     * SPEC-048: Conmutación de tema en caliente
     */
    applyTheme(theme) {
        if (!theme) return;
        const themePreset = typeof theme === "string" ? THEME_PRESETS.find(t => t.id === theme) : theme;
        if (!themePreset) return;

        this.currentThemeId = themePreset.id;
        if (this.terminal && themePreset.colors) {
            this.terminal.options.theme = {
                background: themePreset.colors.bg,
                foreground: themePreset.colors.fg,
                cursor: themePreset.colors.accent,
                selectionBackground: "rgba(96, 165, 250, 0.3)",
                scrollbarSliderBackground: "transparent",
                scrollbarSliderHoverBackground: "transparent",
                scrollbarSliderActiveBackground: "transparent"
            };
        }
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            const seq = "\x1b[B".repeat(typeof themePreset.agyIndex === "number" ? themePreset.agyIndex : 4) + "\r";
            this.websocket.send(seq);
        }
    }

    async logout() {
        console.log("[DEEP-LOGOUT] Iniciando protocolo de purga profunda de credenciales...");

        // SEAM-05: Vaciado inmediato del portapapeles de Android
        if (window.cordova?.plugins?.clipboard) {
            try { cordova.plugins.clipboard.copy(""); } catch (_) {}
        }
        if (navigator.clipboard?.writeText) {
            try { await navigator.clipboard.writeText(""); } catch (_) {}
        }

        // Purgar credenciales y banderas locales
        localStorage.removeItem(this.authenticatedUserStorageKey);
        localStorage.removeItem(this.sessionStorageKey);
        this.authenticatedUser = null;
        this._hasInjectedOAuthCode = false;
        this._hasAutoSelectedLogin = false;
        this._hasAutoOpenedBrowser = false;
        this._hasAutoConfirmedTheme = false;
        this._hasAutoConfirmedTrust = false;
        this._hasAutoConfirmedTerms = false;
        this._termsAccepted = false;
        this.activeOAuthUrl = null;
        this._lastOAuthUrl = null;

        if (this.accountBadgeEl && this.accountBadgeEl.parentNode) {
            this.accountBadgeEl.parentNode.removeChild(this.accountBadgeEl);
            this.accountBadgeEl = null;
        }

        // Marcar purga pendiente para la próxima sesión bash fresca
        this._pendingCredentialPurge = true;

        await this.restartSession();
    }

    async restartSession() {
        localStorage.removeItem(this.sessionStorageKey);
        if (this.onboardingWizard) {
            this.onboardingWizard.dismiss(0);
            this.onboardingWizard = null;
        }
        if (this.authCard) {
            this.authCard.dismiss();
            this.authCard = null;
        }
        if (this.floatingPill) {
            this.floatingPill.dismiss();
            this.floatingPill = null;
        }
        if (this.viewportEl) {
            this.viewportEl.classList.remove("has-input-pill");
        }
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
        this._hasAutoSelectedLogin = false;
        this._hasAutoConfirmedTheme = false;
        this._hasAutoConfirmedTrust = false;
        this._hasAutoConfirmedTerms = false;
        this._hasInjectedOAuthCode = false;
        this._termsAccepted = false;
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
        if (this.onboardingWizard) {
            this.onboardingWizard.dismiss(0);
            this.onboardingWizard = null;
        }
        if (this.authCard) {
            this.authCard.dismiss();
            this.authCard = null;
        }
        if (this.floatingPill) {
            this.floatingPill.dismiss();
            this.floatingPill = null;
        }
        if (this.viewportEl) {
            this.viewportEl.classList.remove("has-input-pill");
        }
        if (this.accountBadgeEl && this.accountBadgeEl.parentNode) {
            this.accountBadgeEl.parentNode.removeChild(this.accountBadgeEl);
            this.accountBadgeEl = null;
        }
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
