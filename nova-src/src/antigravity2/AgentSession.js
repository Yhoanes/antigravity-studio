/**
 * AgentSession - Transporte de turno para el protocolo `stream-json` de `agy`
 * Conforme a SPEC-054 §3.3 (transporte v1: un proceso por turno)
 *
 * Une el plugin nativo `window.Executor` con el decodificador AgentStreamClient.
 * El executor es inyectable para poder verificar el transporte sin dispositivo.
 *
 * No toca el DOM: emite callbacks que la vista consume.
 */

import { AgentStreamClient } from "./AgentStreamClient.js";

export const SESSION_STATE = {
    IDLE: "idle",
    RUNNING: "running",
};

export class AgentSession {
    constructor(options = {}) {
        // Inyectable: en producción es el clobber `window.Executor` del plugin.
        this.executor = options.executor
            || (typeof window !== "undefined" ? window.Executor : null);

        // `agy` vive en el rootfs Alpine bajo PRoot, no en el host Android.
        this.alpine = options.alpine !== false;

        this.onInit = options.onInit || (() => {});
        this.onStep = options.onStep || (() => {});
        this.onResult = options.onResult || (() => {});
        this.onStderr = options.onStderr || (() => {});
        this.onError = options.onError || (() => {});
        this.onStateChange = options.onStateChange || (() => {});
        this.onMalformed = options.onMalformed || (() => {});

        this.client = new AgentStreamClient({
            model: options.model || null,
            effort: options.effort || null,
            mode: options.mode || null,
            printTimeout: options.printTimeout || "15m",
            skipPermissions: options.skipPermissions !== false,
            conversationId: options.conversationId || null,
            onInit: (caps, ev) => this.onInit(caps, ev),
            onStep: (step, update, ev) => this.onStep(step, update, ev),
            onResult: (result, ev) => {
                this._sawResult = true;
                this.onResult(result, ev);
            },
            onMalformed: (line, err) => this.onMalformed(line, err),
        });

        this.state = SESSION_STATE.IDLE;
        this.activeUuid = null;
        this._sawResult = false;
        this._stderr = "";
    }

    get isRunning() {
        return this.state === SESSION_STATE.RUNNING;
    }

    get conversationId() {
        return this.client.conversationId;
    }

    setModel(model) {
        this.client.model = model || null;
    }

    setEffort(effort) {
        this.client.effort = effort || null;
    }

    /**
     * Lanza un turno. Devuelve false si ya hay uno en curso o si falta el
     * executor nativo, para que la vista pueda rechazar el envío sin excepciones.
     */
    async send(promptText) {
        const text = String(promptText ?? "").trim();
        if (!text) return false;
        if (this.isRunning) return false;

        if (!this.executor || typeof this.executor.start !== "function") {
            this.onError(new Error("Executor nativo no disponible"));
            return false;
        }

        // Estado por turno: el conversationId se conserva a propósito.
        this.client.reset();
        this._sawResult = false;
        this._stderr = "";

        this._setState(SESSION_STATE.RUNNING);

        const command = this.client.buildCommand(text);

        try {
            this.activeUuid = await this.executor.start(
                command,
                (type, data) => this._onExecutorData(type, data),
                this.alpine,
            );
        } catch (err) {
            this._setState(SESSION_STATE.IDLE);
            this.activeUuid = null;
            this.onError(err instanceof Error ? err : new Error(String(err)));
            return false;
        }

        return true;
    }

    _onExecutorData(type, data) {
        switch (type) {
            case "stdout":
                // `Executor.start` entrega una línea por mensaje con el salto ya
                // consumido, de ahí ingestLine() en lugar de ingest().
                this.client.ingestLine(data);
                break;

            case "stderr":
                this._stderr += String(data ?? "") + "\n";
                this.onStderr(String(data ?? ""));
                break;

            case "exit":
                this.client.end();
                this._finish(data);
                break;

            default:
                // Mensaje sin prefijo reconocido: se trata como stdout, que es lo
                // que el plugin emite cuando la envoltura `tipo:contenido` falla.
                this.client.ingestLine(data);
        }
    }

    _finish(exitData) {
        const exitCode = Number.parseInt(String(exitData ?? "0"), 10);
        this.activeUuid = null;
        this._setState(SESSION_STATE.IDLE);

        // Si el proceso murió sin emitir `result`, la vista se quedaría esperando
        // para siempre. Se sintetiza un resultado de error con lo que haya.
        if (!this._sawResult) {
            this.onResult({
                conversationId: this.client.conversationId,
                status: "ERROR",
                response: this.client.getConcatenatedText(),
                error: this._stderr.trim()
                    || `agy terminó sin emitir resultado (código ${Number.isNaN(exitCode) ? "?" : exitCode})`,
                isError: true,
                durationSeconds: null,
                numTurns: null,
                usage: null,
                synthesized: true,
            }, null);
        }
    }

    /** Interrumpe el turno en curso. */
    async abort() {
        if (!this.activeUuid) return false;
        const uuid = this.activeUuid;
        this.activeUuid = null;
        try {
            if (this.executor && typeof this.executor.stop === "function") {
                await this.executor.stop(uuid);
            }
        } catch (err) {
            this.onError(err instanceof Error ? err : new Error(String(err)));
        }
        this._setState(SESSION_STATE.IDLE);
        return true;
    }

    /** Descarta la conversación: el turno siguiente arranca en frío. */
    newConversation() {
        this.client.resetConversation();
    }

    getSteps() {
        return this.client.getSteps();
    }

    _setState(state) {
        if (this.state === state) return;
        this.state = state;
        this.onStateChange(state);
    }
}

export default AgentSession;
