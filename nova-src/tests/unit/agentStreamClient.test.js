import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import { describe, expect, it } from "vitest";

import {
	AgentStreamClient,
	RESULT_STATUS,
	STEP_STATE,
	STEP_TYPE,
} from "../../src/antigravity2/AgentStreamClient.js";

// SPEC-054: corpus normativo capturado del dispositivo (Antigravity CLI 1.2.4).
const FIXTURES = fileURLToPath(new URL("../../../harness/fixtures/", import.meta.url));

function readFixture(name) {
	return fs.readFileSync(path.join(FIXTURES, name), "utf8");
}

/**
 * Cliente instrumentado: registra la secuencia de eventos despachados para poder
 * afirmar sobre el orden, no solo sobre el estado final.
 */
function makeRecordingClient(options = {}) {
	const events = [];
	const client = new AgentStreamClient({
		...options,
		onInit: (caps) => events.push({ kind: "init", caps }),
		onStep: (step, update) => events.push({
			kind: "step",
			index: update.step_index,
			state: update.state,
			type: update.step_type,
			text: step.text,
		}),
		onResult: (result) => events.push({ kind: "result", result }),
		onMalformed: (line, err) => events.push({ kind: "malformed", line, message: err.message }),
	});
	return { client, events };
}

describe("AgentStreamClient - AC-STREAM-001: decodificación del corpus", () => {
	it("decodifica la captura simple con la secuencia exacta de eventos", () => {
		const { client, events } = makeRecordingClient();
		client.ingest(readFixture("agy-print-stream-json.ndjson"));
		client.end();

		expect(events.map((e) => e.kind)).toEqual([
			"init",
			"step", // idx 0 user_input DONE
			"step", // idx 1 agent_response ACTIVE
			"step", // idx 1 agent_response ACTIVE
			"step", // idx 1 agent_response DONE
			"result",
		]);
		expect(client.malformedCount).toBe(0);
	});

	it("expone las capacidades del evento init", () => {
		const { client } = makeRecordingClient();
		client.ingest(readFixture("agy-print-stream-json.ndjson"));

		expect(client.capabilities.cwd).toBe("/home/studio/workspace");
		expect(client.capabilities.permissionMode).toBe("request-review");
		expect(client.capabilities.tools).toContain("list_dir");
		expect(client.capabilities.tools).toContain("run_command");
		expect(client.capabilities.tools.length).toBeGreaterThan(50);
	});

	it("--dangerously-skip-permissions se refleja como always-proceed en el init", () => {
		const { client } = makeRecordingClient();
		client.ingest(readFixture("agy-print-stream-json-tools.ndjson"));

		expect(client.capabilities.permissionMode).toBe("always-proceed");
	});

	it("decodifica la captura con herramientas sin líneas malformadas", () => {
		const { client, events } = makeRecordingClient();
		client.ingest(readFixture("agy-print-stream-json-tools.ndjson"));
		client.end();

		expect(client.malformedCount).toBe(0);
		expect(events.filter((e) => e.kind === "malformed")).toHaveLength(0);
		expect(events.at(-1).kind).toBe("result");
	});
});

