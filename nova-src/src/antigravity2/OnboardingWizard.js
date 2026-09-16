/**
 * OnboardingWizard - Asistente Visual Multi-Paso Nativo para Google Antigravity
 * Conforme a SPEC-043, SPEC-044, SPEC-045 & SPEC-046:
 * Transiciones Optimistas Fluidas, Despacho Único a PTY y Erradicación de Emojis
 */

export const THEME_PRESETS = [
    {
        id: "dark",
        name: "dark",
        agyIndex: 4,
        index: 4,
        label: "Dark (Recomendado)",
        desc: "Oscuro / Recomendado",
        isDefault: true,
        colors: { bg: "#1e1e2e", fg: "#cdd6f4", accent: "#89b4fa" }
    },
    {
        id: "tokyo-night",
        name: "tokyo-night",
        agyIndex: 7,
        index: 7,
        label: "Tokyo Night",
        desc: "Púrpura y Neón",
        isDefault: false,
        colors: { bg: "#1a1b26", fg: "#a9b1d6", accent: "#7aa2f7" }
    },
    {
        id: "solarized-dark",
        name: "solarized-dark",
        agyIndex: 5,
        index: 5,
        label: "Solarized Dark",
        desc: "Azul Petróleo",
        isDefault: false,
        colors: { bg: "#002b36", fg: "#839496", accent: "#268bd2" }
    },
    {
        id: "terminal",
        name: "terminal",
        agyIndex: 0,
        index: 0,
        label: "Terminal Clásico",
        desc: "Verde Matrix",
        isDefault: false,
        colors: { bg: "#000000", fg: "#22c55e", accent: "#16a34a" }
    },
    {
        id: "light",
        name: "light",
        agyIndex: 1,
        index: 1,
        label: "Light (Claro)",
        desc: "Claro Estándar",
        isDefault: false,
        colors: { bg: "#ffffff", fg: "#1e293b", accent: "#2563eb" }
    }
];

export class OnboardingWizard {
    constructor(options = {}) {
        this.onAction = options.onAction || (() => {});
        this.onSelectAuthMethod = options.onSelectAuthMethod || (() => this.onAction("select_auth", "\r"));
        this.onOpenBrowser = options.onOpenBrowser || ((url) => this.onAction("open_browser", url));
        this.onSelectTheme = options.onSelectTheme || ((agyIndex, seq) => {
            const idx = typeof agyIndex === "number" ? agyIndex : 4;
            const finalSeq = seq || ("\x1b[B".repeat(idx) + "\r");
            this.onAction("confirm_theme", finalSeq);
        });
        // SPEC-043 / SPEC-046: Secuencia de aceptación de términos
        this.onAcceptTerms = options.onAcceptTerms || (() => this.onAction("accept_terms", "\t\x1b[C\r"));

        this.containerEl = null;
        this.currentStep = "step-auth-method";
        this.authUrl = null;
        this.isWaitingForBrowser = false;
        this.isAwaitingBrowserLaunch = false;
        this.selectedThemeIndex = 4; // SPEC-045: Dark por defecto (Índice real 4 en agy)
    }

    _getThemeAnsiSequence(agyIndex) {
        const idx = typeof agyIndex === "number" ? agyIndex : 4;
        return "\x1b[B".repeat(idx) + "\r";
    }

