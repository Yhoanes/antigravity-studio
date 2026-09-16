/**
 * AgentStreamClient - Decodificador del protocolo `stream-json` del CLI `agy`
 * Conforme a SPEC-054: Interfaz Nativa de Agente sobre Eventos Estructurados
 *
 * Lógica pura y comprobable: NO toca el DOM ni lanza procesos. Recibe fragmentos
 * arbitrarios de stdout, los trocea en líneas NDJSON, decodifica los eventos y
 * mantiene el modelo de pasos que la vista renderiza.
 *
 * Contrato verificado contra harness/fixtures/agy-print-stream-json*.ndjson
 * (Antigravity CLI 1.2.4).
 */

// SPEC-054 §3.1: los tres valores observados de `event`
export const AGY_EVENT = {
    INIT: "init",
    STEP_UPDATE: "step_update",
    RESULT: "result",
};

export const STEP_STATE = {
    ACTIVE: "ACTIVE",
    DONE: "DONE",
};

// SPEC-054 §3.1: `step_type` observados. La lista no es cerrada — el protocolo no
// está documentado, así que un tipo desconocido se propaga en lugar de descartarse.
export const STEP_TYPE = {
    USER_INPUT: "user_input",
    AGENT_RESPONSE: "agent_response",
    TOOL: "tool",
    ERROR_MESSAGE: "error_message",
    // Observado en dispositivo (v2.8.1): agy emite este paso al inicio de cada
    // turno. No se habia visto en el corpus capturado.
    SYSTEM_MESSAGE: "system_message",
};

export const RESULT_STATUS = {
    SUCCESS: "SUCCESS",
    ERROR: "ERROR",
};

// Caracteres de cita como constantes: evita secuencias de escape ambiguas en
// la construccion del comando de shell (ver shellQuote).
const SINGLE_QUOTE = String.fromCharCode(39);
const DOUBLE_QUOTE = String.fromCharCode(34);

export class AgentStreamClient {
    constructor(options = {}) {
        this.onInit = options.onInit || (() => {});
        this.onStep = options.onStep || (() => {});
        this.onResult = options.onResult || (() => {});
        this.onMalformed = options.onMalformed || (() => {});

        // Opciones de invocación (SPEC-054 §3.3, transporte v1)
        this.model = options.model || null;
        this.effort = options.effort || null;
        this.mode = options.mode || null;
        this.printTimeout = options.printTimeout || "15m";
        this.skipPermissions = options.skipPermissions !== false;

        this.conversationId = options.conversationId || null;
        this.capabilities = null;

        this._buffer = "";
        this._lineBuffer = "";
        this._steps = new Map();
        this._malformedCount = 0;

        // Tope de reensamblado de ingestLine(). Sin el, una linea corrupta abierta
        // por una llave acumularia indefinidamente.
        this.maxLineBytes = options.maxLineBytes || 1048576;
    }

    /**
     * SPEC-054: cita POSIX de un argumento para pasarlo por `sh -c`.
     * Envuelve en comillas simples y escapa las internas.
     */
    static shellQuote(arg) {
        const raw = (arg === null || arg === undefined) ? "" : String(arg);
        // Cita POSIX construida sin secuencias de escape, a proposito: cierra la
        // comilla simple, intercala una comilla simple entre dobles, y reabre.
        //   o'reilly  ->  'o'"'"'reilly'
        const Q = SINGLE_QUOTE;
        const SAFE = Q + DOUBLE_QUOTE + Q + DOUBLE_QUOTE + Q;
        return Q + raw.split(Q).join(SAFE) + Q;
    }

    /**
     * Comando de shell completo para el transporte. `Executor.start()` acepta una
     * cadena y la ejecuta por `sh -c`, de ahi la cita POSIX de cada argumento.
     *
     * `opts.binary`:
     *   - omitido  -> "agy" (invocacion directa, p.ej. desde el host)
     *   - ""/null  -> SIN binario, solo el argv
     *
     * El caso sin binario es el que usa AgentSession: al entrar por el sandbox
     * Alpine, `init-alpine.sh` ya hace `exec agy "$@"`, asi que incluir "agy"
     * produce `agy agy -p ...` y el CLI responde
     * `unexpected argument "agy"`. El contrato del script de entrada es recibir
     * los ARGUMENTOS de agy, no el binario.
     */
    buildCommand(promptText, opts = {}) {
        const quoted = this.buildArgv(promptText, opts)
            .map((a) => AgentStreamClient.shellQuote(a));
        const binary = opts.binary === undefined ? "agy" : opts.binary;
        return (binary ? [binary].concat(quoted) : quoted).join(" ");
    }