describe("AgentStreamClient - AC-STREAM-002: fragmentación arbitraria", () => {
	const FIXTURE_NAMES = [
		"agy-print-stream-json.ndjson",
		"agy-print-stream-json-tools.ndjson",
	];

	// Generador congruencial lineal: fragmentación aleatoria pero determinista.
	function seededChunks(text, seed) {
		let state = seed;
		const next = () => {
			state = (state * 1664525 + 1013904223) % 4294967296;
			return state / 4294967296;
		};
		const chunks = [];
		let offset = 0;
		while (offset < text.length) {
			const size = 1 + Math.floor(next() * 64);
			chunks.push(text.slice(offset, offset + size));
			offset += size;
		}
		return chunks;
	}

	for (const name of FIXTURE_NAMES) {
		it(`${name}: alimentar carácter a carácter produce la misma secuencia que de una vez`, () => {
			const text = readFixture(name);

			const whole = makeRecordingClient();
			whole.client.ingest(text);
			whole.client.end();

			const split = makeRecordingClient();
			for (const ch of text) split.client.ingest(ch);
			split.client.end();

			expect(split.events).toEqual(whole.events);
		});

		it(`${name}: fragmentación aleatoria con semilla fija es equivalente`, () => {
			const text = readFixture(name);

			const whole = makeRecordingClient();
			whole.client.ingest(text);
			whole.client.end();

			for (const seed of [1, 7, 42, 1337]) {
				const split = makeRecordingClient();
				for (const chunk of seededChunks(text, seed)) split.client.ingest(chunk);
				split.client.end();
				expect(split.events, `semilla ${seed}`).toEqual(whole.events);
			}
		});
	}

	it("una última línea sin salto de línea final se procesa en end()", () => {
		const { client, events } = makeRecordingClient();
		// Sin "\n" al final: el evento queda retenido hasta end().
		client.ingest('{"event":"result","result":{"status":"SUCCESS","response":"ok"}}');
		expect(events).toHaveLength(0);

		client.end();
		expect(events.map((e) => e.kind)).toEqual(["result"]);
	});

	it("el troceado a nivel de byte funciona con TextDecoder en modo stream", () => {
		// El contrato documentado: el transporte decodifica antes de llamar a ingest().
		// Esta prueba verifica esa combinación con UTF-8 multi-byte real ("¡Hola!", "vacío").
		const text = readFixture("agy-print-stream-json.ndjson");
		const bytes = new TextEncoder().encode(text);

		const whole = makeRecordingClient();
		whole.client.ingest(text);
		whole.client.end();

		const streamed = makeRecordingClient();
		const decoder = new TextDecoder("utf-8");
		for (const byte of bytes) {
			streamed.client.ingest(decoder.decode(new Uint8Array([byte]), { stream: true }));
		}
		streamed.client.ingest(decoder.decode());
		streamed.client.end();

		expect(streamed.events).toEqual(whole.events);
	});
});

describe("AgentStreamClient - AC-STREAM-003: deltas frente a result.response", () => {
	it("captura simple: los deltas concatenados reproducen exactamente result.response", () => {
		let result = null;
		const client = new AgentStreamClient({ onResult: (r) => { result = r; } });
		client.ingest(readFixture("agy-print-stream-json.ndjson"));
		client.end();

		expect(client.getConcatenatedText()).toBe(result.response);
		expect(result.response).toBe("¡Hola! ¿En qué puedo ayudarte hoy?\n");
	});

	/**
	 * SPEC-054 §5.2 - DISCREPANCIA ABIERTA, DOCUMENTADA COMO TRIPWIRE.
	 *
	 * En la captura con herramientas los deltas NO reproducen result.response: los
	 * deltas traen el enlace markdown completo donde response trae solo "workspace".
	 *
	 * Hipótesis principal: artefacto de transcripción (las fixtures se transcribieron
	 * de capturas de pantalla, no se volcaron a archivo).
	 * Hipótesis alternativa: result.response aplica una normalización.
	 *
	 * Esta prueba fija el estado CONOCIDO a propósito. Cuando se sustituyan las
	 * fixtures por volcados reales, fallará y forzará la resolución en lugar de
	 * dejar pasar la ambigüedad en silencio.
	 */
	it("captura con herramientas: la divergencia conocida de §5.2 sigue presente", () => {
		let result = null;
		const client = new AgentStreamClient({ onResult: (r) => { result = r; } });
		client.ingest(readFixture("agy-print-stream-json-tools.ndjson"));
		client.end();

		const joined = client.getConcatenatedText();

		expect(joined).not.toBe(result.response);
		expect(joined).toContain("[`/home/studio/workspace`](file:///home/studio/workspace)");
		expect(result.response).toContain("El directorio workspace se encuentra");
		expect(result.response).not.toContain("file:///home/studio/workspace");
	});
});