    mount(parentEl = document.body) {
        if (this.containerEl) return;

        this.containerEl = document.createElement("div");
        this.containerEl.className = "onboarding-wizard-overlay";
        this.containerEl.id = "onboarding-wizard";

        this.containerEl.innerHTML = `
            <div class="wizard-card" id="wizard-card">
                <!-- Paso 1: Selección de Método (SPEC-045: Pure Google First-Party) -->
                <div class="wizard-step" id="step-auth-method">
                    <div class="wizard-logo-wrap">
                        <svg class="wizard-logo" viewBox="0 0 48 48" width="64" height="64">
                            <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                            <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                            <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                            <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
                        </svg>
                    </div>
                    <h2 class="wizard-title">Google Antigravity</h2>
                    <p class="wizard-subtitle">Inicia sesión con tu cuenta de Google para comenzar a desarrollar</p>
                    <div class="wizard-actions">
                        <button class="btn-primary-auth" id="btn-auth-google">
                            <svg class="btn-g-logo" viewBox="0 0 48 48" width="20" height="20">
                                <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                                <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                                <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                                <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
                            </svg>
                            <span>Continuar con Google</span>
                        </button>
                    </div>
                </div>

                <!-- Paso 2: Código de Autorización (SPEC-046: Spinner Material 3 sin Emojis) -->
                <div class="wizard-step" id="step-auth-code" style="display: none;">
                    <div class="wizard-logo-wrap">
                        <svg class="wizard-logo" viewBox="0 0 48 48" width="56" height="56">
                            <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                            <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
                            <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                            <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                        </svg>
                    </div>
                    <h2 class="wizard-title">Autorización de Cuenta</h2>
                    <p class="wizard-subtitle">Completa la autorización en Google Chrome y copia tu código de acceso</p>
                    <div class="auth-waiting-box" id="auth-waiting-box">
                        <span class="auth-spinner-dot" id="auth-spinner-icon"></span>
                        <span id="auth-code-status">Esperando código del portapapeles...</span>
                    </div>
                    <button class="btn-open-browser" id="btn-open-browser">Abrir navegador de nuevo</button>
                </div>

                <!-- Paso 3: Selector de Tema (SPEC-045: Cuadrícula Visual 5 Temas con Swatches) -->
                <div class="wizard-step" id="step-theme-selector" style="display: none;">
                    <h2 class="wizard-title">Elige tu Tema de Interfaz</h2>
                    <p class="wizard-subtitle">Personaliza el contraste y la paleta de color para tu pantalla</p>
                    <div class="theme-options-grid">
                        <div class="theme-card active selected" data-theme-index="4" data-theme-name="dark">
                            <div class="theme-swatches" style="background: #1e1e2e;">
                                <span class="swatch-circle swatch" style="background: #1e1e2e;"></span>
                                <span class="swatch-circle swatch" style="background: #cdd6f4;"></span>
                                <span class="swatch-circle swatch" style="background: #89b4fa;"></span>
                            </div>
                            <span class="theme-label">Dark (Recomendado)</span>
                        </div>
                        <div class="theme-card" data-theme-index="7" data-theme-name="tokyo-night">
                            <div class="theme-swatches" style="background: #1a1b26;">
                                <span class="swatch-circle swatch" style="background: #1a1b26;"></span>
                                <span class="swatch-circle swatch" style="background: #a9b1d6;"></span>
                                <span class="swatch-circle swatch" style="background: #7aa2f7;"></span>
                            </div>
                            <span class="theme-label">Tokyo Night</span>
                        </div>
                        <div class="theme-card" data-theme-index="5" data-theme-name="solarized-dark">
                            <div class="theme-swatches" style="background: #002b36;">
                                <span class="swatch-circle swatch" style="background: #002b36;"></span>
                                <span class="swatch-circle swatch" style="background: #839496;"></span>
                                <span class="swatch-circle swatch" style="background: #268bd2;"></span>
                            </div>
                            <span class="theme-label">Solarized Dark</span>
                        </div>
                        <div class="theme-card" data-theme-index="0" data-theme-name="terminal">
                            <div class="theme-swatches" style="background: #000000;">
                                <span class="swatch-circle swatch" style="background: #000000;"></span>
                                <span class="swatch-circle swatch" style="background: #22c55e;"></span>
                                <span class="swatch-circle swatch" style="background: #16a34a;"></span>
                            </div>
                            <span class="theme-label">Terminal Clásico</span>
                        </div>
                        <div class="theme-card" data-theme-index="1" data-theme-name="light">
                            <div class="theme-swatches" style="background: #ffffff; border: 1px solid #cbd5e1;">
                                <span class="swatch-circle swatch" style="background: #ffffff; border: 1px solid #cbd5e1;"></span>
                                <span class="swatch-circle swatch" style="background: #1e293b;"></span>
                                <span class="swatch-circle swatch" style="background: #2563eb;"></span>
                            </div>
                            <span class="theme-label">Light (Claro)</span>
                        </div>
                    </div>
                    <button class="btn-wizard-next" id="btn-confirm-theme">Continuar</button>
                </div>

                <!-- Paso 4: Términos y Telemetría (SPEC-046: Texto Plano sin Emojis) -->
                <div class="wizard-step" id="step-terms-telemetry" style="display: none;">
                    <h2 class="wizard-title">Términos y Privacidad</h2>
                    <p class="wizard-subtitle">Revisa las condiciones del servicio y opciones de datos</p>
                    <div class="terms-consent-box">
                        <label class="consent-label">
                            <input type="checkbox" id="chk-telemetry" checked />
                            <span>Permitir datos de interacción para mejorar Antigravity (opcional)</span>
                        </label>
                    </div>
                    <button class="btn-start-coding" id="btn-start-coding">Comenzar a programar</button>
                </div>
            </div>
        `;

        parentEl.appendChild(this.containerEl);
        this._bindEvents();
    }

