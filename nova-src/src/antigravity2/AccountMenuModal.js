/**
 * AccountMenuModal - Modal Material 3 Oficial para Google Antigravity Mobile
 * Conforme a SPEC-048: Menú de Cuenta, Cambio de Tema en Caliente,
 * Reinicio de Sesión y Cierre de Sesión con Cero Emojis y Cero Diálogos Primitivos.
 */

import { THEME_PRESETS } from "./OnboardingWizard.js";

export class AccountMenuModal {
    constructor(options = {}) {
        this.userEmail = options.userEmail || "shadrick1212@gmail.com";
        this.userTier = options.userTier || "Google AI Ultra";
        this.modelName = options.modelName || "Gemini 3.8 Flash (High)";
        this.workspacePath = options.workspacePath || "/home/studio/workspace";
        this.currentThemeId = options.currentThemeId || "dark";
        this.onSelectTheme = options.onSelectTheme || ((theme) => {});
        this.onRestartSession = options.onRestartSession || (() => {});
        this.onLogout = options.onLogout || (() => {});
        this.onDismiss = options.onDismiss || (() => {});

        this.containerEl = null;
    }

    mount(parentEl = document.body) {
        if (this.containerEl) return;

        this.containerEl = document.createElement("div");
        this.containerEl.className = "account-menu-overlay";
        this.containerEl.id = "account-menu-modal";

        const initialChar = (this.userEmail[0] || "U").toUpperCase();

        this.containerEl.innerHTML = `
            <div class="account-menu-backdrop" id="account-menu-backdrop"></div>
            <div class="account-menu-card" id="account-menu-card">
                <div class="account-card-header">
                    <div class="account-avatar">${initialChar}</div>
                    <div class="account-user-meta">
                        <span class="account-email">${this.userEmail}</span>
                        <span class="account-tier-chip">${this.userTier}</span>
                    </div>
                    <button class="account-btn-close" id="btn-account-close" aria-label="Cerrar">
                        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                            <line x1="18" y1="6" x2="6" y2="18"/>
                            <line x1="6" y1="6" x2="18" y2="18"/>
                        </svg>
                    </button>
                </div>

                <div class="account-card-body">
                    <div class="account-info-row">
                        <span class="info-label">Modelo Activo</span>
                        <span class="info-value">${this.modelName}</span>
                    </div>
                    <div class="account-info-row">
                        <span class="info-label">Espacio de Trabajo</span>
                        <span class="info-value font-mono">${this.workspacePath}</span>
                    </div>
                    <div class="account-info-row">
                        <span class="info-label">Runtime</span>
                        <span class="info-value">PRoot Linux ARM64</span>
                    </div>
                </div>

                <!-- Vista Principal de Acciones -->
                <div class="account-card-actions" id="account-actions-main">
                    <button class="account-action-btn" id="btn-account-theme">
                        <svg class="btn-action-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                            <circle cx="12" cy="12" r="10"/>
                            <circle cx="8" cy="10" r="1.5" fill="currentColor"/>
                            <circle cx="12" cy="8" r="1.5" fill="currentColor"/>
                            <circle cx="16" cy="10" r="1.5" fill="currentColor"/>
                        </svg>
                        <span>Cambiar Tema</span>
                    </button>
                    <button class="account-action-btn" id="btn-account-restart">
                        <svg class="btn-action-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M3 12a9 9 0 0 1 15.5-6.4L21 8M21 3v5h-5M21 12a9 9 0 0 1-15.5 6.4L3 16M3 21v-5h5"/>
                        </svg>
                        <span>Reiniciar Sesión</span>
                    </button>
                    <button class="account-action-btn btn-danger" id="btn-account-logout">
                        <svg class="btn-action-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>
                        </svg>
                        <span>Cerrar Sesión</span>
                    </button>
                </div>

                <!-- Sub-vista: Selector de 5 Temas en Caliente -->
                <div class="account-card-actions account-theme-grid" id="account-theme-selector" style="display: none;">
                    <div class="theme-grid-header">
                        <button class="btn-theme-back" id="btn-theme-back">
                            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M19 12H5M12 19l-7-7 7-7"/>
                            </svg>
                            <span>Volver</span>
                        </button>
                        <span class="theme-grid-title">Elegir Esquema</span>
                    </div>
                    <div class="theme-options-list">
                        ${THEME_PRESETS.map((t) => `
                            <button class="theme-option-item ${t.id === this.currentThemeId ? "active" : ""}" data-theme-id="${t.id}" data-theme-index="${t.agyIndex}">
                                <div class="theme-item-swatches" style="background: ${t.colors.bg};">
                                    <span class="item-swatch" style="background: ${t.colors.fg};"></span>
                                    <span class="item-swatch" style="background: ${t.colors.accent};"></span>
                                </div>
                                <span class="theme-item-label">${t.label}</span>
                            </button>
                        `).join("")}
                    </div>
                </div>
            </div>
        `;

        parentEl.appendChild(this.containerEl);
        this._bindEvents();
    }

    _bindEvents() {
        const backdrop = this.containerEl.querySelector("#account-menu-backdrop");
        const btnClose = this.containerEl.querySelector("#btn-account-close");
        const btnTheme = this.containerEl.querySelector("#btn-account-theme");
        const btnRestart = this.containerEl.querySelector("#btn-account-restart");
        const btnLogout = this.containerEl.querySelector("#btn-account-logout");
        const btnThemeBack = this.containerEl.querySelector("#btn-theme-back");
        const mainActions = this.containerEl.querySelector("#account-actions-main");
        const themeSelector = this.containerEl.querySelector("#account-theme-selector");

        backdrop?.addEventListener("click", () => this.dismiss(200));
        btnClose?.addEventListener("click", () => this.dismiss(200));

        btnTheme?.addEventListener("click", () => {
            if (mainActions && themeSelector) {
                mainActions.style.display = "none";
                themeSelector.style.display = "flex";
            }
        });

        btnThemeBack?.addEventListener("click", () => {
            if (mainActions && themeSelector) {
                themeSelector.style.display = "none";
                mainActions.style.display = "flex";
            }
        });

        const themeItems = this.containerEl.querySelectorAll(".theme-option-item");
        themeItems.forEach((item) => {
            item.addEventListener("click", () => {
                const themeId = item.dataset.themeId;
                const themeIndex = parseInt(item.dataset.themeIndex || "4", 10);
                const themePreset = THEME_PRESETS.find((p) => p.id === themeId);
                this.dismiss(150);
                this.onSelectTheme(themePreset || { id: themeId, agyIndex: themeIndex });
            });
        });

        btnRestart?.addEventListener("click", () => {
            this.dismiss(150);
            this.onRestartSession();
        });

        btnLogout?.addEventListener("click", () => {
            this.dismiss(150);
            this.onLogout();
        });
    }

    async dismiss(delayMs = 200) {
        if (!this.containerEl) return;
        this.containerEl.classList.add("fade-out");
        await new Promise((r) => setTimeout(r, delayMs));
        if (this.containerEl?.parentNode) {
            this.containerEl.parentNode.removeChild(this.containerEl);
        }
        this.containerEl = null;
        this.onDismiss();
    }
}

export default AccountMenuModal;
