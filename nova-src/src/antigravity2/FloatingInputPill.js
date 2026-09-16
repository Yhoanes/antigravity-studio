/**
 * FloatingInputPill - Barra de Entrada Flotante Minimalista para Google Antigravity
 * Conforme a SPEC-050: Píldora de Chat Ergonómica, Despacho PTY Directo y Cero Emojis
 * Conforme a SPEC-051 (PILL-09/PILL-10): Anclaje sobre el teclado virtual via visualViewport
 */

export class FloatingInputPill {
    constructor(options = {}) {
        this.onSend = options.onSend || (() => false);
        this.containerEl = null;
        this.inputEl = null;
        this.visible = false;
        this._viewportHandler = null;
    }

    mount(parentEl = document.body) {
        if (this.containerEl) return;

        this.containerEl = document.createElement("div");
        this.containerEl.className = "floating-input-pill hidden";
        this.containerEl.id = "floating-input-pill";

        this.containerEl.innerHTML = `
            <input type="text" placeholder="Pregúntale a Antigravity..." autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false" aria-label="Entrada de prompt">
            <button class="pill-send-btn" type="button" aria-label="Enviar prompt">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="#0b0f19" aria-hidden="true">
                    <path d="M3.4 20.4l17.45-7.48a1 1 0 0 0 0-1.84L3.4 3.6a.993.993 0 0 0-1.39.91L2 9.12c0 .5.37.93.87.99L17 12 2.87 13.88c-.5.07-.87.5-.87 1l.01 4.61c0 .71.73 1.2 1.39.91z"/>
                </svg>
            </button>
        `;

        this.inputEl = this.containerEl.querySelector("input");
        const sendBtn = this.containerEl.querySelector(".pill-send-btn");

        this.inputEl?.addEventListener("input", () => this._updateSendButton());

        this.inputEl?.addEventListener("keydown", (e) => {
            if (e.key === "Enter" && !e.isComposing && e.keyCode !== 229) {
                e.preventDefault();
                e.stopPropagation();
                this._handleSend();
            }
        });

        sendBtn?.addEventListener("click", (e) => {
            e.preventDefault();
            e.stopPropagation();
            this._handleSend();
        });

        parentEl.appendChild(this.containerEl);

        this._attachViewportTracking();
    }

    /**
     * SPEC-051 (PILL-09): Reposiciona la píldora por encima del teclado virtual de Android.
     * visualViewport expone la altura útil real cuando el IME ocupa pantalla; el desplazamiento
     * inferior resultante se aplica como offset nunca menor al margen base de 16px.
     */
    _attachViewportTracking() {
        if (!window.visualViewport) return;

        this._viewportHandler = () => {
            if (!this.containerEl) return;
            const vv = window.visualViewport;
            const bottomOffset = window.innerHeight - vv.height - vv.offsetTop;
            this.containerEl.style.bottom = `calc(${Math.max(bottomOffset, 16)}px + env(safe-area-inset-bottom, 0px))`;
        };

        window.visualViewport.addEventListener("resize", this._viewportHandler);
        window.visualViewport.addEventListener("scroll", this._viewportHandler);
    }

    _detachViewportTracking() {
        if (window.visualViewport && this._viewportHandler) {
            window.visualViewport.removeEventListener("resize", this._viewportHandler);
            window.visualViewport.removeEventListener("scroll", this._viewportHandler);
        }
        this._viewportHandler = null;
    }

    _handleSend() {
        const text = (this.inputEl?.value || "").trim();
        if (!text) return;
        const accepted = this.onSend(text);
        if (accepted) {
            if (this.inputEl) {
                this.inputEl.value = "";
                this.inputEl.blur();
            }
            this._updateSendButton();
        }
    }

    _updateSendButton() {
        const btn = this.containerEl?.querySelector(".pill-send-btn");
        if (btn) {
            const hasText = (this.inputEl?.value || "").trim().length > 0;
            btn.classList.toggle("visible", hasText);
        }
    }

    show() {
        if (!this.containerEl) return;
        this.containerEl.classList.remove("hidden");
        this.visible = true;
    }

    hide() {
        if (!this.containerEl) return;
        this.containerEl.classList.add("hidden");
        this.visible = false;
    }

    dismiss() {
        this._detachViewportTracking();
        if (!this.containerEl) return;
        if (this.containerEl.parentNode) {
            this.containerEl.parentNode.removeChild(this.containerEl);
        }
        this.containerEl = null;
        this.inputEl = null;
        this.visible = false;
    }
}

export default FloatingInputPill;
