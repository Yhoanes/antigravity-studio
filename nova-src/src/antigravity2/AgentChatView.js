/**
 * AgentChatView - Interfaz Nativa de Chat sobre Eventos Estructurados
 * Conforme a SPEC-054: Erradicación del Raspado de TUI
 *
 * Renderiza los eventos `stream-json` de `agy` como una lista de mensajes nativa:
 * burbujas, markdown en streaming, tarjetas de herramienta y contadores de token.
 * Cero Xterm.js (AC-VIEW-005). Cero emojis: solo SVG vectorial.
 */

import DOMPurify from "dompurify";
import markdownIt from "markdown-it";
import { AgentSession } from "./AgentSession.js";
import { FloatingInputPill } from "./FloatingInputPill.js";
import { STEP_TYPE } from "./AgentStreamClient.js";
import "./agent-chat.scss";

// `html: false` impide que el markdown del agente inyecte HTML crudo; DOMPurify
// es la segunda barrera sobre el resultado ya renderizado.
const md = markdownIt({
    html: false,
    linkify: true,
    breaks: true,
});

// AC-VIEW-004: los deltas reales traen enlaces `file:///home/studio/...` al
// workspace. DOMPurify los descartaría con su lista por defecto, así que se
// habilita `file:` explícitamente. Sigue vetando javascript:, data: y demás.
const SANITIZE_CONFIG = {
    ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto|tel|file):|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i,
};

function renderMarkdown(text) {
    return DOMPurify.sanitize(md.render(String(text ?? "")), SANITIZE_CONFIG);
}