describe("AgentStreamClient - AC-STREAM-004: resultado en error con contenido útil", () => {
	it("propaga status ERROR, el campo error y el response completo a la vez", () => {
		let result = null;
		const client = new AgentStreamClient({ onResult: (r) => { result = r; } });
		client.ingest(readFixture("agy-print-stream-json-tools.ndjson"));
		client.end();

		expect(result.status).toBe(RESULT_STATUS.ERROR);
		expect(result.isError).toBe(true);
		expect(result.error).toContain("UNAVAILABLE (code 503)");
		expect(result.error).toContain("No capacity available");

		// Lo esencial: el contenido NO se pierde por venir marcado como error.
		expect(result.response).not.toBe("");
		expect(result.response).toContain("vacío");
		expect(result.usage.total_tokens).toBe(41053);
	});

	it("la captura correcta se marca SUCCESS sin campo error", () => {
		let result = null;
		const client = new AgentStreamClient({ onResult: (r) => { result = r; } });
		client.ingest(readFixture("agy-print-stream-json.ndjson"));
		client.end();

		expect(result.status).toBe(RESULT_STATUS.SUCCESS);
		expect(result.isError).toBe(false);
		expect(result.error).toBeNull();
	});
});

describe("AgentStreamClient - AC-STREAM-005: reinyección de conversation_id", () => {
	it("el primer turno no lleva --conversation", () => {
		const client = new AgentStreamClient();
		const argv = client.buildArgv("di hola");

		expect(argv).not.toContain("--conversation");
		expect(argv.slice(0, 4)).toEqual(["-p", "di hola", "--output-format", "stream-json"]);
	});

	it("tras el primer turno reinyecta el conversation_id capturado", () => {
		const client = new AgentStreamClient();
		client.ingest(readFixture("agy-print-stream-json.ndjson"));
		client.end();

		expect(client.conversationId).toBe("b3a987ef-7ac3-4eaa-94ab-24230c7aaf8a");

		const argv = client.buildArgv("y ahora qué");
		const at = argv.indexOf("--conversation");
		expect(at).toBeGreaterThan(-1);
		expect(argv[at + 1]).toBe("b3a987ef-7ac3-4eaa-94ab-24230c7aaf8a");
	});

	it("reset() conserva la conversación y resetConversation() la descarta", () => {
		const client = new AgentStreamClient();
		client.ingest(readFixture("agy-print-stream-json.ndjson"));
		client.end();

		client.reset();
		expect(client.conversationId).toBe("b3a987ef-7ac3-4eaa-94ab-24230c7aaf8a");
		expect(client.getSteps()).toHaveLength(0);

		client.resetConversation();
		expect(client.conversationId).toBeNull();
		expect(client.buildArgv("x")).not.toContain("--conversation");
	});

	it("traslada model, effort, mode, timeout y permisos al argv", () => {
		const client = new AgentStreamClient({
			model: "gemini-3.8-flash-high",
			effort: "high",
			mode: "plan",
			printTimeout: "15m",
		});
		const argv = client.buildArgv("haz algo");

		expect(argv).toContain("--dangerously-skip-permissions");
		expect(argv[argv.indexOf("--model") + 1]).toBe("gemini-3.8-flash-high");
		expect(argv[argv.indexOf("--effort") + 1]).toBe("high");
		expect(argv[argv.indexOf("--mode") + 1]).toBe("plan");
		expect(argv[argv.indexOf("--print-timeout") + 1]).toBe("15m");
	});

	it("skipPermissions: false omite el flag de auto-aprobación", () => {
		const client = new AgentStreamClient({ skipPermissions: false });
		expect(client.buildArgv("x")).not.toContain("--dangerously-skip-permissions");
	});
});

