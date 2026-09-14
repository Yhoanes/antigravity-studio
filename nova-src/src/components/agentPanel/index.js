import "./style.scss";
import tag from "html-tag-js";
import actionStack from "lib/actionStack";
import TerminalComponent from "components/terminal/terminal";
import terminalManager from "components/terminal/terminalManager";
import toast from "components/toast";

const STORAGE_KEY_WIDTH = "nova:agent-panel-width";

/**
 * Nova Agent (Ask) Panel Component
 * Implements SPEC-017, SPEC-018 & SPEC-019 (Docked Sidebar, Keep-Alive, 80-Cols & PointerEvents)
 * Provides docked side panel / full screen hosting Xterm.js terminal connected to agy CLI
 * with tactile resize handle, absolute lifecycle persistence, and responsive typography.
 */
class NovaAgentPanel {
	#el;
	#resizeHandle;
	#terminalContainer;
	#maximizeBtn;
	#statusBadge;
	#isMaximized = false;
	#isVisible = false;
	#terminalInstance = null;
	#isInstalling = false;
	#currentWidth = 420;
	#resizeObserver = null;
	#isPointerDragging = false;

	constructor() {
		this.#init();
	}

	#init() {
		this.#loadPersistedWidth();

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

		this.#resizeHandle = (
			<div
				id="agent-resize-handle"
				className="agent-resize-handle"
				role="separator"
				aria-orientation="vertical"
			>
				<div className="resize-handle-bar"></div>
			</div>
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
				{this.#resizeHandle}
				<div className="agent-panel-header">
					<div className="agent-panel-title">
						<span className="agent-icon">✦</span>
						<span className="agent-title-text">Nova Agent (Ask)</span>
						{this.#statusBadge}
					</div>
					<div className="agent-panel-actions">
						{this.#maximizeBtn}
					</div>
				</div>
				{this.#terminalContainer}
			</div>
		);

		this.#setupResizeHandleEvents();
		this.#setupAdaptiveTypographyObserver();
		window.addEventListener("resize", () => this.#onWindowResize());
	}

	get el() {
		return this.#el;
	}

	get resizeHandle() {
		return this.#resizeHandle;
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

	get currentWidth() {
		return this.#currentWidth;
	}

	#loadPersistedWidth() {
		const screenW = typeof window !== "undefined" ? window.innerWidth : 1280;
		const minWidth = Math.max(360, Math.round(screenW * 0.25));
		const maxWidth = Math.round(screenW * 0.75);
		const defaultW = Math.round(screenW * 0.35);

		let savedW = null;
		try {
			savedW = parseFloat(localStorage.getItem(STORAGE_KEY_WIDTH));
		} catch (_) {}

		let width = savedW && !isNaN(savedW) ? savedW : defaultW;
		if (width < minWidth) width = minWidth;
		if (width > maxWidth) width = maxWidth;
		this.#currentWidth = width;
	}

	#savePersistedWidth(width) {
		try {
			localStorage.setItem(STORAGE_KEY_WIDTH, String(Math.round(width)));
		} catch (e) {
			console.warn("Failed to persist agent panel width:", e);
		}
	}

	#setupResizeHandleEvents() {
		let startX = 0;
		let startW = 0;
		let startTime = 0;

		const onPointerDown = (e) => {
			if (this.#isMaximized) return;
			this.#isPointerDragging = true;
			startX = e.clientX;
			startW = this.#currentWidth;
			startTime = Date.now();
			try {
				this.#resizeHandle.setPointerCapture(e.pointerId);
			} catch (_) {}
			document.body.classList.add("agent-resizing");
			this.#el.style.transition = "none";
		};

		const onPointerMove = (e) => {
			if (!this.#isPointerDragging) return;
			const deltaX = startX - e.clientX;
			const screenW = window.innerWidth;
			const minWidth = Math.max(360, Math.round(screenW * 0.25));
			const maxWidth = Math.round(screenW * 0.75);

			let targetW = startW + deltaX;
			if (targetW < minWidth) targetW = minWidth;
			if (targetW > maxWidth) targetW = maxWidth;

			this.applyWidth(targetW);
			this.updateAdaptiveTypography();
			this.#terminalInstance?.fitAndResizeTerminal?.(false);
		};

		const onPointerUp = (e) => {
			if (!this.#isPointerDragging) return;
			this.#isPointerDragging = false;
			try {
				this.#resizeHandle.releasePointerCapture(e.pointerId);
			} catch (_) {}
			document.body.classList.remove("agent-resizing");
			this.#el.style.transition = "";

			// Tactile Swipe-to-Close gesture detection (SPEC-020 §3.3)
			const swipeRightPx = e.clientX - startX;
			const elapsedMs = Math.max(1, Date.now() - startTime);
			const vx = swipeRightPx / elapsedMs;
			if (swipeRightPx > 100 || (swipeRightPx > 40 && vx > 0.5)) {
				this.hide();
				return;
			}

			this.#savePersistedWidth(this.#currentWidth);
			this.updateAdaptiveTypography();
			this.#terminalInstance?.fitAndResizeTerminal?.(true);

			const editor = window.editorManager?.editor;
			if (typeof editor?.resize === "function") {
				editor.resize(true);
			}
		};

		this.#resizeHandle.addEventListener("pointerdown", onPointerDown);
		this.#resizeHandle.addEventListener("pointermove", onPointerMove);
		this.#resizeHandle.addEventListener("pointerup", onPointerUp);
		this.#resizeHandle.addEventListener("pointercancel", onPointerUp);

		// Touch swipe-to-close on panel header (SPEC-020 §3.3)
		let headerTouchStartX = 0;
		let headerTouchStartTime = 0;
		const headerEl = this.#el.querySelector(".agent-panel-header");
		if (headerEl) {
			headerEl.addEventListener(
				"touchstart",
				(e) => {
					if (e.touches && e.touches.length > 0) {
						headerTouchStartX = e.touches[0].clientX;
						headerTouchStartTime = Date.now();
					}
				},
				{ passive: true },
			);
			headerEl.addEventListener(
				"touchend",
				(e) => {
					if (e.changedTouches && e.changedTouches.length > 0) {
						const swipeX = e.changedTouches[0].clientX - headerTouchStartX;
						const elapsed = Math.max(1, Date.now() - headerTouchStartTime);
						const vx = swipeX / elapsed;
						if (swipeX > 100 || (swipeX > 40 && vx > 0.5)) {
							this.hide();
						}
					}
				},
				{ passive: true },
			);
		}
	}

	#setupAdaptiveTypographyObserver() {
		if (typeof ResizeObserver !== "undefined") {
			this.#resizeObserver = new ResizeObserver(() => {
				if (!this.#isVisible || !this.#terminalInstance?.terminal) return;
				this.updateAdaptiveTypography();
				this.#terminalInstance.fitAndResizeTerminal?.(false);
			});
			this.#resizeObserver.observe(this.#terminalContainer);
		}
	}

	#onWindowResize() {
		if (!this.#isVisible) return;
		const screenW = window.innerWidth;
		const minWidth = Math.max(360, Math.round(screenW * 0.25));
		const maxWidth = Math.round(screenW * 0.75);

		if (this.#currentWidth < minWidth) this.#currentWidth = minWidth;
		if (this.#currentWidth > maxWidth) this.#currentWidth = maxWidth;

		this.applyWidth(this.#currentWidth);
		this.updateAdaptiveTypography();
		this.#terminalInstance?.fitAndResizeTerminal?.(true);
	}

	/**
	 * Adaptive typography calculation ensuring 80 columns without line breaks (SPEC-019 §5)
	 */
	updateAdaptiveTypography() {
		if (!this.#terminalInstance?.terminal || !this.#terminalContainer) return;
		const containerWidth = this.#terminalContainer.clientWidth - 16;
		if (containerWidth <= 0) return;

		const desiredCols = 80;
		const maxPossibleCharWidth = containerWidth / desiredCols;
		const calculatedFontSize = maxPossibleCharWidth / 0.60;

		// Standard base 12px, condensed 11.5px, clamped between 10px and 13px
		const clampedFontSize = Math.max(10, Math.min(13, Number(calculatedFontSize.toFixed(1))));

		if (this.#terminalInstance.terminal.options.fontSize !== clampedFontSize) {
			this.#terminalInstance.terminal.options.fontSize = clampedFontSize;
		}
	}

	applyWidth(width) {
		this.#currentWidth = width;
		this.#el.style.width = `${width}px`;
		document.documentElement.style.setProperty("--agent-panel-width", `${width}px`);

		if (window.innerWidth >= 1024 && this.#isVisible && !this.#isMaximized) {
			document.body.classList.add("has-docked-agent");
			const rootEl = tag.get("#root");
			if (rootEl) {
				rootEl.style.marginRight = `${width}px`;
				const sideBarWidth = parseInt(localStorage.sideBarWidth) || 0;
				const isSideBarShown = localStorage.sidebarShown === "1";
				const leftMargin = isSideBarShown ? sideBarWidth : 0;
				rootEl.style.width = `calc(100% - ${leftMargin}px - ${width}px)`;
			}
		}
	}

	async show() {
		if (this.#isVisible) return;
		this.#isVisible = true;
		this.#el.classList.remove("hidden");
		this.#el.classList.add("visible");

		const togglerBtn = document.getElementById("agent-toggler");
		if (togglerBtn) togglerBtn.classList.add("active");

		actionStack.push({
			id: "nova-agent-panel",
			action: () => this.hide(),
		});

		this.applyWidth(this.#currentWidth);

		const editor = window.editorManager?.editor;
		if (typeof editor?.resize === "function") {
			editor.resize(true);
		}

		window.dispatchEvent(
			new CustomEvent("nova:agent-visibility-changed", {
				detail: { visible: true, maximized: this.#isMaximized },
			}),
		);

		// Keep-Alive Invariant: If session is already connected, restore in 0ms without re-running agy
		if (this.#terminalInstance && this.#terminalInstance.isConnected) {
			this.setStatus("READY", "ready");
			this.updateAdaptiveTypography();
			setTimeout(() => {
				this.#terminalInstance.fitAndResizeTerminal?.(true);
				this.#terminalInstance.focus?.();
			}, 50);
			return;
		}

		await this.checkAndSetupAgent();
	}

	hide() {
		if (!this.#isVisible) return;
		this.#isVisible = false;
		actionStack.remove("nova-agent-panel");
		this.#el.classList.remove("visible");
		this.#el.classList.add("hidden");

		const togglerBtn = document.getElementById("agent-toggler");
		if (togglerBtn) {
			togglerBtn.classList.remove("active");
			togglerBtn.classList.remove("thinking");
		}

		document.body.classList.remove("has-docked-agent");
		const rootEl = tag.get("#root");
		if (rootEl) {
			rootEl.style.removeProperty("margin-right");
			const sideBarWidth = parseInt(localStorage.sideBarWidth) || 0;
			const isSideBarShown = localStorage.sidebarShown === "1";
			if (isSideBarShown) {
				rootEl.style.width = `calc(100% - ${sideBarWidth}px)`;
			} else {
				rootEl.style.removeProperty("width");
			}
		}

		const editor = window.editorManager?.editor;
		if (typeof editor?.resize === "function") {
			editor.resize(true);
		}

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
		const rootEl = tag.get("#root");

		if (this.#isMaximized) {
			this.#el.classList.add("maximized");
			if (iconEl) iconEl.textContent = "🗗";
			if (rootEl) {
				rootEl.style.removeProperty("margin-right");
			}
		} else {
			this.#el.classList.remove("maximized");
			if (iconEl) iconEl.textContent = "⛶";
			this.applyWidth(this.#currentWidth);
		}

		this.updateAdaptiveTypography();

		if (this.#terminalInstance) {
			setTimeout(() => {
				this.#terminalInstance.fitAndResizeTerminal?.(true);
			}, 220);
		}

		const editor = window.editorManager?.editor;
		if (typeof editor?.resize === "function") {
			editor.resize(true);
		}

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

		const togglerBtn = document.getElementById("agent-toggler");
		if (togglerBtn && this.#isVisible) {
			if (type === "thinking") {
				togglerBtn.classList.add("thinking");
			} else {
				togglerBtn.classList.remove("thinking");
			}
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
					<div className="agent-brand">‹ ✦ › Nova Agent</div>
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
		// Keep-Alive Invariant: never re-run or recreate if already connected
		if (this.#terminalInstance && this.#terminalInstance.isConnected) {
			this.setStatus("READY", "ready");
			this.updateAdaptiveTypography();
			setTimeout(() => {
				this.#terminalInstance.fitAndResizeTerminal?.(true);
				this.#terminalInstance.focus?.();
			}, 50);
			return;
		}

		this.setStatus("STARTING", "thinking");
		this.#terminalContainer.innerHTML = "";

		try {
			const terminalComponent = new TerminalComponent({
				serverMode: true,
				fontSize: 12,
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
				this.updateAdaptiveTypography();
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