    /**
     * SPEC-054 §3.3: argv del turno. Reinyecta `--conversation` cuando ya hay
     * un `conversation_id` capturado, que es lo que preserva el contexto entre
     * invocaciones en el transporte de proceso-por-turno (AC-STREAM-005).
     */
    buildArgv(promptText, opts = {}) {
        // "-p" debe ir primero y con UN solo guion. `init-alpine.sh` evalua
        // [ "${1#--}" = "$1" ]: si el primer argumento empieza por "--", omite
        // el `exec agy "$@"` y la invocacion no llega a ejecutarse nunca.
        const argv = ["-p", String(promptText ?? ""), "--output-format", "stream-json"];

        const conversationId = opts.conversationId || this.conversationId;
        if (conversationId) {
            argv.push("--conversation", conversationId);
        }

        const model = opts.model || this.model;
        if (model) argv.push("--model", model);

        const effort = opts.effort || this.effort;
        if (effort) argv.push("--effort", effort);

        const mode = opts.mode || this.mode;
        if (mode) argv.push("--mode", mode);

        const printTimeout = opts.printTimeout || this.printTimeout;
        if (printTimeout) argv.push("--print-timeout", printTimeout);

        const skipPermissions = opts.skipPermissions !== undefined
            ? opts.skipPermissions
            : this.skipPermissions;
        if (skipPermissions) argv.push("--dangerously-skip-permissions");

        return argv;
    }

    /**
     * Acepta un fragmento arbitrario de stdout. Tolera límites de línea partidos:
     * el resto incompleto queda retenido hasta que llegue su salto de línea
     * (AC-STREAM-002).
     *
     * El troceado de bytes multi-byte UTF-8 es responsabilidad del transporte:
     * decodifica con `new TextDecoder().decode(chunk, { stream: true })` antes
     * de llamar aquí.
     */
    ingest(chunk) {
        if (chunk === null || chunk === undefined) return;
        this._buffer += String(chunk);

        const lines = this._buffer.split("\n");
        // El último elemento puede ser una línea incompleta: se retiene.
        this._buffer = lines.pop() ?? "";

        for (const line of lines) {
            this._handleLine(line);
        }
    }

    /**
     * Entrada para transportes line-delimited (`Executor.start`), que entregan una
     * linea por mensaje y ya han consumido el salto de linea, por lo que ingest()
     * nunca despacharia.
     *
     * Tolera que el nativo parta una linea larga en varios mensajes: si lo
     * acumulado no decodifica pero parece un prefijo incompleto, se retiene y se
     * reintenta con el mensaje siguiente. Con un tope para que una linea corrupta
     * abierta por llave no acumule sin limite.
     */
    ingestLine(line) {
        const incoming = (line === null || line === undefined) ? "" : String(line);
        const combined = (this._lineBuffer + incoming).trim();

        if (!combined) {
            this._lineBuffer = "";
            return;
        }

        // 1. Lo acumulado decodifica: era una linea partida ya completa.
        if (AgentStreamClient._parses(combined)) {
            this._lineBuffer = "";
            this._handleLine(combined);
            return;
        }

        // 2. La linea nueva decodifica por si sola: lo retenido era basura, no un
        //    prefijo. Se descarta el residuo y se procesa la linea buena, en lugar
        //    de pegarla al residuo y perder todo el stream posterior.
        const alone = incoming.trim();
        if (this._lineBuffer && alone && AgentStreamClient._parses(alone)) {
            const discarded = this._lineBuffer;
            this._lineBuffer = "";
            this._malformedCount += 1;
            this.onMalformed(discarded, new Error("Residuo no reensamblable descartado"));
            this._handleLine(alone);
            return;
        }

        // 3. Parece prefijo incompleto y cabe en el tope: se retiene.
        if (this._looksIncomplete(combined) && combined.length <= this.maxLineBytes) {
            this._lineBuffer += incoming;
            return;
        }

        // 4. Basura sin salvacion.
        this._lineBuffer = "";
        this._malformedCount += 1;
        this.onMalformed(combined, new Error("Linea no decodificable"));
    }

    /** true si el texto decodifica como JSON. */
    static _parses(text) {
        try {
            JSON.parse(text);
            return true;
        } catch (_) {
            return false;
        }
    }

    /** Heuristica de prefijo JSON incompleto: abre por llave y no cierra balanceado. */
    _looksIncomplete(text) {
        if (text[0] !== "{" && text[0] !== "[") return false;

        let depth = 0;
        let inString = false;
        let escaped = false;
        for (const ch of text) {
            if (escaped) { escaped = false; continue; }
            if (inString) {
                if (ch === "\\") escaped = true;
                else if (ch === '"') inString = false;
                continue;
            }
            if (ch === '"') inString = true;
            else if (ch === "{" || ch === "[") depth += 1;
            else if (ch === "}" || ch === "]") depth -= 1;
        }
        return inString || depth > 0;
    }

    /**
     * Procesa el resto retenido. Debe invocarse al cerrar stdout, porque la
     * última línea del stream puede no terminar en salto de línea.
     */
    end() {
        const pending = [this._buffer, this._lineBuffer];
        this._buffer = "";
        this._lineBuffer = "";
        for (const remainder of pending) {
            if (remainder && remainder.trim()) {
                this._handleLine(remainder);
            }
        }
    }