describe("AgentStreamClient - AC-STREAM-006: tolerancia a líneas corruptas", () => {
	it("descarta la línea inválida y conserva los eventos posteriores", () => {
		const text = readFixture("agy-print-stream-json.ndjson");
		const lines = text.split("\n").filter((l) => l.trim());

		// Inyecta basura entre el init y el resto del stream.
		const corrupted = [lines[0], "{ esto no es json", "", ...lines.slice(1)].join("\n") + "\n";

		const { client, events } = makeRecordingClient();
		client.ingest(corrupted);
		client.end();

		expect(client.malformedCount).toBe(1);
		expect(events.filter((e) => e.kind === "malformed")).toHaveLength(1);

		// Todo lo que venía después sigue llegando.
		expect(events.filter((e) => e.kind === "step")).toHaveLength(4);
		expect(events.at(-1).kind).toBe("result");
	});

	it("reporta un evento de tipo desconocido en lugar de ignorarlo en silencio", () => {
		const { client, events } = makeRecordingClient();
		client.ingest('{"event":"telemetry_ping","conversation_id":"abc"}\n');

		const malformed = events.filter((e) => e.kind === "malformed");
		expect(malformed).toHaveLength(1);
		expect(malformed[0].message).toContain("telemetry_ping");
		// Aun así captura el conversation_id: el evento es desconocido, no inútil.
		expect(client.conversationId).toBe("abc");
	});

	it("descarta step_update sin step_index numérico", () => {
		const { client, events } = makeRecordingClient();
		client.ingest('{"event":"step_update","step_update":{"state":"DONE"}}\n');

		expect(client.malformedCount).toBe(1);
		expect(events.filter((e) => e.kind === "step")).toHaveLength(0);
	});

	it("las líneas vacías y el espacio en blanco no cuentan como corruptas", () => {
		const { client, events } = makeRecordingClient();
		client.ingest("\n\n   \n\t\n");
		client.end();

		expect(client.malformedCount).toBe(0);
		expect(events).toHaveLength(0);
	});
});

describe("AgentStreamClient - modelo de pasos (base de AC-VIEW-001/002/003)", () => {
	it("agrupa por step_index: los deltas del mismo paso no crean pasos nuevos", () => {
		const client = new AgentStreamClient();
		client.ingest(readFixture("agy-print-stream-json.ndjson"));
		client.end();

		const steps = client.getSteps();
		// Tres eventos sobre el índice 1 colapsan en un solo paso.
		expect(steps.map((s) => s.index)).toEqual([0, 1]);

		const response = steps[1];
		expect(response.type).toBe(STEP_TYPE.AGENT_RESPONSE);
		expect(response.state).toBe(STEP_STATE.DONE);
		expect(response.text).toBe("¡Hola! ¿En qué puedo ayudarte hoy?\n");
	});

	it("los pasos de herramienta exponen nombre, parámetros y duración", () => {
		const client = new AgentStreamClient();
		client.ingest(readFixture("agy-print-stream-json-tools.ndjson"));
		client.end();

		const tools = client.getSteps().filter((s) => s.type === STEP_TYPE.TOOL);
		expect(tools).toHaveLength(2);

		const [listDir, findByName] = tools;

		expect(listDir.toolName).toBe("list_dir");
		expect(listDir.state).toBe(STEP_STATE.DONE);
		expect(listDir.parameters).toEqual({ DirectoryPath: "/home/studio/workspace" });
		expect(listDir.durationSeconds).toBeCloseTo(0.126659115, 6);

		expect(findByName.toolName).toBe("find_by_name");
		expect(findByName.parameters).toEqual({
			Pattern: ".*",
			SearchDirectory: "/home/studio/workspace",
		});
	});

	it("SPEC-054 §3.1: el paso de herramienta DONE no trae el resultado", () => {
		// Limitación verificada del protocolo, no un defecto del parser: la vista
		// debe renderizar la invocación, no la salida de la herramienta.
		const client = new AgentStreamClient();
		client.ingest(readFixture("agy-print-stream-json-tools.ndjson"));
		client.end();

		for (const tool of client.getSteps().filter((s) => s.type === STEP_TYPE.TOOL)) {
			expect(tool.text).toBe("");
		}
	});

	it("un agent_response sin text_delta es un paso de razonamiento con usage", () => {
		const client = new AgentStreamClient();
		client.ingest(readFixture("agy-print-stream-json-tools.ndjson"));
		client.end();

		const thinking = client.getSteps().filter(
			(s) => s.type === STEP_TYPE.AGENT_RESPONSE && s.text === "",
		);
		expect(thinking.length).toBeGreaterThan(0);
		for (const step of thinking) {
			expect(step.usage.thinking_tokens).toBeGreaterThan(0);
		}
	});

	it("registra el paso error_message intermedio", () => {
		const client = new AgentStreamClient();
		client.ingest(readFixture("agy-print-stream-json-tools.ndjson"));
		client.end();

		const errors = client.getSteps().filter((s) => s.type === STEP_TYPE.ERROR_MESSAGE);
		expect(errors).toHaveLength(1);
		expect(errors[0].index).toBe(5);
	});
});

