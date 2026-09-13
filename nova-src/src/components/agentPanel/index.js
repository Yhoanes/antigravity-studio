import "./style.scss";
import tag from "html-tag-js";
import actionStack from "lib/actionStack";
import TerminalComponent from "components/terminal/terminal";
import terminalManager from "components/terminal/terminalManager";
import toast from "components/toast";

/**
 * Nova Agent (Ask) Panel Component
 * Implements SPEC-017 §2.4, §3.4, §5.1 & SPEC-018 §2, §3
 * Provides side panel / full screen hosting Xterm.js terminal connected to agy CLI
 * with integrated onboarding check, live installation log and status header.
 */
class NovaAgentPanel {
	#el;
	#terminalContainer;
	#maximizeBtn;
	#closeBtn;
	#statusBadge;
	#isMaximized = false;
	#isVisible = false;
	#terminalInstance = null;
	#isInstalling = false;

	constructor() {
		this.#init();
	}

	#init() {
		this.#statusBadge = (
			<span className="agent-status-badge ready">READY</span>
		);

		this.#maximizeBtn = (
			<button
				id="agent-maximize-btn"
				className="agent-action-btn"
				title="Toggle Maximize (⛶)"
				aria-label="Toggle Maximize"
				onclick={() => this.toggleMaximize()}
			>
				<span className="maximize-icon">⛶</span>
			</button>
		);

		this.#closeBtn = (
			<button
				id="agent-close-btn"
				className="agent-action-btn"
				title="Close Panel"
				aria-label="Close Panel"
				onclick={() => this.hide()}
			>
				<span className="icon close" style={{ fontSize: "1em" }}>✕</span>
			</button>
		);

		this.#terminalContainer = (
			<div
				id="agent-terminal-container"
				className="agent-terminal-container"
			>
				<div className="agent-terminal-placeholder">
					<div className="agent-placeholder-content">
						<div className="agent-brand">‹ ✦ › Nova Agent</div>
						<div className="agent-subtext">
							Ubuntu Noble ARM64 &bull; Google Antigravity CLI (agy)
						</div>
						<div className="agent-prompt-hint">
							Conectando al motor agéntico PTY (127.0.0.1:8767)...
						</div>
					</div>
				</div>
			</div>
		);

		this.#el = (
			<div id="nova-agent-panel" className="nova-agent-panel hidden">
				<div className="agent-panel-header">
					<div className="agent-panel-title">
						<span className="agent-icon">✦</span>
						<span className="agent-title-text">Nova Agent (Ask)</span>
						{this.#statusBadge}
					</div>
					<div className="agent-panel-actions">
						{this.#maximizeBtn}
						{this.#closeBtn}
					</div>
				</div>
				{this.#terminalContainer}
			</div>
		);
	}

	get el() {
		return this.#el;
	}

	get terminalContainer() {
		return this.#terminalContainer;
	}

	get isVisible() {
		return this.#isVisible;
	}

	get isMaximized() {
		return this.#isMaximized;
	}

	get terminalInstance() {
		return this.#terminalInstance;
	}

	async show() {
		if (this.#isVisible) return;
		this.#isVisible = true;
		this.#el.classList.remove("hidden");
		this.#el.classList.add("visible");

		actionStack.push({
			id: "nova-agent-panel",
			action: () => this.hide(),
		});

		window.dispatchEvent(
			new CustomEvent("nova:agent-visibility-changed", {
				detail: { visible: true, maximized: this.#isMaximized },
			}),
		);

		await this.checkAndSetupAgent();
	}

	hide() {
		if (!this.#isVisible) return;
		this.#isVisible = false;
		actionStack.remove("nova-agent-panel");
		this.#el.classList.remove("visible");
		this.#el.classList.add("hidden");

		if (this.#isMaximized) {
			this.toggleMaximize(false);
		}

		window.dispatchEvent(
			new CustomEvent("nova:agent-visibility-changed", {
				detail: { visible: false, maximized: false },
			}),
		);
	}

	toggle() {
		if (this.#isVisible) {
			this.hide();
		} else {
			this.show();
		}
	}

	toggleMaximize(forceState) {
		const nextState =
			typeof forceState === "boolean" ? forceState : !this.#isMaximized;
		this.#isMaximized = nextState;

		const iconEl = this.#maximizeBtn.querySelector(".maximize-icon");
		if (this.#isMaximized) {
			this.#el.classList.add("maximized");
			if (iconEl) iconEl.textContent = "🗗";
		} else {
			this.#el.classList.remove("maximized");
			if (iconEl) iconEl.textContent = "⛶";
		}

		if (this.#terminalInstance) {
			setTimeout(() => {
				this.#terminalInstance.fitAndResizeTerminal?.(true);
			}, 220);
		}

		// Dispatch formal SPEC-017 / SPEC-018 NovaUIEvent
		window.dispatchEvent(
			new CustomEvent("nova:agent-maximize-changed", {
				detail: { isMaximized: this.#isMaximized },
			}),
		);
	}

	setStatus(status = "READY", type = "ready") {
		if (this.#statusBadge) {
			this.#statusBadge.textContent = status;
			this.#statusBadge.className = `agent-status-badge ${type}`;
		}
	}

	mountTerminal(terminalInstance) {
		this.#terminalInstance = terminalInstance;
		this.#terminalContainer.innerHTML = "";
		if (terminalInstance && terminalInstance.mount) {
			terminalInstance.mount(this.#terminalContainer);
		}
	}

	/**
	 * Verify if Terminal environment is installed; if not, show onboarding UI
	 */
	async checkAndSetupAgent() {
		if (this.#isInstalling) return;

		let installed = false;
		try {
			if (typeof Terminal !== "undefined" && typeof Terminal.isInstalled === "function") {
				installed = await Terminal.isInstalled();
			} else if (typeof window.Terminal !== "undefined" && typeof window.Terminal.isInstalled === "function") {
				installed = await window.Terminal.isInstalled();
			}
		} catch (e) {
			console.warn("Failed to check Terminal.isInstalled():", e);
			installed = false;
		}

		if (!installed) {
			this.renderOnboarding();
		} else {
			await this.startAgentTerminal();
		}
	}

	/**
	 * Render Onboarding card with button [ Inicializar Nova Agent ] and live log
	 */
	renderOnboarding() {
		this.setStatus("SETUP NEEDED", "thinking");
		this.#terminalContainer.innerHTML = "";

		const initBtn = (
			<button
				className="agent-init-btn"
				id="agent-init-btn"
				onclick={() => this.runInstallation(initBtn, logContainer)}
			>
				<span className="icon wand-sparkles"></span>
				<span>Inicializar Nova Agent</span>
			</button>
		);

		const logContainer = (
			<div className="agent-install-log hidden" id="agent-install-log"></div>
		);

		const onboardingView = (
			<div className="agent-onboarding-container">
				<div className="agent-onboarding-card">
					<div className="agent-brand">✦ Nova Agent</div>
					<div className="agent-subtext">
						Motor Agéntico Autónomo (Ubuntu Noble ARM64 + agy CLI)
					</div>
					<div className="agent-onboarding-desc">
						El subsistema Linux y el agente inteligente de Google Antigravity aún no han sido aprovisionados en este dispositivo.
					</div>
					{initBtn}
					{logContainer}
				</div>
			</div>
		);

		this.#terminalContainer.appendChild(onboardingView);
	}

	/**
	 * Run on-demand provisioning and display live logs
	 */
	async runInstallation(initBtn, logContainer) {
		if (this.#isInstalling) return;
		this.#isInstalling = true;

		initBtn.disabled = true;
		initBtn.innerHTML = `<span>⏳ Instalando Nova Agent...</span>`;
		logContainer.classList.remove("hidden");
		logContainer.textContent = "Iniciando aprovisionamiento bajo demanda...\n";
		this.setStatus("INSTALLING", "thinking");

		const appendLog = (msg) => {
			logContainer.textContent += `${msg}\n`;
			logContainer.scrollTop = logContainer.scrollHeight;
		};

		try {
			const result = await terminalManager.checkAndInstallTerminal(
				(logMsg) => appendLog(logMsg),
				(errMsg) => appendLog(`[ERROR] ${errMsg}`),
			);

			if (result && result.success) {
				appendLog("✅ Aprovisionamiento completado con éxito.");
				toast("Nova Agent aprovisionado correctamente");
				this.#isInstalling = false;
				await this.startAgentTerminal();
			} else {
				const error = result?.error || "Error desconocido durante la instalación.";
				appendLog(`❌ Fallo en la instalación: ${error}`);
				this.setStatus("ERROR", "error");
				initBtn.disabled = false;
				initBtn.innerHTML = `<span>Reintentar Inicialización</span>`;
				this.#isInstalling = false;
			}
		} catch (err) {
			console.error("Installation error:", err);
			appendLog(`❌ Error: ${err.message || err}`);
			this.setStatus("ERROR", "error");
			initBtn.disabled = false;
			initBtn.innerHTML = `<span>Reintentar Inicialización</span>`;
			this.#isInstalling = false;
		}
	}

	/**
	 * Mount TerminalComponent connected to server PTY and launch agy
	 */
	async startAgentTerminal() {
		if (this.#terminalInstance && this.#terminalInstance.isConnected) {
			this.setStatus("READY", "ready");
			setTimeout(() => {
				this.#terminalInstance.fitAndResizeTerminal?.(true);
				this.#terminalInstance.focus?.();
			}, 100);
			return;
		}

		this.setStatus("STARTING", "thinking");
		this.#terminalContainer.innerHTML = "";

		try {
			const terminalComponent = new TerminalComponent({
				serverMode: true,
			});

			this.mountTerminal(terminalComponent);

			await terminalComponent.connectToSession();

			// Launch agy in the dedicated PTY session
			setTimeout(() => {
				if (terminalComponent.isConnected) {
					terminalComponent.write("agy\r");
				}
			}, 350);

			this.setStatus("READY", "ready");

			setTimeout(() => {
				terminalComponent.fitAndResizeTerminal?.(true);
				terminalComponent.focus?.();
			}, 150);
		} catch (err) {
			console.error("Failed to start agent terminal:", err);
			this.setStatus("ERROR", "error");
			const reconnectBtn = (
				<button
					className="agent-init-btn"
					onclick={() => this.checkAndSetupAgent()}
				>
					<span>Reconectar</span>
				</button>
			);
			const errorView = (
				<div className="agent-onboarding-container">
					<div className="agent-onboarding-card">
						<div className="agent-brand">⚠️ Error de Conexión</div>
						<div className="agent-onboarding-desc">
							No se pudo conectar a la sesión PTY del agente: {err.message || String(err)}
						</div>
						{reconnectBtn}
					</div>
				</div>
			);
			this.#terminalContainer.innerHTML = "";
			this.#terminalContainer.appendChild(errorView);
		}
	}
}

export default new NovaAgentPanel();
