/**
 * Google Antigravity 2.0 Mobile - AgentBridge Service
 * Manages WebSocket PTY communication with PRoot Ubuntu Noble ARM64 and agy CLI
 * SPEC-021: AC-AG2-009 (auto-continue flag agy -c)
 */

export class AgentBridge {
  constructor(options = {}) {
    this.port = options.port || 8767;
    this.pid = null;
    this.websocket = null;
    this.isConnected = false;
    this.isConnecting = false;
    this.autoContinue = options.autoContinue !== false;
    this.activeChatId = options.activeChatId || null;
    this.activeProject = options.activeProject || "default";

    this.listeners = {
      message: [],
      thinking: [],
      toolCall: [],
      planApproval: [],
      statusChange: [],
      serverDetected: [],
    };

    this.detectedPorts = new Set();
    this.portScanInterval = null;
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
      // 1. Check/Start AXS daemon if on Android with Terminal plugin
      if (typeof Terminal !== "undefined") {
        if (typeof Terminal.isAxsRunning === "function" && !(await Terminal.isAxsRunning())) {
          await Terminal.startAxs(false, () => {}, console.error, false);
        }
      }

      // 2. Wait for AXS ready
      await this.waitForServerReady();

      // 3. Create Terminal Session
      this.pid = await this.createSession();

      // 4. Open WebSocket
      const wsUrl = `ws://127.0.0.1:${this.port}/terminals/${this.pid}`;
      await this.openWebSocket(wsUrl);

      this.isConnected = true;
      this.isConnecting = false;
      this.emit('statusChange', { status: 'READY', type: 'ready' });

      // 5. Start agy with auto-continue flag: agy -c
      setTimeout(() => {
        this.launchAgy();
      }, 300);

      // 6. Start background local port detection
      this.startPortScanner();

    } catch (err) {
      console.error("AgentBridge connection failed:", err);
      this.isConnecting = false;
      this.isConnected = false;
      this.emit('statusChange', { status: 'ERROR', type: 'error', error: err.message });
      throw err;
    }
  }

  async waitForServerReady(maxAttempts = 20, delayMs = 400) {
    for (let i = 0; i < maxAttempts; i++) {
      try {
        const res = await fetch(`http://127.0.0.1:${this.port}/status`).catch(() => null);
        if (res && res.ok) return true;
      } catch (e) {}
      await new Promise((r) => setTimeout(r, delayMs));
    }
    // Proceed even if status check fails on restricted WebViews
    return true;
  }

  async createSession() {
    try {
      const res = await fetch(`http://127.0.0.1:${this.port}/terminals`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ cols: 100, rows: 35 }),
      });
      if (!res.ok) throw new Error(`Failed to create terminal: ${res.status}`);
      const pidText = await res.text();
      return pidText.trim();
    } catch (e) {
      // Fallback: If Cordova HTTP is available
      if (window.cordova?.plugin?.http) {
        return new Promise((resolve, reject) => {
          cordova.plugin.http.sendRequest(`http://127.0.0.1:${this.port}/terminals`, {
            method: "POST",
            data: { cols: 100, rows: 35 },
            serializer: "json",
          }, (r) => resolve(r.data.trim()), (err) => reject(new Error(err.error || "Terminal creation error")));
        });
      }
      throw e;
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
   * Dispatch a user prompt to the PTY
   * @param {string} promptText
   */
  sendUserPrompt(promptText) {
    if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) {
      throw new Error("Cannot send prompt: AgentBridge is disconnected");
    }
    this.emit('statusChange', { status: 'THINKING', type: 'thinking' });
    this.websocket.send(`${promptText}\r`);
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

    // 4. Emit standard message chunk
    this.emit('message', { raw: rawData, text: cleanText });
  }

  /**
   * Scans localhost ports (3000, 5173, 8080, 8000, 4321) to detect live servers
   */
  startPortScanner() {
    if (this.portScanInterval) clearInterval(this.portScanInterval);
    const candidatePorts = [3000, 5173, 8080, 8000, 4321];

    this.portScanInterval = setInterval(async () => {
      for (const port of candidatePorts) {
        try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 1200);
          const response = await fetch(`http://localhost:${port}/`, {
            method: "HEAD",
            signal: controller.signal,
            mode: "no-cors",
          }).catch(() => null);
          clearTimeout(timeoutId);

          if (response || (!this.detectedPorts.has(port) && await this.checkPortTcp(port))) {
            if (!this.detectedPorts.has(port)) {
              this.detectedPorts.add(port);
              this.emit('serverDetected', { port, url: `http://localhost:${port}` });
            }
          }
        } catch (e) {}
      }
    }, 4000);
  }

  async checkPortTcp(port) {
    // Quick heuristic: Try to load favicon or probe
    try {
      const img = new Image();
      return new Promise((resolve) => {
        img.onload = () => resolve(true);
        img.onerror = () => resolve(true); // Connected but 404 still means server is up
        img.src = `http://localhost:${port}/favicon.ico?_=${Date.now()}`;
        setTimeout(() => resolve(false), 800);
      });
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
