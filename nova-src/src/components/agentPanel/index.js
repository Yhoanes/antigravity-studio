import "./style.scss";
import tag from "html-tag-js";
import actionStack from "lib/actionStack";

/**
 * Nova Agent (Ask) Panel Component
 * Implements SPEC-017 §2.4, §3.4, §5.1
 * Provides side panel hosting Xterm.js terminal connected to agy CLI with status header and maximize toggle (⛶).
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
							glibc Ubuntu ARM64 &bull; Google Antigravity CLI (agy)
						</div>
						<div className="agent-prompt-hint">
							Conectado al motor agéntico PTY (127.0.0.1:8767)
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

	show() {
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

		// Dispatch formal SPEC-017 NovaUIEvent: TOGGLE_AGENT_MAXIMIZE
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
}

export default new NovaAgentPanel();
