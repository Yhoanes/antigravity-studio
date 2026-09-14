/**
 * Google Antigravity 2.0 Mobile - ChatCanvas Component
 * Reactive conversational canvas with Thinking Accordions, Tool Cards & Plan Approvals
 * SPEC-021: AC-AG2-003, AC-AG2-004, AC-AG2-005
 */

import MarkdownIt from "markdown-it";
import agentBridge from "./AgentBridge";
import { DEFAULT_USER_PROFILE, GOOGLE_COLORS } from "./types";

export class ChatCanvas {
  constructor(options = {}) {
    this.container = null;
    this.messagesListEl = null;
    this.promptInputEl = null;
    this.sendBtnEl = null;
    this.userProfile = options.userProfile || DEFAULT_USER_PROFILE;
    this.md = new MarkdownIt({ html: false, linkify: true, breaks: true });
    this.activeThinkingAccordion = null;

    this.init();
  }

  init() {
    this.container = document.createElement("div");
    this.container.className = "ag-chat-canvas-pane";

    // 1. Messages scroll area
    this.messagesListEl = document.createElement("div");
    this.messagesListEl.className = "ag-chat-messages-scroll";
    this.container.appendChild(this.messagesListEl);

    // 2. Bottom prompt input bar
    const inputBar = this.createInputBar();
    this.container.appendChild(inputBar);

    // 3. Render initial welcome conversation
    this.renderWelcomeConversation();

    // 4. Hook AgentBridge listeners
    this.setupAgentBridgeListeners();
  }