function escapeHtml(text) {
    return String(text ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

function formatDuration(seconds) {
    if (typeof seconds !== "number") return "";
    if (seconds < 1) return `${Math.round(seconds * 1000)} ms`;
    if (seconds < 60) return `${seconds.toFixed(1)} s`;
    const mins = Math.floor(seconds / 60);
    return `${mins} min ${Math.round(seconds % 60)} s`;
}

function formatTokens(n) {
    if (typeof n !== "number") return "0";
    return n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n);
}

export class AgentChatView {
    constructor(options = {}) {
        this.onSwitchToTerminal = options.onSwitchToTerminal || (() => {});
        this.userEmail = options.userEmail || null;
        this.modelLabel = options.modelLabel || "3.8 Flash";

        this.session = options.session || new AgentSession({
            model: options.modelId || null,
            onInit: (caps) => this._handleInit(caps),
            onStep: (step) => this._upsertStep(step),
            onResult: (result) => this._handleResult(result),
            onError: (err) => this._appendSystemNotice(err.message, "error"),
            onStateChange: (state) => this._handleStateChange(state),
        });

        this.containerEl = null;
        this.listEl = null;
        this.pill = null;
        this._stepNodes = new Map();
        this._turnEl = null;
        this._pendingEl = null;
    }

    mount(parentEl = document.body) {
        if (this.containerEl) return;

        this.containerEl = document.createElement("div");
        this.containerEl.className = "agent-chat-container";
        this.containerEl.id = "agent-chat-container";

        this.containerEl.innerHTML = `
            <header class="agent-chat-top-bar">
                <div class="agent-chat-bar-left">
                    <svg class="agent-chat-logo" viewBox="0 0 48 48" width="22" height="22" aria-hidden="true">
                        <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                        <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                        <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                        <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
                    </svg>
                    <span class="agent-chat-title">Antigravity</span>
                    <span class="agent-chat-model-chip" id="agent-chat-model">${escapeHtml(this.modelLabel)}</span>
                </div>
                <button class="agent-chat-view-switch" id="agent-chat-to-terminal" type="button" aria-label="Cambiar a vista de terminal">
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                        <rect x="3" y="4" width="18" height="16" rx="2"/>
                        <path d="M7 9l3 3-3 3M13 15h4"/>
                    </svg>
                </button>
            </header>
            <main class="agent-chat-list" id="agent-chat-list" role="log" aria-live="polite"></main>
        `;

        this.listEl = this.containerEl.querySelector("#agent-chat-list");

        this.containerEl.querySelector("#agent-chat-to-terminal")
            ?.addEventListener("click", () => this.onSwitchToTerminal());

        parentEl.appendChild(this.containerEl);

        this.pill = new FloatingInputPill({
            onSend: (text) => this._handleSend(text),
        });
        this.pill.mount(this.containerEl);
        this.pill.show();

        this._renderEmptyState();
    }

    _renderEmptyState() {
        const empty = document.createElement("div");
        empty.className = "agent-chat-empty";
        empty.id = "agent-chat-empty";
        empty.innerHTML = `
            <span class="agent-chat-empty-title">Antigravity</span>
            <span class="agent-chat-empty-hint">Pregunta cualquier cosa sobre tu espacio de trabajo.</span>
        `;
        this.listEl?.appendChild(empty);
    }

    _clearEmptyState() {
        this.containerEl?.querySelector("#agent-chat-empty")?.remove();
    }

    _handleSend(text) {
        if (this.session.isRunning) return false;

        this._clearEmptyState();
        this._appendUserMessage(text);

        // Turno nuevo: los pasos del anterior ya no se actualizan.
        this._stepNodes = new Map();
        this._turnEl = document.createElement("div");
        this._turnEl.className = "agent-chat-turn";
        this.listEl?.appendChild(this._turnEl);

        this._showPending();
        this.session.send(text);
        return true;
    }

    /**
     * El evento `user_input` no acarrea el texto, así que la burbuja del usuario
     * se pinta aquí, donde sí se conoce.
     */
    _appendUserMessage(text) {
        const el = document.createElement("div");
        el.className = "agent-chat-msg agent-chat-msg-user";
        el.innerHTML = `<div class="agent-chat-bubble">${escapeHtml(text)}</div>`;
        this.listEl?.appendChild(el);
        this._scrollToBottom();
    }

    _showPending() {
        this._pendingEl = document.createElement("div");
        this._pendingEl.className = "agent-chat-pending";
        this._pendingEl.innerHTML = `
            <span class="agent-chat-dot"></span>
            <span class="agent-chat-dot"></span>
            <span class="agent-chat-dot"></span>
        `;
        this._turnEl?.appendChild(this._pendingEl);
        this._scrollToBottom();
    }

    _hidePending() {
        this._pendingEl?.remove();
        this._pendingEl = null;
    }

    _handleInit(caps) {
        // Las capacidades no se pintan todavía; quedan disponibles para la
        // especificación que exponga las herramientas.
        this.capabilities = caps;
    }

    _handleStateChange(state) {
        const running = state === "running";
        this.containerEl?.classList.toggle("is-running", running);
        // Sin esto el usuario escribia y pulsaba enviar sin que pasara nada.
        this.pill?.setBusy(running);
        if (!running) this._hidePending();
    }

    /**
     * AC-VIEW-001: la clave es `step_index`. Un índice repetido actualiza el
     * mismo nodo en lugar de añadir otro.
     */
    _upsertStep(step) {
        if (!this._turnEl) return;

        let node = this._stepNodes.get(step.index);
        if (!node) {
            node = document.createElement("div");
            node.className = "agent-chat-step";
            node.dataset.stepIndex = String(step.index);
            this._stepNodes.set(step.index, node);
            this._turnEl.appendChild(node);
        }

        switch (step.type) {
            case STEP_TYPE.AGENT_RESPONSE:
                this._renderAgentResponse(node, step);
                break;
            case STEP_TYPE.TOOL:
                this._renderTool(node, step);
                break;
            case STEP_TYPE.ERROR_MESSAGE:
                this._renderStepError(node, step);
                break;
            case STEP_TYPE.USER_INPUT:
                // Ya pintado por _appendUserMessage.
                this._dropStep(step.index, node);
                return;

            case STEP_TYPE.SYSTEM_MESSAGE:
                // agy emite este paso al abrir cada turno. Si no trae texto no
                // debe ocupar sitio: mostrarlo como "paso no reconocido" hacia
                // pensar que el mensaje habia fallado.
                if (!step.text) {
                    this._dropStep(step.index, node);
                    return;
                }
                node.className = "agent-chat-step agent-chat-notice agent-chat-notice-info";
                node.textContent = step.text;
                break;

            default:
                // Un step_type nuevo del protocolo no es un error del usuario.
                // Se registra en consola y, si trae texto, se pinta como mensaje.
                console.warn(`[SPEC-054] step_type no contemplado: ${step.type}`, step);
                if (!step.text) {
                    this._dropStep(step.index, node);
                    return;
                }
                node.className = "agent-chat-step agent-chat-msg agent-chat-msg-agent";
                node.innerHTML = `<div class="agent-chat-markdown">${renderMarkdown(step.text)}</div>`;
        }

        // El indicador de espera solo se retira cuando hay contenido real en
        // pantalla. Antes se ocultaba con el primer paso de metadatos, que llega
        // de inmediato, y la respuesta tardaba un minuto sin ninguna senal.
        const hasContent = (step.type === STEP_TYPE.AGENT_RESPONSE && step.text)
            || step.type === STEP_TYPE.TOOL;
        if (hasContent) {
            this._hidePending();
        } else {
            this._movePendingToEnd();
        }

        this._scrollToBottom();
    }

    _dropStep(index, node) {
        node.remove();
        this._stepNodes.delete(index);
        this._movePendingToEnd();
    }

    /** Mantiene el indicador al final del turno, por debajo de lo ya pintado. */
    _movePendingToEnd() {
        if (this._pendingEl && this._turnEl) {
            this._turnEl.appendChild(this._pendingEl);
        }
    }

    /**
     * AC-VIEW-003: un `agent_response` sin `text_delta` es razonamiento interno.
     * No debe producir una burbuja vacía.
     */
    _renderAgentResponse(node, step) {
        if (!step.text) {
            node.className = "agent-chat-step agent-chat-thinking";
            const tokens = step.usage?.thinking_tokens;
            node.innerHTML = `
                <svg class="agent-chat-thinking-icon" viewBox="0 0 24 24" width="14" height="14" fill="currentColor" aria-hidden="true">
                    <path d="M12 2a7 7 0 0 0-4 12.74V18a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1v-3.26A7 7 0 0 0 12 2zm-2 20h4a1 1 0 0 0 0-2h-4a1 1 0 0 0 0 2z"/>
                </svg>
                <span>Razonando${tokens ? ` · ${formatTokens(tokens)} tokens` : ""}${step.durationSeconds ? ` · ${formatDuration(step.durationSeconds)}` : ""}</span>
            `;
            return;
        }

        node.className = "agent-chat-step agent-chat-msg agent-chat-msg-agent";
        node.innerHTML = `<div class="agent-chat-markdown">${renderMarkdown(step.text)}</div>`;
        if (step.state === "ACTIVE") {
            node.classList.add("is-streaming");
        } else {
            node.classList.remove("is-streaming");
        }
    }

    /**
     * AC-VIEW-002: tarjeta plegable con nombre, parámetros y duración.
     *
     * SPEC-054 §3.1: el evento `DONE` NO trae el resultado de la herramienta, así
     * que la tarjeta muestra la invocación. El resultado llega narrado en el
     * `agent_response` siguiente.
     */
    _renderTool(node, step) {
        const running = step.state !== "DONE";
        node.className = `agent-chat-step agent-chat-tool${running ? " is-running" : ""}`;

        const params = step.parameters && Object.keys(step.parameters).length
            ? JSON.stringify(step.parameters, null, 2)
            : null;

        const meta = step.durationSeconds !== null ? formatDuration(step.durationSeconds) : "";

        node.innerHTML = `
            <button class="agent-chat-tool-head" type="button" aria-expanded="false">
                <svg class="agent-chat-tool-icon" viewBox="0 0 24 24" width="14" height="14" fill="currentColor" aria-hidden="true">
                    <path d="M22.7 19l-9.1-9.1a5.5 5.5 0 0 0-7.6-6.9l3.6 3.6-2.1 2.1-3.6-3.6a5.5 5.5 0 0 0 6.9 7.6l9.1 9.1a1 1 0 0 0 1.4 0l1.4-1.4a1 1 0 0 0 0-1.4z"/>
                </svg>
                <span class="agent-chat-tool-name">${escapeHtml(step.toolName || "herramienta")}</span>
                <span class="agent-chat-tool-meta">${escapeHtml(meta)}</span>
                ${params ? `<svg class="agent-chat-tool-chevron" viewBox="0 0 24 24" width="14" height="14" fill="currentColor" aria-hidden="true"><path d="M7 10l5 5 5-5z"/></svg>` : ""}
            </button>
            ${params ? `<pre class="agent-chat-tool-params" hidden>${escapeHtml(params)}</pre>` : ""}
        `;

        const head = node.querySelector(".agent-chat-tool-head");
        const body = node.querySelector(".agent-chat-tool-params");
        if (head && body) {
            head.addEventListener("click", () => {
                const open = !body.hidden;
                body.hidden = open;
                head.setAttribute("aria-expanded", String(!open));
                node.classList.toggle("is-open", !open);
            });
        }
    }

    _renderStepError(node, step) {
        node.className = "agent-chat-step agent-chat-step-error";
        node.innerHTML = `
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" aria-hidden="true">
                <path d="M12 2L1 21h22L12 2zm1 15h-2v2h2v-2zm0-8h-2v6h2V9z"/>
            </svg>
            <span>Paso con error${step.durationSeconds ? ` · ${formatDuration(step.durationSeconds)}` : ""}</span>
        `;
    }

    /**
     * SPEC-054 §3.1: `status: "ERROR"` puede traer un `response` completo y útil.
     * Se pinta el contenido y se señala el fallo aparte; descartarlo perdería
     * trabajo válido (AC-STREAM-004).
     */
    _handleResult(result) {
        this._hidePending();

        // Si ningún paso pintó texto, el `response` del resultado es lo único que hay.
        const rendered = Array.from(this._stepNodes.values())
            .some((n) => n.classList.contains("agent-chat-msg-agent"));
        if (!rendered && result.response) {
            const el = document.createElement("div");
            el.className = "agent-chat-step agent-chat-msg agent-chat-msg-agent";
            el.innerHTML = `<div class="agent-chat-markdown">${renderMarkdown(result.response)}</div>`;
            this._turnEl?.appendChild(el);
        }

        if (result.isError && result.error) {
            this._appendSystemNotice(result.error, "error");
        }

        if (result.usage) {
            const footer = document.createElement("div");
            footer.className = "agent-chat-turn-footer";
            const parts = [];
            if (result.durationSeconds) parts.push(formatDuration(result.durationSeconds));
            parts.push(`${formatTokens(result.usage.total_tokens)} tokens`);
            if (result.usage.thinking_tokens) {
                parts.push(`${formatTokens(result.usage.thinking_tokens)} razonando`);
            }
            footer.textContent = parts.join(" · ");
            this._turnEl?.appendChild(footer);
        }

        this._scrollToBottom();
    }

    _appendSystemNotice(message, kind = "info") {
        const el = document.createElement("div");
        el.className = `agent-chat-notice agent-chat-notice-${kind}`;
        el.textContent = message;
        (this._turnEl || this.listEl)?.appendChild(el);
        this._scrollToBottom();
    }

    setModelLabel(label) {
        const chip = this.containerEl?.querySelector("#agent-chat-model");
        if (chip) chip.textContent = label;
        this.modelLabel = label;
    }

    _scrollToBottom() {
        if (!this.listEl) return;
        this.listEl.scrollTop = this.listEl.scrollHeight;
    }

    show() {
        this.containerEl?.classList.remove("hidden");
        this.pill?.show();
    }

    hide() {
        this.containerEl?.classList.add("hidden");
        this.pill?.hide();
    }

    destroy() {
        this.session?.abort();
        this.pill?.dismiss();
        this.pill = null;
        this.containerEl?.remove();
        this.containerEl = null;
        this.listEl = null;
        this._stepNodes = new Map();
    }
}

export default AgentChatView;
