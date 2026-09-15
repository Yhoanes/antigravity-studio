/**
 * ProvisioningLoader.js
 * Google Antigravity Mobile 2.1.8 (SPEC-036)
 * Loader visual dinámico con interpolación continua y log stream monolínea anti-NaN.
 */
export class ProvisioningLoader {
    constructor() {
        this.overlayEl = null;
        this.statusTextEl = null;
        this.progressBarEl = null;
        this.percentageTextEl = null;
        this.logStreamEl = null;

        this._targetPercent = 0;
        this._currentVisualPercent = 0;
        this._tickerTimer = null;
    }

    mount(parentEl = document.body) {
        if (this.overlayEl) return;

        // SPEC-037 (AC-DISMISS-03): Disipación Activa de Splash Screen
        const splash = document.getElementById("splash");
        if (splash) {
            splash.style.display = "none";
            splash.style.visibility = "hidden";
        }
        document.body.classList.remove("loading", "splash");

        this.overlayEl = document.createElement("div");
        this.overlayEl.className = "provisioning-loader-overlay";
        this.overlayEl.id = "provisioning-loader";

        this.overlayEl.innerHTML = `
            <div class="provisioning-card">
                <div class="provisioning-logo-wrap">
                    <img src="./logo.svg" alt="Google Antigravity" class="provisioning-logo" />
                </div>
                <h2 class="provisioning-title">Google Antigravity</h2>
                <p class="provisioning-subtitle" id="provisioning-status-text">Inicializando entorno agéntico...</p>
                <div class="provisioning-progress-track">
                    <div class="provisioning-progress-bar" id="provisioning-progress-fill"></div>
                </div>
                <span class="provisioning-percentage" id="provisioning-percentage-text">0%</span>
                <div class="provisioning-log-stream" id="provisioning-log-stream">
                    <span class="log-prefix">❯</span><span class="log-message">Iniciando verificación de componentes...</span>
                </div>
            </div>
        `;

        parentEl.appendChild(this.overlayEl);

        this.statusTextEl = this.overlayEl.querySelector("#provisioning-status-text");
        this.progressBarEl = this.overlayEl.querySelector("#provisioning-progress-fill");
        this.percentageTextEl = this.overlayEl.querySelector("#provisioning-percentage-text");
        this.logStreamEl = this.overlayEl.querySelector("#provisioning-log-stream");

        this._startTicker();
    }

    update(progressOrMsg, maybeMsg) {
        let numericPercent = null;
        let messageText = null;

        // 1. Discriminador Polimórfico de Parámetros
        if (typeof progressOrMsg === "number") {
            numericPercent = progressOrMsg;
            if (typeof maybeMsg === "string") {
                messageText = maybeMsg;
            }
        } else if (typeof progressOrMsg === "string") {
            messageText = progressOrMsg;
            if (typeof maybeMsg === "number") {
                numericPercent = maybeMsg;
            }
        }

        // 2. Normalización Segura Anti-NaN
        if (numericPercent !== null && Number.isFinite(numericPercent) && !isNaN(numericPercent)) {
            const clamped = Math.max(0, Math.min(100, numericPercent));
            this._targetPercent = clamped;
            this._startTicker();
        }

        // 3. Despacho a Log Stream y Subtítulo
        if (messageText) {
            this.log(messageText);
        }
    }

    log(msg) {
        if (!this.logStreamEl) return;
        const cleanMsg = String(msg)
            .replace(/^(\s*\[SISTEMA\]|\s*📦|\s*✅|\s*⬇️|\s*🚀)\s*/, "")
            .trim();

        const msgSpan = this.logStreamEl.querySelector(".log-message");
        if (msgSpan) {
            msgSpan.textContent = cleanMsg;
        } else {
            this.logStreamEl.textContent = cleanMsg;
        }

        if (this.statusTextEl && cleanMsg.length < 50) {
            this.statusTextEl.textContent = cleanMsg;
        }
    }

    _startTicker() {
        if (this._tickerTimer) return;

        this._tickerTimer = setInterval(() => {
            const delta = this._targetPercent - this._currentVisualPercent;

            if (Math.abs(delta) < 0.1) {
                this._currentVisualPercent = this._targetPercent;
                this._renderVisual();
                clearInterval(this._tickerTimer);
                this._tickerTimer = null;
                return;
            }

            // Interpolación cinemática continua con easing exponencial
            this._currentVisualPercent += delta * 0.12;
            this._renderVisual();
        }, 16); // ~60fps - 144Hz compatible
    }

    _renderVisual() {
        const rounded = Math.round(this._currentVisualPercent);
        if (this.progressBarEl) {
            this.progressBarEl.style.width = `${this._currentVisualPercent.toFixed(1)}%`;
        }
        if (this.percentageTextEl) {
            this.percentageTextEl.textContent = `${rounded}%`;
        }
    }

    async completeAndFadeOut(delayMs = 250) {
        return this.finish(delayMs);
    }

    async finish(delayMs = 250) {
        this.update(100, "¡Listo! Iniciando sesión agéntica...");
        if (!this.overlayEl) return;

        return new Promise((resolve) => {
            setTimeout(() => {
                if (this.overlayEl) {
                    this.overlayEl.classList.add("fade-out");
                }
                setTimeout(() => {
                    if (this._tickerTimer) {
                        clearInterval(this._tickerTimer);
                        this._tickerTimer = null;
                    }
                    if (this.overlayEl && this.overlayEl.parentNode) {
                        this.overlayEl.parentNode.removeChild(this.overlayEl);
                    }
                    this.overlayEl = null;
                    resolve();
                }, 300);
            }, delayMs);
        });
    }
}