  createInputBar() {
    const inputBar = document.createElement("div");
    inputBar.className = "ag-chat-input-bar";

    // Quick chips
    const chipsContainer = document.createElement("div");
    chipsContainer.className = "ag-quick-chips";
    const chips = [
      { label: "📋 Modo Plan", prompt: "Elabora un plan de arquitectura detallado paso a paso antes de modificar archivos." },
      { label: "⚡ Gemini 2.5 Pro", prompt: "" },
      { label: "🐞 Diagnosticar", prompt: "Revisa los logs del sistema e inspecciona si hay errores de compilación o runtime." },
      { label: "🌐 Test en Vivo", prompt: "Inicia el servidor de desarrollo local y valida los endpoints." },
    ];

    chips.forEach(c => {
      const chip = document.createElement("button");
      chip.className = "ag-chip";
      chip.textContent = c.label;
      chip.onclick = () => {
        if (c.prompt) {
          this.promptInputEl.value = c.prompt;
          this.promptInputEl.focus();
        }
      };
      chipsContainer.appendChild(chip);
    });
    inputBar.appendChild(chipsContainer);

    // Input row
    const inputRow = document.createElement("div");
    inputRow.className = "ag-input-row";

    // Attachment [+] button
    const attachBtn = document.createElement("button");
    attachBtn.className = "ag-input-action-btn";
    attachBtn.innerHTML = "<span>+</span>";
    attachBtn.title = "Adjuntar archivo o contexto";
    inputRow.appendChild(attachBtn);

    // Prompt textarea
    this.promptInputEl = document.createElement("textarea");
    this.promptInputEl.className = "ag-prompt-textarea";
    this.promptInputEl.placeholder = "Describe la tarea o problema a resolver...";
    this.promptInputEl.rows = 1;

    // Auto-resize textarea
    this.promptInputEl.addEventListener("input", () => {
      this.promptInputEl.style.height = "auto";
      this.promptInputEl.style.height = Math.min(this.promptInputEl.scrollHeight, 120) + "px";
    });

    // Enter to send (Shift+Enter for new line)
    this.promptInputEl.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        this.submitPrompt();
      }
    });
    inputRow.appendChild(this.promptInputEl);

    // Voice mic button
    const micBtn = document.createElement("button");
    micBtn.className = "ag-input-action-btn";
    micBtn.innerHTML = "<span>🎤</span>";
    micBtn.title = "Dictado por voz";
    micBtn.onclick = () => {
      this.promptInputEl.value += " [Dictado por voz activado] ";
      this.promptInputEl.focus();
    };
    inputRow.appendChild(micBtn);

    // Send button
    this.sendBtnEl = document.createElement("button");
    this.sendBtnEl.className = "ag-btn-send";
    this.sendBtnEl.innerHTML = "<span>➤</span>";
    this.sendBtnEl.title = "Enviar prompt al agente";
    this.sendBtnEl.onclick = () => this.submitPrompt();
    inputRow.appendChild(this.sendBtnEl);

    inputBar.appendChild(inputRow);
    return inputBar;
  }

  submitPrompt() {
    const text = this.promptInputEl.value.trim();
    if (!text) return;

    // Add user message bubble
    this.addUserMessage(text);
    this.promptInputEl.value = "";
    this.promptInputEl.style.height = "auto";

    // Send to agent via AgentBridge
    try {
      agentBridge.sendUserPrompt(text);
    } catch (e) {
      console.error("Failed to dispatch prompt:", e);
    }

    // Scroll to bottom
    this.scrollToBottom();
  }

  addUserMessage(text) {
    const msgEl = document.createElement("div");
    msgEl.className = "ag-chat-message user";

    const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    msgEl.innerHTML = `
      <div class="ag-msg-header">
        <span class="ag-sender-badge">👤 ${this.userProfile.email}</span>
        <span class="ag-msg-time">${timeStr}</span>
      </div>
      <div class="ag-msg-bubble">
        <p>${this.escapeHtml(text).replace(/\n/g, '<br>')}</p>
      </div>
    `;

    this.messagesListEl.appendChild(msgEl);
    this.scrollToBottom();
  }

  addAgentMessage(markdownContent) {
    const msgEl = document.createElement("div");
    msgEl.className = "ag-chat-message agent";

    const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const renderedHtml = this.md.render(markdownContent);

    msgEl.innerHTML = `
      <div class="ag-msg-header">
        <span class="ag-sender-badge" style="color: ${GOOGLE_COLORS.blue}">✦ Google Antigravity (Gemini 2.5 Pro)</span>
        <span class="ag-msg-time">${timeStr}</span>
      </div>
      <div class="ag-msg-bubble">
        ${renderedHtml}
      </div>
    `;

    this.messagesListEl.appendChild(msgEl);
    this.scrollToBottom();
    return msgEl;
  }

  /**
   * Render Thinking Accordion (SPEC-021: AC-AG2-003)
   */
  addThinkingAccordion(initialThoughts = [], durationSec = 3.2, isComplete = false) {
    const accEl = document.createElement("div");
    accEl.className = "ag-thinking-accordion";

    const header = document.createElement("div");
    header.className = "ag-thinking-header";
    header.innerHTML = `
      <div class="ag-thinking-title">
        <span class="ag-thinking-icon">▾</span>
        <span class="ag-thinking-label">Thinking... (Razonado durante ${durationSec}s)</span>
      </div>
      <span class="ag-chevron">▾</span>
    `;

    const body = document.createElement("div");
    body.className = "ag-thinking-body";
    body.innerHTML = initialThoughts.map(t => `<div>• ${this.escapeHtml(t)}</div>`).join("");

    header.onclick = () => {
      accEl.classList.toggle("open");
    };

    accEl.appendChild(header);
    accEl.appendChild(body);

    this.messagesListEl.appendChild(accEl);
    this.activeThinkingAccordion = { element: accEl, body, header };
    this.scrollToBottom();
    return accEl;
  }

  /**
   * Render Tool Execution Card (SPEC-021: AC-AG2-004)
   */
  addToolCard(toolName, args, status = "SUCCESS", diffContent = null) {
    const card = document.createElement("div");
    card.className = "ag-tool-card";

    let icon = "⚙️";
    if (toolName === "write_to_file") icon = "📄";
    if (toolName === "run_command") icon = "💻";

    let bodyContent = `<div class="ag-tool-args"><code>${this.escapeHtml(String(args))}</code></div>`;

    if (diffContent) {
      const lines = diffContent.split("\n");
      bodyContent = lines.map(line => {
        let cls = "normal";
        if (line.startsWith("+")) cls = "add";
        if (line.startsWith("-")) cls = "del";
        return `<div class="ag-diff-line ${cls}">${this.escapeHtml(line)}</div>`;
      }).join("");
    }

    card.innerHTML = `
      <div class="ag-tool-header">
        <div class="ag-tool-meta">
          <span>${icon}</span>
          <span>HERRAMIENTA: ${this.escapeHtml(toolName)}</span>
        </div>
        <span class="ag-tool-status ${status}">${status}</span>
      </div>
      <div class="ag-tool-body">
        ${bodyContent}
      </div>
    `;

    this.messagesListEl.appendChild(card);
    this.scrollToBottom();
    return card;
  }

  /**
   * Render Plan Approval Card with Touch Button (SPEC-021: AC-AG2-005)
   */
  addPlanApprovalCard(title, steps = [], affectedFiles = []) {
    const card = document.createElement("div");
    card.className = "ag-plan-approval-card";

    const stepsHtml = steps.map((s, idx) => `
      <div class="ag-plan-step">
        <input type="checkbox" id="step-${idx}" ${s.completed ? 'checked' : ''} />
        <label for="step-${idx}">${this.escapeHtml(s.description)}</label>
      </div>
    `).join("");

    const filesHtml = affectedFiles.length ? `
      <div style="font-size: 0.8rem; color: var(--ag-text-secondary); margin-bottom: 12px;">
        Archivos afectados: ${affectedFiles.map(f => `<code>${this.escapeHtml(f)}</code>`).join(", ")}
      </div>
    ` : "";

    card.innerHTML = `
      <div class="ag-plan-header">
        <span>📋</span>
        <span>PLAN DE ARQUITECTURA PROPUESTO</span>
      </div>
      <div class="ag-plan-title">${this.escapeHtml(title)}</div>
      <div class="ag-plan-steps">
        ${stepsHtml}
      </div>
      ${filesHtml}
      <div class="ag-plan-actions">
        <button class="ag-btn-approve" id="btn-approve-plan">
          <span>✓</span>
          <span>Aprobar y Ejecutar</span>
        </button>
        <button class="ag-btn-adjust" id="btn-adjust-plan">
          <span>✎ Solicitar Ajustes</span>
        </button>
      </div>
    `;

    // Button [ ✓ Aprobar y Ejecutar ] sends approval to AgentBridge
    const approveBtn = card.querySelector("#btn-approve-plan");
    approveBtn.onclick = () => {
      approveBtn.disabled = true;
      approveBtn.innerHTML = "<span>✓ Plan Aprobado</span>";
      approveBtn.style.opacity = "0.7";
      agentBridge.approvePlan();
    };

    const adjustBtn = card.querySelector("#btn-adjust-plan");
    adjustBtn.onclick = () => {
      this.promptInputEl.value = "Por favor realiza el siguiente ajuste en el plan de arquitectura: ";
      this.promptInputEl.focus();
    };

    this.messagesListEl.appendChild(card);
    this.scrollToBottom();
    return card;
  }

  renderWelcomeConversation() {
    this.addAgentMessage(`### Bienvenido a Google Antigravity 2.0 Mobile
**Estación Agéntica Conversacional impulsada por Gemini 2.5 Pro y Ultra**.

- **Chat Canvas:** Razonamiento transparente paso a paso (*Thinking Process*).
- **Control Determinado:** Revisa y aprueba planes arquitectónicos con un solo toque \`[ ✓ Aprobar y Ejecutar ]\`.
- **Live Web Preview:** Visualización y pruebas interactivas en vivo a 144Hz.
- **Continuidad Total:** Sesiones persistentes vinculadas a tus proyectos en \`/sdcard/Projects\`.

¿Qué proyecto o funcionalidad construiremos hoy?`);

    // Add illustrative Thinking Accordion
    this.addThinkingAccordion([
      "Entorno PRoot Linux Ubuntu Noble ARM64 activo y verificado.",
      "Google Antigravity CLI (agy v1.2.2) enlazado con auto-continue flag (-c).",
      "Detector de servidores locales en escucha en puertos 3000, 5173, 8080.",
      "Listo para recibir especificaciones y redactar código."
    ], 1.8, true);
  }

  setupAgentBridgeListeners() {
    agentBridge.on('thinking', (data) => {
      if (!this.activeThinkingAccordion) {
        this.addThinkingAccordion([data.raw], 2.5);
      } else {
        const item = document.createElement("div");
        item.textContent = `• ${data.raw.substring(0, 100)}...`;
        this.activeThinkingAccordion.body.appendChild(item);
      }
    });

    agentBridge.on('toolCall', (tool) => {
      this.addToolCard(tool.toolName, tool.args, tool.status, tool.diff);
    });

    agentBridge.on('planApproval', (plan) => {
      this.addPlanApprovalCard(plan.title || "Implementación Requerida", [
        { id: "1", description: "Instalar dependencias y módulos requeridos", completed: true },
        { id: "2", description: "Crear componentes y servicios correspondientes", completed: false },
        { id: "3", description: "Verificar con suite de tests y comprobación local", completed: false }
      ], ["package.json", "src/auth.ts"]);
    });
  }

  scrollToBottom() {
    if (this.messagesListEl) {
      setTimeout(() => {
        this.messagesListEl.scrollTop = this.messagesListEl.scrollHeight;
      }, 50);
    }
  }

  escapeHtml(str) {
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  get el() {
    return this.container;
  }
}

export default ChatCanvas;