    _bindEvents() {
        // Paso 1: Pure Google First-Party (SPEC-047: Lanzamiento instantáneo de Chrome en 0ms)
        const btnGoogle = this.containerEl.querySelector("#btn-auth-google, #btn-wizard-google");
        btnGoogle?.addEventListener("click", () => {
            if (btnGoogle.disabled) return;
            if (this.authUrl) {
                btnGoogle.disabled = true;
                this.onOpenBrowser(this.authUrl);
                this.goToStep("step-auth-code", this.authUrl);
            } else {
                this.isWaitingForBrowser = true;
                this.isAwaitingBrowserLaunch = true;
                btnGoogle.disabled = true;
                btnGoogle.classList.add("loading");
                btnGoogle.innerHTML = `<span class="auth-spinner-dot"></span> <span>Preparando enlace seguro...</span>`;
                this.onSelectAuthMethod();
            }
        });

        // Paso 2: Reapertura manual de navegador
        const btnBrowser = this.containerEl.querySelector("#btn-open-browser, #btn-wizard-reopen-browser");
        btnBrowser?.addEventListener("click", () => {
            if (this.authUrl) {
                this.onOpenBrowser(this.authUrl);
            }
        });

        // Paso 3: SPEC-045 & SPEC-046 Selector de Temas con Transición Optimista Inmediata
        const themeCards = this.containerEl.querySelectorAll(".theme-card");
        themeCards.forEach(card => {
            card.addEventListener("click", () => {
                themeCards.forEach(c => {
                    c.classList.remove("active");
                    c.classList.remove("selected");
                });
                card.classList.add("active");
                card.classList.add("selected");
                this.selectedThemeIndex = parseInt(card.dataset.themeIndex || "4", 10);
            });
        });

        const btnTheme = this.containerEl.querySelector("#btn-confirm-theme, #btn-wizard-theme-continue");
        btnTheme?.addEventListener("click", () => {
            if (btnTheme.disabled) return;
            btnTheme.disabled = true;
            btnTheme.style.opacity = "0.6";

            // AC-UX-02: Transición optimista inmediata al Paso 4 sin esperar respuesta PTY
            this.goToStep("step-terms-telemetry");

            // AC-UX-03: Despacho único sin duplicación
            const seq = this._getThemeAnsiSequence(this.selectedThemeIndex);
            this.onSelectTheme(this.selectedThemeIndex, seq);
        });

        // Paso 4: SPEC-046 & SPEC-049 Aceptación de Términos (Retención del velo opaco)
        const btnTerms = this.containerEl.querySelector("#btn-start-coding, #btn-wizard-terms-done");
        btnTerms?.addEventListener("click", () => {
            if (btnTerms.disabled) return;
            btnTerms.disabled = true;
            btnTerms.style.opacity = "0.7";
            btnTerms.innerHTML = `<span class="auth-spinner-dot"></span> Iniciando Google Antigravity...`;

            // SEAM-01: Disparar la secuencia espaciada hacia la PTY reteniendo el velo opaco
            this.onAcceptTerms();
        });
    }

    goToStep(stepInput, extraData = null) {
        if (!this.containerEl) return;

        let stepId = stepInput;
        if (typeof stepInput === "number") {
            const stepMap = {
                1: "step-auth-method",
                2: "step-auth-code",
                3: "step-theme-selector",
                4: "step-terms-telemetry"
            };
            stepId = stepMap[stepInput] || "step-auth-method";
        }

        const steps = this.containerEl.querySelectorAll(".wizard-step");
        steps.forEach(step => {
            step.style.display = "none";
        });

        const target = this.containerEl.querySelector(`#${stepId}`);
        if (target) {
            target.style.display = "flex";
            this.currentStep = stepId;
        }

        if ((stepId === "step-auth-code" || stepInput === 2) && extraData) {
            this.authUrl = extraData;
        }
    }

    setAuthUrl(url) {
        this.authUrl = url;
        if (this.isWaitingForBrowser || this.isAwaitingBrowserLaunch) {
            this.isWaitingForBrowser = false;
            this.isAwaitingBrowserLaunch = false;
            this.onOpenBrowser(url);
            this.goToStep("step-auth-code", url);
        }
    }

    setAuthenticating(msg = "Código detectado con éxito. Conectando cuenta...") {
        const statusEl = this.containerEl?.querySelector("#wizard-auth-status, #auth-code-status");
        if (statusEl) {
            statusEl.textContent = msg;
            statusEl.style.color = "#34a853";
        }
        // AC-UX-01: Erradicación de emojis: usar .auth-spinner-dot sin emojis
        const spinner = this.containerEl?.querySelector("#auth-spinner-icon, .auth-spinner, .auth-spinner-dot");
        if (spinner) {
            spinner.className = "auth-spinner-dot";
            spinner.textContent = "";
        }
    }

    setTokenInjected() {
        this.setAuthenticating("Código detectado con éxito. Conectando cuenta...");
    }

    async fadeOut(delayMs = 350) {
        return this.dismiss(delayMs);
    }

    async dismiss(delayMs = 350) {
        if (!this.containerEl) return;
        this.containerEl.classList.add("fade-out");
        await new Promise(resolve => setTimeout(resolve, delayMs));
        if (this.containerEl?.parentNode) {
            this.containerEl.parentNode.removeChild(this.containerEl);
        }
        this.containerEl = null;
    }
}

export default OnboardingWizard;
