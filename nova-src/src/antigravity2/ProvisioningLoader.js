/**
 * ProvisioningLoader.js
 * Google Antigravity 2.1.7: Overlay Visual de Aprovisionamiento Inicial (SPEC-035)
 * Criterios: AC-LOADER-01, AC-LOADER-02, AC-LOADER-03
 */
export class ProvisioningLoader {
    constructor() {
        this.overlayEl = null;
        this.statusTextEl = null;
        this.progressBarEl = null;
        this.percentageTextEl = null;
        this.currentPercent = 0;
    }

    mount(parentEl = document.body) {
        if (this.overlayEl) return;

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
            </div>
        `;

        parentEl.appendChild(this.overlayEl);

        this.statusTextEl = this.overlayEl.querySelector("#provisioning-status-text");
        this.progressBarEl = this.overlayEl.querySelector("#provisioning-progress-fill");
        this.percentageTextEl = this.overlayEl.querySelector("#provisioning-percentage-text");
    }

    update(percent, message) {
        const clamped = Math.max(0, Math.min(100, Math.round(percent)));
        this.currentPercent = clamped;

        if (this.progressBarEl) {
            this.progressBarEl.style.width = `${clamped}%`;
        }
        if (this.percentageTextEl) {
            this.percentageTextEl.textContent = `${clamped}%`;
        }
        if (message && this.statusTextEl) {
            this.statusTextEl.textContent = message;
        }
    }

    async finish() {
        this.update(100, "¡Listo! Iniciando sesión agéntica...");
        if (!this.overlayEl) return;

        return new Promise((resolve) => {
            setTimeout(() => {
                if (this.overlayEl) {
                    this.overlayEl.classList.add("fade-out");
                }
                setTimeout(() => {
                    if (this.overlayEl && this.overlayEl.parentNode) {
                        this.overlayEl.parentNode.removeChild(this.overlayEl);
                    }
                    this.overlayEl = null;
                    resolve();
                }, 300);
            }, 200);
        });
    }
}
