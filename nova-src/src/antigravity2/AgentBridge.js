/**
 * Google Antigravity 2.0 Mobile - AgentBridge Service
 * Manages WebSocket PTY communication with PRoot Ubuntu Noble ARM64 (agy CLI)
 * and Direct Gemini REST/SSE Engine (SPEC-027: DualEngineContract)
 */

export const ENGINE_MODES = {
  AUTO: "AUTO",             // Detección automática (OAuth PTY -> Gemini Direct -> Unauth Info)
  PTY_OFFICIAL: "PTY",      // Forzar motor PTY oficial de Linux (agy)
  GEMINI_DIRECT: "DIRECT",  // Forzar conexión REST/SSE directa con Gemini API
};

export class AgentBridge {
  constructor(options = {}) {
    this.port = options.port || 8767;
    this.pid = null;
    this.websocket = null;
    this.isConnected = false;
    this.isConnecting = false;
    this.autoContinue = options.autoContinue !== false;
    this.activeChatId = options.activeChatId || null;
    this.activeProject = options.activeProject || "workspace";
    this.pendingQueue = [];

    // Dual Engine Configuration (SPEC-027)
    this.engineMode = options.engineMode || ENGINE_MODES.AUTO;
    this.apiKey = options.apiKey || (typeof localStorage !== "undefined" ? localStorage.getItem("ag_gemini_api_key") : null);

    this.listeners = {
      message: [],
      thinking: [],
      toolCall: [],
      planApproval: [],
      statusChange: [],
      serverDetected: [],
      promptQueued: [],
      promptDispatched: [],
      authSuccess: [],
      installLog: [],
      unauthenticatedPrompt: [],
    };

    this.detectedPorts = new Set();
    this.portScanInterval = null;
  }

  setEngineMode(mode) {
    if (Object.values(ENGINE_MODES).includes(mode)) {
      this.engineMode = mode;
      console.log(`AgentBridge EngineMode cambiado a: ${mode}`);
    }
  }

  setApiKey(key) {
    this.apiKey = key;
    try {
      if (typeof localStorage !== "undefined") {
        localStorage.setItem("ag_gemini_api_key", key);
      }
    } catch (e) {}
  }

  checkIsAuthenticated() {
    try {
      if (typeof localStorage !== "undefined") {
        const stored = localStorage.getItem("ag_user_profile");
        if (stored) {
          const parsed = JSON.parse(stored);
          return !!parsed.isAuthenticated;
        }
      }
    } catch (e) {}
    return false;
  }

  on(event, callback) {
    if (this.listeners[event]) {
      this.listeners[event].push(callback);
    }
  }

  emit(event, data) {
    if (this.listeners[event]) {
      for (const cb of this.listeners[event]) {
        try {
          cb(data);
        } catch (e) {
          console.error(`Error in listener for ${event}:`, e);
        }
      }
    }
  }

  /**
   * Initializes and connects to AXS PTY daemon, executing agy -c
   */
  async connect() {
    if (this.isConnected || this.isConnecting) return;
    this.isConnecting = true;
    this.emit('statusChange', { status: 'CONNECTING', type: 'thinking' });

    try {
      // 1. Verify if Linux runtime environment is installed (SPEC-024 §3: ZeroClickBootContract, SPEC-025: ZeroLockTransitionContract)
      if (typeof Terminal !== "undefined" && typeof Terminal.isInstalled === "function") {
        const isInstalled = await Terminal.isInstalled();
        if (!isInstalled) {
          console.log("Auto-Provisioning: Entorno no instalado. Iniciando extracción local inmediata...");
          this.emit('statusChange', { status: 'INITIALIZING', type: 'thinking' });

          const installed = await this.installRuntime();
          if (!installed) {
            throw new Error("No se pudo completar la extracción e instalación del subsistema Linux");
          }

          console.log("Auto-Provisioning completado con éxito. Continuando secuencia hacia AXS daemon...");
          this.emit('statusChange', { status: 'CONNECTING', type: 'thinking' });
          // SPEC-025: Continuación directa secuencial sin return
        }
      }

      // 2. Check/Start AXS daemon if on Android with Terminal plugin
      if (typeof Terminal !== "undefined") {
        if (typeof Terminal.isAxsRunning === "function" && !(await Terminal.isAxsRunning())) {
          await Terminal.startAxs(false, () => {}, console.error, false);
        }
      }

      // 3. Wait for AXS ready
      await this.waitForServerReady();

      // 4. Create Terminal Session
      this.pid = await this.createSession();

      // 5. Open WebSocket
      const wsUrl = `ws://127.0.0.1:${this.port}/terminals/${this.pid}`;
      await this.openWebSocket(wsUrl);

      this.isConnected = true;
      this.isConnecting = false;
      this.emit('statusChange', { status: 'READY', type: 'ready' });

      // 6. Start agy with auto-continue flag: agy -c and flush pending prompts
      setTimeout(() => {
        this.launchAgy();
        this.flushPendingQueue();
      }, 300);

      // 7. Start background local port detection
      this.startPortScanner();

    } catch (err) {
      console.error("AgentBridge connection failed:", err);
      this.isConnecting = false;
      this.isConnected = false;
      this.emit('statusChange', { status: 'ERROR', type: 'error', error: err.message });
      throw err;
    }
  }

