/**
 * OnboardingWizard - Asistente Visual Multi-Paso Nativo para Google Antigravity
 * Conforme a SPEC-043: Material 3 Multi-Step Onboarding Wizard y Enmascaramiento Opaco de Terminal.
 */

export class OnboardingWizard {
    constructor(options = {}) {
        this.onAction = options.onAction || (() => {});
        this.onSelectAuthMethod = options.onSelectAuthMethod || ((m) => {
            if (m === "google") this.onAction("select_auth", "\r");
            else this.onAction("select_auth", "\x1b[B\r");
        });
        this.onOpenBrowser = options.onOpenBrowser || ((url) => this.onAction("open_browser", url));
        this.onSelectTheme = options.onSelectTheme || ((theme) => {
            let keySequence = "\r";
            if (theme === "terminal" || theme === 1) keySequence = "\x1b[B\r";
            else if (theme === "light" || theme === 2) keySequence = "\x1b[B\x1b[B\r";
            this.onAction("confirm_theme", keySequence);
        });
        this.onAcceptTerms = options.onAcceptTerms || (() => this.onAction("accept_terms", "\t\x1b[C\r"));

        this.containerEl = null;
        this.currentStep = "step-auth-method";
        this.authUrl = null;
        this.selectedThemeIndex = 0; // 0: Dark, 1: Terminal, 2: Light
    }

    mount(parentEl = document.body) {
        if (this.containerEl) return;

        this.containerEl = document.createElement("div");
        this.containerEl.className = "onboarding-wizard-overlay";
        this.containerEl.id = "onboarding-wizard";

        this.containerEl.innerHTML = `
            <div class="wizard-card" id="wizard-card">
                <!-- Paso 1: Selección de Método -->
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
                    <p class="wizard-subtitle">Inicia sesión para sincronizar tus proyectos y asistencia de desarrollo</p>
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
                        <button class="btn-secondary-auth" id="btn-auth-token">
                            <span>Ingresar Token / API Key</span>
                        </button>
                    </div>
                </div>

                <!-- Paso 2: Código de Autorización -->
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
                        <span class="auth-spinner" id="auth-spinner-icon">⏳</span>
                        <span id="auth-code-status">Esperando código del portapapeles...</span>
                    </div>
                    <button class="btn-open-browser" id="btn-open-browser">Abrir navegador de nuevo</button>
                </div>

                <!-- Paso 3: Selector de Tema -->
                <div class="wizard-step" id="step-theme-selector" style="display: none;">
                    <h2 class="wizard-title">Elige tu Tema de Interfaz</h2>
                    <p class="wizard-subtitle">Personaliza el contraste y la paleta de color para tu pantalla</p>
                    <div class="theme-options-grid">
                        <div class="theme-card active" data-theme-index="0" data-theme-name="dark">
                            <div class="theme-preview dark-theme"></div>
                            <span>Dark (Recomendado)</span>
                        </div>
                        <div class="theme-card" data-theme-index="1" data-theme-name="terminal">
                            <div class="theme-preview classic-theme"></div>
                            <span>Terminal Clásico</span>
                        </div>
                        <div class="theme-card" data-theme-index="2" data-theme-name="light">
                            <div class="theme-preview light-theme"></div>
                            <span>Light (Claro)</span>
                        </div>
                    </div>
                    <button class="btn-wizard-next" id="btn-confirm-theme">Continuar</button>
                </div>

                <!-- Paso 4: Términos y Telemetría -->
                <div class="wizard-step" id="step-terms-telemetry" style="display: none;">
                    <h2 class="wizard-title">Términos y Privacidad</h2>
                    <p class="wizard-subtitle">Revisa las condiciones del servicio y opciones de datos</p>
                    <div class="terms-consent-box">
                        <label class="consent-label">
                            <input type="checkbox" id="chk-telemetry" checked />
                            <span>Permitir datos de interacción para mejorar Antigravity (opcional)</span>
                        </label>
                    </div>
                    <button class="btn-start-coding" id="btn-start-coding">Comenzar a Programar 🚀</button>
                </div>
            </div>
        `;

        parentEl.appendChild(this.containerEl);
        this._bindEvents();
    }

    _bindEvents() {
        // Paso 1
        const btnGoogle = this.containerEl.querySelector("#btn-auth-google, #btn-wizard-google");
        btnGoogle?.addEventListener("click", () => {
            this.onSelectAuthMethod("google");
            this.onAction("select_auth", "\r");
        });

        const btnToken = this.containerEl.querySelector("#btn-auth-token, #btn-wizard-token");
        btnToken?.addEventListener("click", () => {
            this.onSelectAuthMethod("token");
            this.onAction("select_auth", "\x1b[B\r");
        });

        // Paso 2
        const btnBrowser = this.containerEl.querySelector("#btn-open-browser, #btn-wizard-reopen-browser");
        btnBrowser?.addEventListener("click", () => {
            if (this.authUrl) {
                this.onOpenBrowser(this.authUrl);
                this.onAction("open_browser", this.authUrl);
            }
        });

        // Paso 3
        const themeCards = this.containerEl.querySelectorAll(".theme-card");
        themeCards.forEach(card => {
            card.addEventListener("click", () => {
                themeCards.forEach(c => c.classList.remove("active"));
                card.classList.add("active");
                this.selectedThemeIndex = parseInt(card.dataset.themeIndex || "0", 10);
            });
        });

        const btnTheme = this.containerEl.querySelector("#btn-confirm-theme, #btn-wizard-theme-continue");
        btnTheme?.addEventListener("click", () => {
            const themes = ["dark", "terminal", "light"];
            const chosen = themes[this.selectedThemeIndex] || "dark";
            let keySequence = "\r";
            if (this.selectedThemeIndex === 1) keySequence = "\x1b[B\r";
            else if (this.selectedThemeIndex === 2) keySequence = "\x1b[B\x1b[B\r";
            this.onSelectTheme(chosen);
            this.onAction("confirm_theme", keySequence);
        });

        // Paso 4: WIZ-05 - Secuencia exacta de Inquirer: Tab -> Flecha Derecha -> Enter
        const btnTerms = this.containerEl.querySelector("#btn-start-coding, #btn-wizard-terms-done");
        btnTerms?.addEventListener("click", () => {
            this.onAcceptTerms();
            this.onAction("accept_terms", "\t\x1b[C\r");
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
    }

    setAuthenticating(msg = "Código detectado con éxito. Conectando cuenta...") {
        const statusEl = this.containerEl?.querySelector("#wizard-auth-status, #auth-code-status");
        if (statusEl) {
            statusEl.textContent = msg;
            statusEl.style.color = "#34a853";
        }
        const spinner = this.containerEl?.querySelector("#auth-spinner-icon, .auth-spinner");
        if (spinner) {
            spinner.textContent = "⏳";
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