describe("AgentStreamClient - captura --output-format json (no streaming)", () => {
	it("la fixture de objeto único es JSON válido con la forma de result", () => {
		// No pasa por ingest(): --output-format json emite un objeto sin envoltura
		// de evento. Se fija aquí para que el contrato quede cubierto por el corpus.
		const single = JSON.parse(readFixture("agy-print-json.json"));

		expect(single.status).toBe(RESULT_STATUS.SUCCESS);
		expect(single.conversation_id).toBe("3fd6e341-bff2-43a1-a94e-d38ba78c6ec9");
		expect(single.response).toBe("¡Hola! ¿En qué te puedo ayudar hoy?\n");
		expect(single.usage.total_tokens).toBe(13229);
		expect(single).not.toHaveProperty("event");
	});
});


// ---------------------------------------------------------------------------
// SPEC-054: cita de shell (buildCommand) e ingesta line-delimited (ingestLine)
// ---------------------------------------------------------------------------

/** Localiza un `sh` POSIX usable; null si no hay (la prueba se omite). */
function findSh() {
	const candidates = [
		"/bin/sh",
		"/usr/bin/sh",
		"C:/Program Files/Git/usr/bin/sh.exe",
		"C:/Program Files/Git/bin/sh.exe",
	];
	for (const p of candidates) {
		try {
			execFileSync(p, ["-c", "exit 0"]);
			return p;
		} catch {
			/* siguiente candidato */
		}
	}
	return null;
}

const SH = findSh();

describe("AgentStreamClient - cita de shell", () => {
	it("cita un argumento simple", () => {
		expect(AgentStreamClient.shellQuote("di hola")).toBe("'di hola'");
	});

	it("cita una comilla simple interna sin usar barras invertidas", () => {
		// Forma POSIX alternativa: cerrar, intercalar '"'"', reabrir.
		expect(AgentStreamClient.shellQuote("o'reilly")).toBe(`'o'"'"'reilly'`);
	});

	it("cita la cadena vacia y los valores nulos", () => {
		expect(AgentStreamClient.shellQuote("")).toBe("''");
		expect(AgentStreamClient.shellQuote(null)).toBe("''");
		expect(AgentStreamClient.shellQuote(undefined)).toBe("''");
	});

	it("buildCommand arma el binario mas el argv citado", () => {
		const client = new AgentStreamClient();
		const cmd = client.buildCommand("di hola");

		expect(cmd.startsWith("agy '-p' 'di hola'")).toBe(true);
		expect(cmd).toContain("'--output-format' 'stream-json'");
	});

	it("buildCommand acepta un binario alternativo", () => {
		const client = new AgentStreamClient();
		expect(client.buildCommand("x", { binary: "/usr/local/bin/agy" }))
			.toMatch(/^\/usr\/local\/bin\/agy /);
	});

	// Esta es la prueba que importa: el prompt es entrada de usuario que acaba en
	// una linea de comandos. Un fallo de cita aqui seria inyeccion de comandos.
	it.skipIf(!SH)("los argumentos hostiles sobreviven a un sh real sin interpretarse", () => {
		const hostiles = [
			"di hola",
			"o'reilly",
			'comillas " dobles',
			"punto; y coma && pipe | y $(subshell)",
			"backtick `whoami` dentro",
			"ruta con espacios/y $HOME",
			"todo junto: '; rm -rf / $(x) `y` \"z\"",
		];

		for (const arg of hostiles) {
			const quoted = AgentStreamClient.shellQuote(arg);
			const out = execFileSync(SH, ["-c", `printf %s ${quoted}`]).toString();
			expect(out, `argumento: ${JSON.stringify(arg)}`).toBe(arg);
		}
	});

	it.skipIf(!SH)("buildCommand trocea en el argv exacto esperado", () => {
		const client = new AgentStreamClient({ model: "gemini-3.8-flash-high" });
		const cmd = client.buildCommand("dime; algo 'raro' $(peligro)");
		const inner = cmd.replace(/^agy /, "");

		const argv = execFileSync(SH, [
			"-c",
			`set -- ${inner}; for a in "$@"; do printf "[%s]" "$a"; done`,
		]).toString();

		expect(argv).toContain("[dime; algo 'raro' $(peligro)]");
		expect(argv).toContain("[--output-format][stream-json]");
		expect(argv).toContain("[--model][gemini-3.8-flash-high]");
	});
});