  async waitForServerReady(maxAttempts = 25, delayMs = 400) {
    const statusUrl = `http://127.0.0.1:${this.port}/status`;

    for (let i = 0; i < maxAttempts; i++) {
      try {
        let isOk = false;
        if (window.cordova?.plugin?.http) {
          const response = await new Promise((resolve, reject) => {
            cordova.plugin.http.sendRequest(
              statusUrl,
              { method: "GET", responseType: "text" },
              resolve,
              reject
            );
          });
          if (response.status >= 200 && response.status < 300 && response.data?.trim() === "OK") {
            isOk = true;
          }
        } else {
          // Fallback para entornos de desarrollo en navegador de escritorio
          const res = await window.fetch(statusUrl).catch(() => null);
          if (res && res.ok) isOk = true;
        }

        if (isOk) return true;
      } catch (e) {
        // Ignorar fallas temporales mientras el servidor AXS inicializa el puerto
      }
      await new Promise((r) => setTimeout(r, delayMs));
    }
    return true; // Continuar de forma resiliente
  }

  async createSession() {
    const terminalsUrl = `http://127.0.0.1:${this.port}/terminals`;
    const requestBody = { cols: 100, rows: 35 };

    if (window.cordova?.plugin?.http) {
      const response = await new Promise((resolve, reject) => {
        cordova.plugin.http.sendRequest(
          terminalsUrl,
          {
            method: "POST",
            responseType: "text",
            serializer: "json",
            data: requestBody,
          },
          resolve,
          (err) => reject(new Error(err.error || `HTTP ${err.status || 'error'}`))
        );
      });

      if (response.status >= 200 && response.status < 300) {
        this.pid = response.data.trim();
        return this.pid;
      }
      throw new Error(`Fallo creando terminal: HTTP ${response.status}`);
    } else {
      // Fallback de navegador
      const res = await window.fetch(terminalsUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requestBody),
      });
      if (!res.ok) throw new Error(`HTTP error ${res.status}`);
      this.pid = (await res.text()).trim();
      return this.pid;
    }
  }

  openWebSocket(url) {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(url);
      const timer = setTimeout(() => {
        if (ws.readyState !== WebSocket.OPEN) {
          ws.close();
          reject(new Error("WebSocket connection timeout"));
        }
      }, 6000);

      ws.onopen = () => {
        clearTimeout(timer);
        this.websocket = ws;
        resolve(ws);
      };

      ws.onmessage = (event) => {
        this.handlePtyOutput(event.data);
      };

      ws.onerror = (err) => {
        clearTimeout(timer);
        console.error("AgentBridge WebSocket error:", err);
      };

      ws.onclose = () => {
        this.isConnected = false;
        this.emit('statusChange', { status: 'DISCONNECTED', type: 'error' });
      };
    });
  }

  /**
   * Launch official agy command with auto-continue flag (SPEC-021: AC-AG2-009)
   */
  launchAgy() {
    if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) return;
    let cmd = "agy -c\r";
    if (this.activeChatId) {
      cmd = `agy -c --chat-id "${this.activeChatId}"\r`;
    }
    this.websocket.send(cmd);
  }

  /**
   * Dispatch a user prompt (SPEC-023 §5, SPEC-027 §2: DualEngineContract)
   * Supports Motor 1 (PTY agy), Motor 2 (Direct Gemini API), or Unauthenticated Info State
   * @param {string} promptText
   */
  sendUserPrompt(promptText) {
    if (!promptText || !promptText.trim()) return;

    // Evaluación del modo de motor (DualEngineContract - SPEC-027)
    const isAuth = this.checkIsAuthenticated();
    const effectiveApiKey = this.apiKey || (typeof localStorage !== "undefined" ? localStorage.getItem("ag_gemini_api_key") : null);

    // Motor 2 forzado
    if (this.engineMode === ENGINE_MODES.GEMINI_DIRECT) {
      this.streamGeminiDirect(promptText).catch((err) => {
        console.error("Error en streamGeminiDirect:", err);
      });
      return;
    }

    // Modo AUTO: si no hay sesión OAuth activa en la UI, pero hay clave de API (proporcionada por el usuario o en storage), usar Motor 2
    if (this.engineMode === ENGINE_MODES.AUTO && !isAuth && effectiveApiKey) {
      this.streamGeminiDirect(promptText).catch((err) => {
        console.error("Error en streamGeminiDirect (modo auto con API key):", err);
      });
      return;
    }

    // Modo AUTO: si no hay ni autenticación OAuth ni API key, emitir Estado Informativo No Autenticado
    if (!isAuth && !effectiveApiKey) {
      this.emit('unauthenticatedPrompt', { promptText });
      this.emit('message', {
        raw: "",
        text: `### ✦ Autenticación Requerida para Google Antigravity\n\n` +
              `Para comenzar a programar con **Gemini 2.5 Pro** y ejecutar herramientas agénticas en este dispositivo, selecciona un método de acceso:\n\n` +
              `* **Cuenta Google (Recomendado):** Vincula tu cuenta personal o corporativa mediante OAuth 2.0 para desbloquear cuotas completas y soporte de planes Google One AI Ultra.\n` +
              `* **Clave de API Directa:** Utiliza una API Key de Google AI Studio para desarrollo local inmediato.`
      });
      this.emit('statusChange', { status: 'READY', type: 'ready' });
      return;
    }

    // Motor 1: Oficial Google Antigravity PTY (Producción)
    if (!this.isConnected || !this.websocket || this.websocket.readyState !== WebSocket.OPEN) {
      console.warn("AgentBridge no está conectado aún a la PTY. Encolando prompt:", promptText);
      this.pendingQueue.push(promptText);
      this.emit('promptQueued', { text: promptText, queueLength: this.pendingQueue.length });

      // Si no está conectando, disparar reconexión automática
      if (!this.isConnecting) {
        this.connect().catch((e) => console.error("Error en auto-reconexión:", e));
      }
      return;
    }

    this.emit('statusChange', { status: 'THINKING', type: 'thinking' });
    this.websocket.send(`${promptText}\r`);
  }

  /**
   * Streaming Server-Sent Events (SSE) directo con Gemini API (SPEC-027 §2.2)
   * @param {string} promptText
   */
  async streamGeminiDirect(promptText) {
    this.emit('statusChange', { status: 'THINKING', type: 'thinking' });
    const apiKey = this.apiKey || (typeof localStorage !== "undefined" ? localStorage.getItem("ag_gemini_api_key") : null);
    const modelsToTry = ["gemini-2.5-pro", "gemini-3.6-flash", "gemini-2.5-flash"];
    let lastError = null;

    for (const model of modelsToTry) {
      try {
        const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:streamGenerateContent?alt=sse&key=${apiKey}`;
        const response = await fetch(url, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            contents: [{ role: "user", parts: [{ text: promptText }] }],
            generationConfig: {
              temperature: 0.7,
              thinkingConfig: { includeThoughts: true }
            }
          })
        });

        if (!response.ok) {
          const errText = await response.text();
          if (response.status === 404) {
            lastError = new Error(`Model ${model} not found: ${errText}`);
            continue;
          }
          throw new Error(`Gemini API error (${response.status}): ${errText}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder("utf-8");
        let buffer = "";

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop();

          for (const line of lines) {
            const trimmed = line.trim();
            if (trimmed.startsWith("data: ")) {
              const jsonStr = trimmed.slice(6);
              try {
                const data = JSON.parse(jsonStr);
                const candidates = data.candidates || [];
                if (candidates.length > 0 && candidates[0].content?.parts) {
                  for (const part of candidates[0].content.parts) {
                    if (part.thought) {
                      this.emit('thinking', { raw: part.text, isComplete: false });
                    } else if (part.text) {
                      this.emit('message', { raw: part.text, text: part.text });
                    }
                  }
                }
              } catch (parseErr) {
                // Fragmento JSON en curso
              }
            }
          }
        }

        this.emit('statusChange', { status: 'READY', type: 'ready' });
        return;
      } catch (err) {
        lastError = err;
        if (model !== modelsToTry[modelsToTry.length - 1]) continue;
      }
    }

    console.error("Gemini Direct streaming failed:", lastError);
    this.emit('message', {
      raw: "",
      text: `⚠️ **Error al conectar con Gemini Direct API**: ${lastError?.message || "Error desconocido"}. Verifica tu conexión a internet o tu clave de API.`
    });
    this.emit('statusChange', { status: 'READY', type: 'ready' });
  }

  /**
   * Flushes all queued prompts to PTY once connected
   */
  flushPendingQueue() {
    if (!this.isConnected || !this.websocket || this.websocket.readyState !== WebSocket.OPEN) return;
    while (this.pendingQueue.length > 0) {
      const nextPrompt = this.pendingQueue.shift();
      this.websocket.send(`${nextPrompt}\r`);
      this.emit('promptDispatched', { text: nextPrompt });
    }
  }

  /**
   * Installs Linux Ubuntu ARM64 runtime and agy CLI (SPEC-024 §2 & §3)
   */
  async installRuntime(onProgress, onLog) {
    if (typeof Terminal === "undefined" || typeof Terminal.install !== "function") {
      console.warn("Terminal plugin no disponible");
      return false;
    }

    this.emit('statusChange', { status: 'INITIALIZING', type: 'thinking' });

    try {
      const success = await Terminal.install(
        (msg) => {
          if (onLog) onLog(msg);
          if (onProgress && typeof msg === "string") {
            if (msg.includes("Extrayendo") || msg.includes("Extracting") || msg.includes("Descomprimiendo")) onProgress(50);
            else if (msg.includes("Installing") || msg.includes("Instalando") || msg.includes("directorios")) onProgress(80);
            else if (msg.includes("éxito") || msg.includes("completed") || msg.includes("completada") || msg.includes("inicializado")) onProgress(100);
          }
          this.emit('installLog', { message: msg });
        },
        (err) => {
          if (onLog) onLog(`[ERROR] ${err}`);
          console.error("Installation log error:", err);
        }
      );

      if (success) {
        if (onProgress) onProgress(100);
        // SPEC-025: PROHIBIDO llamar a this.connect() aquí.
        // installRuntime se limita a retornar el éxito del aprovisionamiento.
        return true;
      }
      return false;
    } catch (e) {
      console.error("Runtime installation failed:", e);
      this.emit('statusChange', { status: 'ERROR', type: 'error', error: e.message });
      throw e;
    }
  }

  /**
   * Dispara el flujo de autenticación oficial de Google Antigravity (SPEC-024 §4.3, SPEC-026: NativeGoogleOAuthContract)
   * @returns {Promise<any>}
   */
  async triggerGoogleLogin() {
    this.emit('statusChange', { status: 'AUTH', type: 'thinking' });
    const GoogleAuthService = (await import("./GoogleAuthService")).default;
    return GoogleAuthService.startOAuthFlow();
  }

  /**
   * Approve plan execution: sends 'y\r' to PTY
   */
  approvePlan() {
    if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) return;
    this.websocket.send("y\r");
    this.emit('statusChange', { status: 'RUNNING', type: 'thinking' });
  }

  /**
   * Interrupt current command or generation: sends Ctrl+C (\u0003)
   */
  interrupt() {
    if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) return;
    this.websocket.send("\u0003");
    this.emit('statusChange', { status: 'READY', type: 'ready' });
  }

  /**
   * Switch to a specific workspace directory cleanly and continue agy
   * @param {string} projectName
   */
  switchProject(projectName) {
    if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) return;
    this.activeProject = projectName;
    const cleanCmd = `\u0003clear\ncd "/home/studio/workspace/${projectName}" 2>/dev/null || cd /root\nclear\nexec agy -c\n`;
    this.websocket.send(cleanCmd);
  }

  /**
   * Process raw output from PTY stream and detect semantic blocks
   */
  handlePtyOutput(rawData) {
    if (typeof rawData !== "string") return;

    // Clean ANSI color codes for semantic text extraction
    const cleanText = rawData.replace(/\x1b\[[0-9;]*[a-zA-Z]/g, "");

    // 1. Detect Thinking stream
    if (cleanText.includes("Thinking...") || cleanText.includes("<thinking>")) {
      this.emit('statusChange', { status: 'THINKING', type: 'thinking' });
      this.emit('thinking', { raw: cleanText, isComplete: false });
    }

    // 2. Detect Tool Calls
    if (cleanText.includes("write_to_file") || cleanText.includes("replace_file_content") || cleanText.includes("run_command")) {
      const toolMatch = cleanText.match(/(write_to_file|replace_file_content|run_command)\(([^)]*)\)/);
      if (toolMatch) {
        this.emit('toolCall', {
          toolName: toolMatch[1],
          args: toolMatch[2],
          status: 'RUNNING',
          output: cleanText,
        });
      }
    }

    // 3. Detect Plan Approval Prompts
    if (cleanText.includes("Aprobar") || cleanText.includes("Do you trust") || cleanText.includes("[y/N]") || cleanText.includes("PLAN DE ARQUITECTURA")) {
      this.emit('planApproval', {
        title: "Plan de Arquitectura Propuesto",
        raw: cleanText,
        status: 'AWAITING_USER_APPROVAL',
      });
    }

    // 4. Detect Google OAuth login completion (SPEC-024 §4.3, AC-OAUTH-003)
    if (cleanText.includes("Authentication successful") || cleanText.includes("Logged in as")) {
      const emailMatch = cleanText.match(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/);
      const email = emailMatch ? emailMatch[0] : "Google Developer";
      this.emit('authSuccess', { email, tier: 'Google AI Ultra' });
      this.emit('statusChange', { status: 'READY', type: 'ready' });
    }

    // 5. Emit standard message chunk
    this.emit('message', { raw: rawData, text: cleanText });
  }

  /**
   * Scans localhost ports (3000, 5173, 8080, 8000, 4321) to detect live servers (SPEC-026: PortScannerSanitizationContract)
   */
  startPortScanner() {
    if (this.portScanInterval) clearInterval(this.portScanInterval);
    const candidatePorts = [3000, 5173, 8080, 8000, 4321];

    this.portScanInterval = setInterval(async () => {
      // Solo escanear si el agente está conectado y el entorno está listo
      if (!this.isConnected) return;

      for (const port of candidatePorts) {
        if (this.detectedPorts.has(port)) continue;

        const isLive = await this.probePort(port);
        if (isLive && !this.detectedPorts.has(port)) {
          this.detectedPorts.add(port);
          this.emit('serverDetected', { port, url: `http://localhost:${port}` });
        }
      }
    }, 5000);
  }

  async probePort(port) {
    const url = `http://127.0.0.1:${port}/`;

    // 1. Android Nativo vía cordova-plugin-advanced-http
    if (window.cordova?.plugin?.http) {
      return new Promise((resolve) => {
        cordova.plugin.http.sendRequest(
          url,
          { method: "HEAD", timeout: 0.8 },
          (response) => {
            // Si respondió cualquier código HTTP (200, 404, 500), el servidor está vivo
            resolve(response.status >= 200 && response.status < 600);
          },
          (error) => {
            // Si el código de estado existe en el error (ej. 404/500), el servidor está vivo
            if (error.status && error.status >= 200 && error.status < 600) {
              resolve(true);
            } else {
              resolve(false); // Conexión rechazada o timeout
            }
          }
        );
      });
    }

    // 2. Fallback estándar para navegador
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 800);
      const res = await window.fetch(url, {
        method: "GET",
        mode: "no-cors",
        signal: controller.signal,
      }).catch(() => null);
      clearTimeout(timeoutId);

      // En modo no-cors, si la conexión fue rechazada, res es null
      return res !== null && res.type === "opaque";
    } catch (e) {
      return false;
    }
  }

  disconnect() {
    if (this.portScanInterval) {
      clearInterval(this.portScanInterval);
      this.portScanInterval = null;
    }
    if (this.websocket) {
      this.websocket.close();
      this.websocket = null;
    }
    this.isConnected = false;
  }
}

export default new AgentBridge();
