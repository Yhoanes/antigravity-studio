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
    this.currentAgentMessageEl = null;
    this.currentAgentRawText = "";
    this.welcomeHeroEl = null;
    this.setupCardEl = null;

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

    // 3. Render initial Clean Welcome Hero (SPEC-023: CleanHeroContract)
    this.renderWelcomeHero();

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

    // Retirar Hero de Bienvenida con animacion suave si esta presente
    this.dismissWelcomeHero();

    // Reset current agent streaming buffer
    this.currentAgentMessageEl = null;
    this.currentAgentRawText = "";

    // Add user message bubble (con indicador de espera si aun no esta conectado)
    const isPending = !agentBridge.isConnected;
    this.addUserMessage(text, isPending);
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

  createAgentMessageContainer() {
    const msgEl = document.createElement("div");
    msgEl.className = "ag-chat-message agent";

    const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    msgEl.innerHTML = `
      <div class="ag-msg-header">
        <span class="ag-sender-badge" style="color: ${GOOGLE_COLORS.blue}">✦ Google Antigravity (Gemini 2.5 Pro)</span>
        <span class="ag-msg-time">${timeStr}</span>
      </div>
      <div class="ag-msg-bubble"></div>
    `;

    this.messagesListEl.appendChild(msgEl);
    this.scrollToBottom();
    return msgEl;
  }

  addUserMessage(text, isPending = false) {
    const msgEl = document.createElement("div");
    msgEl.className = "ag-chat-message user";

    const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const userLabel = this.userProfile.email || this.userProfile.displayName || "Invitado";
    const pendingBadgeHtml = isPending
      ? `<span class="ag-msg-pending-badge" id="user-prompt-pending-badge">En espera de conexión...</span>`
      : "";

    msgEl.innerHTML = `
      <div class="ag-msg-header">
        <span class="ag-sender-badge">👤 ${this.escapeHtml(userLabel)}</span>
        ${pendingBadgeHtml}
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

  renderWelcomeHero() {
    this.messagesListEl.innerHTML = "";
    this.currentAgentMessageEl = null;
    this.currentAgentRawText = "";

    const hero = document.createElement("div");
    hero.className = "ag-welcome-hero";
    hero.id = "ag-welcome-hero";

    hero.innerHTML = `
      <div class="ag-hero-prism">
        <svg viewBox="0 0 100 100" class="ag-prism-svg">
          <polygon points="50,15 85,80 15,80" fill="none" stroke="rgba(255,255,255,0.1)" stroke-width="2" />
          <path d="M50,15 L85,80" stroke="${GOOGLE_COLORS.yellow}" stroke-width="3.5" stroke-linecap="round" />
          <path d="M85,80 L15,80" stroke="${GOOGLE_COLORS.green}" stroke-width="3.5" stroke-linecap="round" />
          <path d="M15,80 L50,15" stroke="${GOOGLE_COLORS.blue}" stroke-width="3.5" stroke-linecap="round" />
          <circle cx="50" cy="15" r="4.5" fill="${GOOGLE_COLORS.red}" />
          <circle cx="85" cy="80" r="4.5" fill="${GOOGLE_COLORS.yellow}" />
          <circle cx="15" cy="80" r="4.5" fill="${GOOGLE_COLORS.blue}" />
          <circle cx="50" cy="52" r="3.5" fill="#ffffff" class="ag-prism-center-sparkle" />
        </svg>
      </div>
      <h1 class="ag-hero-title">Google Antigravity</h1>
      <p class="ag-hero-subtitle">¿En qué puedo ayudarte hoy?</p>
      
      <div class="ag-hero-starters">
        <div class="ag-starter-card" data-prompt="Elabora un plan de arquitectura detallado paso a paso antes de modificar archivos.">
          <div class="ag-starter-icon">📋</div>
          <div class="ag-starter-title">Modo Plan</div>
          <div class="ag-starter-desc">Diseña la arquitectura antes de codificar.</div>
        </div>
        <div class="ag-starter-card" data-prompt="Crea una aplicación web moderna utilizando HTML, CSS y JavaScript con servidor local en Vite.">
          <div class="ag-starter-icon">⚡</div>
          <div class="ag-starter-title">Crear App Web</div>
          <div class="ag-starter-desc">Frontend reactivo con Vite y componentes.</div>
        </div>
        <div class="ag-starter-card" data-prompt="Revisa los logs del sistema e inspecciona si hay errores de compilación o runtime.">
          <div class="ag-starter-icon">🐞</div>
          <div class="ag-starter-title">Diagnosticar</div>
          <div class="ag-starter-desc">Inspecciona logs y errores del sistema.</div>
        </div>
      </div>
    `;

    hero.querySelectorAll(".ag-starter-card").forEach((card) => {
      card.onclick = () => {
        const prompt = card.getAttribute("data-prompt");
        if (prompt && this.promptInputEl) {
          this.promptInputEl.value = prompt;
          this.promptInputEl.focus();
        }
      };
    });

    this.messagesListEl.appendChild(hero);
    this.welcomeHeroEl = hero;
  }

  dismissWelcomeHero() {
    if (!this.welcomeHeroEl) return;
    const hero = this.welcomeHeroEl;
    this.welcomeHeroEl = null;
    hero.style.transition = "opacity 180ms ease, transform 180ms ease";
    hero.style.opacity = "0";
    hero.style.transform = "translateY(-8px)";
    setTimeout(() => {
      if (hero.parentNode) {
        hero.parentNode.removeChild(hero);
      }
    }, 180);
  }

  renderSetupCard(isAuto = false) {
    if (this.setupCardEl) return;

    const card = document.createElement("div");
    card.className = "ag-setup-card";
    card.id = "ag-setup-card";

    const buttonHtml = isAuto 
      ? `<button class="ag-btn-start-setup" id="btn-start-setup" disabled style="opacity: 0.85;">
           <span>✦ Auto-inicializando entorno ARM64... (~3s)</span>
         </button>`
      : `<button class="ag-btn-start-setup" id="btn-start-setup">
           <span>Inicializar Entorno Agéntico</span>
         </button>`;

    card.innerHTML = `
      <div class="ag-setup-header">
        <span class="ag-setup-icon">✦</span>
        <span class="ag-setup-title">Configuración del Entorno de IA</span>
      </div>
      <p class="ag-setup-desc">
        Para ejecutar Google Antigravity en este dispositivo es necesario inicializar el subsistema Linux Ubuntu ARM64 y el motor agéntico.
      </p>
      <div class="ag-setup-progress-bar">
        <div class="ag-setup-progress-fill" id="setup-progress-fill" style="width: 25%; background: linear-gradient(90deg, #4285F4 0%, #34A853 50%, #4285F4 100%); transition: width 0.3s ease;"></div>
      </div>
      <div class="ag-setup-log" id="setup-log-view">📦 Preparando entorno de desarrollo local...</div>
      ${buttonHtml}
    `;

    const startBtn = card.querySelector("#btn-start-setup");
    const progressFill = card.querySelector("#setup-progress-fill");
    const logView = card.querySelector("#setup-log-view");

    startBtn.onclick = async () => {
      startBtn.disabled = true;
      startBtn.innerHTML = "<span>Instalando entorno...</span>";
      startBtn.style.opacity = "0.7";

      try {
        await agentBridge.installRuntime(
          (progress) => {
            if (progressFill) progressFill.style.width = `${progress}%`;
          },
          (logLine) => {
            if (logView) {
              logView.textContent = String(logLine).slice(-120);
            }
          }
        );
      } catch (err) {
        startBtn.disabled = false;
        startBtn.innerHTML = "<span>Reintentar Instalación</span>";
        startBtn.style.opacity = "1";
        if (logView) logView.textContent = `Error: ${err.message || err}`;
      }
    };

    this.messagesListEl.appendChild(card);
    this.setupCardEl = card;
    this.scrollToBottom();
  }

  dismissSetupCard() {
    if (!this.setupCardEl) return;
    const card = this.setupCardEl;
    this.setupCardEl = null;
    card.style.transition = "opacity 180ms ease";
    card.style.opacity = "0";
    setTimeout(() => {
      if (card.parentNode) {
        card.parentNode.removeChild(card);
      }
    }, 180);
  }

  setupAgentBridgeListeners() {
    // Streaming de respuestas del agente en tiempo real (SPEC-022 §5.1, AC-REP-007)
    agentBridge.on('message', ({ raw, text }) => {
      if (!text || !text.trim()) return;

      // Si no hay una burbuja activa de agente para este turno, crear una nueva
      if (!this.currentAgentMessageEl) {
        this.currentAgentMessageEl = this.createAgentMessageContainer();
        this.currentAgentRawText = "";
      }

      this.currentAgentRawText += text;

      // Renderizar Markdown incremental
      const renderedHtml = this.md.render(this.currentAgentRawText);
      const bubbleBody = this.currentAgentMessageEl.querySelector(".ag-msg-bubble");
      if (bubbleBody) {
        bubbleBody.innerHTML = renderedHtml;
      }

      this.scrollToBottom();
    });

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

    // Despacho de cola de prompts diferida (SPEC-023 §5, AC-CLN-010)
    agentBridge.on('promptDispatched', () => {
      const badge = this.messagesListEl.querySelector("#user-prompt-pending-badge");
      if (badge) {
        badge.textContent = "✓ Enviado";
        badge.style.borderColor = "rgba(52, 168, 83, 0.4)";
        badge.style.color = "#34a853";
        setTimeout(() => {
          if (badge.parentNode) badge.parentNode.removeChild(badge);
        }, 1200);
      }
    });

    // Logs en tiempo real de instalación local (SPEC-024 §3)
    agentBridge.on('installLog', ({ message }) => {
      const logView = this.messagesListEl.querySelector("#setup-log-view");
      if (logView) {
        logView.textContent = String(message).slice(-120);
      }
      const fill = this.messagesListEl.querySelector("#setup-progress-fill");
      if (fill) {
        if (message.includes("Extrayendo") || message.includes("Extracting") || message.includes("Descomprimiendo")) fill.style.width = "50%";
        else if (message.includes("Installing") || message.includes("Instalando") || message.includes("directorios")) fill.style.width = "80%";
        else if (message.includes("éxito") || message.includes("completed") || message.includes("completada") || message.includes("inicializado")) fill.style.width = "100%";
      }
    });

    // Estado del runtime (SPEC-023 §4.2, SPEC-024 §3: ZeroClickBootContract)
    agentBridge.on('statusChange', ({ status }) => {
      if (status === 'INITIALIZING') {
        this.renderSetupCard(true);
      } else if (status === 'SETUP') {
        this.renderSetupCard(false);
      } else if (status === 'READY') {
        // Determinar si la pantalla estaba limpia (solo la tarjeta de setup o vacía)
        const hadOnlySetupCard = this.messagesListEl.children.length === 0 || 
          (this.messagesListEl.children.length === 1 && this.setupCardEl);

        // Retirar la tarjeta con animación suave inmediata
        this.dismissSetupCard();

        // Desplegar el WelcomeHero de inicio si no hay mensajes de usuario en cola
        if (!this.welcomeHeroEl && hadOnlySetupCard) {
          this.renderWelcomeHero();
        }
      }
    });

    // Detección interactiva de servidor web local (SPEC-022 §5.2, AC-REP-008)
    agentBridge.on('serverDetected', ({ port, url }) => {
      const pill = document.createElement("div");
      pill.className = "ag-server-detected-pill";
      pill.innerHTML = `
        <span class="ag-pill-icon">🌐</span>
        <span class="ag-pill-text">Servidor web activo en <strong>${url}</strong></span>
        <button class="ag-pill-btn-open">Abrir Preview</button>
      `;

      pill.querySelector(".ag-pill-btn-open").onclick = () => {
        if (window.antigravityApp?.livePreview) {
          window.antigravityApp.livePreview.setUrl(url);
          if (window.antigravityApp.showPreviewTab) {
            window.antigravityApp.showPreviewTab();
          } else if (window.antigravityApp.setActiveTab) {
            window.antigravityApp.setActiveTab("preview");
          }
        }
      };

      this.messagesListEl.appendChild(pill);
      this.scrollToBottom();
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