describe("AgentStreamClient - ingestLine (transporte line-delimited)", () => {
	it("despacha una linea completa sin necesitar salto de linea", () => {
		const { client, events } = makeRecordingClient();
		client.ingestLine('{"event":"result","result":{"status":"SUCCESS","response":"ok"}}');

		expect(events.map((e) => e.kind)).toEqual(["result"]);
	});

	it("alimentar la fixture linea a linea equivale a alimentarla entera", () => {
		const text = readFixture("agy-print-stream-json-tools.ndjson");

		const whole = makeRecordingClient();
		whole.client.ingest(text);
		whole.client.end();

		const byLine = makeRecordingClient();
		for (const line of text.split("\n")) byLine.client.ingestLine(line);
		byLine.client.end();

		expect(byLine.events).toEqual(whole.events);
	});

	it("reensambla una linea que el nativo parte en varios mensajes", () => {
		const text = readFixture("agy-print-stream-json.ndjson");
		const lines = text.split("\n").filter((l) => l.trim());

		const whole = makeRecordingClient();
		for (const l of lines) whole.client.ingestLine(l);
		whole.client.end();

		const split = makeRecordingClient();
		for (const l of lines) {
			// Parte cada linea en tres trozos arbitrarios.
			const a = Math.floor(l.length / 3);
			const b = Math.floor((l.length * 2) / 3);
			split.client.ingestLine(l.slice(0, a));
			split.client.ingestLine(l.slice(a, b));
			split.client.ingestLine(l.slice(b));
		}
		split.client.end();

		expect(split.client.malformedCount).toBe(0);
		expect(split.events).toEqual(whole.events);
	});

	it("marca como corrupta la basura que no parece prefijo JSON", () => {
		const { client, events } = makeRecordingClient();
		client.ingestLine("esto no es json en absoluto");

		expect(client.malformedCount).toBe(1);
		expect(events.filter((e) => e.kind === "malformed")).toHaveLength(1);
	});

	it("respeta el tope de reensamblado en lugar de acumular sin limite", () => {
		const { client } = makeRecordingClient({ maxLineBytes: 64 });
		// Prefijo eternamente incompleto: abre llave y nunca cierra.
		client.ingestLine('{"event":"step_update","step_update":{"step_index":0');
		expect(client.malformedCount).toBe(0); // todavia acumulando

		client.ingestLine("x".repeat(200));
		expect(client.malformedCount).toBe(1); // tope superado, descartado
	});

	it("las lineas vacias del troceado no cuentan como corruptas", () => {
		const { client, events } = makeRecordingClient();
		client.ingestLine("");
		client.ingestLine("   ");
		client.end();

		expect(client.malformedCount).toBe(0);
		expect(events).toHaveLength(0);
	});
});