    _handleLine(rawLine) {
        const line = rawLine.trim();
        if (!line) return;

        let event;
        try {
            event = JSON.parse(line);
        } catch (err) {
            // AC-STREAM-006: una línea corrupta no aborta el flujo ni pierde
            // los eventos posteriores.
            this._malformedCount += 1;
            this.onMalformed(rawLine, err);
            return;
        }

        if (!event || typeof event !== "object") {
            this._malformedCount += 1;
            this.onMalformed(rawLine, new Error("La línea no decodifica a un objeto"));
            return;
        }

        this._captureConversationId(event);

        switch (event.event) {
            case AGY_EVENT.INIT:
                this._handleInit(event);
                break;
            case AGY_EVENT.STEP_UPDATE:
                this._handleStepUpdate(event);
                break;
            case AGY_EVENT.RESULT:
                this._handleResult(event);
                break;
            default:
                // Evento desconocido: se reporta, no se descarta en silencio.
                this._malformedCount += 1;
                this.onMalformed(rawLine, new Error(`Evento no reconocido: ${event.event}`));
        }
    }

    _captureConversationId(event) {
        const id = event.conversation_id
            || event.init?.conversation_id
            || event.step_update?.conversation_id
            || event.result?.conversation_id;
        if (id) this.conversationId = id;
    }

    _handleInit(event) {
        const init = event.init || {};
        this.capabilities = {
            cwd: init.cwd || null,
            tools: Array.isArray(init.tools) ? init.tools.slice() : [],
            permissionMode: init.permission_mode || null,
        };
        this.onInit(this.capabilities, event);
    }

    /**
     * SPEC-054 §3.1: `step_index` es la clave de agrupación. Varios eventos con
     * el mismo índice son actualizaciones del mismo elemento de la lista, no
     * elementos nuevos (AC-VIEW-001).
     */
    _handleStepUpdate(event) {
        const update = event.step_update || {};
        const index = update.step_index;
        if (typeof index !== "number") {
            this._malformedCount += 1;
            this.onMalformed(JSON.stringify(event), new Error("step_update sin step_index numérico"));
            return;
        }

        const step = this._steps.get(index) || {
            index,
            type: update.step_type || null,
            state: null,
            text: "",
            toolName: null,
            parameters: null,
            durationSeconds: null,
            usage: null,
        };

        if (update.step_type) step.type = update.step_type;
        if (update.state) step.state = update.state;

        // Los deltas se acumulan; nunca se reemplazan.
        if (typeof update.text_delta === "string") {
            step.text += update.text_delta;
        }

        if (update.tool_name) step.toolName = update.tool_name;
        if (update.tool_info && typeof update.tool_info === "object") {
            step.toolName = step.toolName || update.tool_info.name || null;
            if (update.tool_info.parameters !== undefined) {
                step.parameters = update.tool_info.parameters;
            }
        }
        if (typeof update.duration_seconds === "number") {
            step.durationSeconds = update.duration_seconds;
        }
        if (update.usage && typeof update.usage === "object") {
            step.usage = update.usage;
        }

        this._steps.set(index, step);
        this.onStep(step, update, event);
    }

    /**
     * SPEC-054 §3.1: `status: "ERROR"` puede coexistir con un `response` útil y
     * completo. Se propagan ambos; tratar ERROR como "sin resultado" descartaría
     * trabajo válido (AC-STREAM-004).
     */
    _handleResult(event) {
        const result = event.result || {};
        const payload = {
            conversationId: result.conversation_id || this.conversationId,
            status: result.status || null,
            response: typeof result.response === "string" ? result.response : "",
            error: result.error || null,
            isError: result.status === RESULT_STATUS.ERROR,
            durationSeconds: typeof result.duration_seconds === "number" ? result.duration_seconds : null,
            numTurns: typeof result.num_turns === "number" ? result.num_turns : null,
            usage: result.usage || null,
        };
        this.onResult(payload, event);
    }

    /** Pasos ordenados por índice, con el texto ya acumulado. */
    getSteps() {
        return Array.from(this._steps.values()).sort((a, b) => a.index - b.index);
    }

    /** Texto concatenado de todos los pasos, en orden (ver AC-STREAM-003). */
    getConcatenatedText() {
        return this.getSteps().map((s) => s.text).join("");
    }

    get malformedCount() {
        return this._malformedCount;
    }

    /** Limpia el estado de turno. Conserva `conversationId` para el turno siguiente. */
    reset() {
        this._buffer = "";
        this._lineBuffer = "";
        this._steps = new Map();
        this._malformedCount = 0;
    }

    /** Descarta también la conversación: el turno siguiente arranca en frío. */
    resetConversation() {
        this.reset();
        this.conversationId = null;
        this.capabilities = null;
    }
}

export default AgentStreamClient;
