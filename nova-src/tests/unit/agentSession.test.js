import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it, vi } from "vitest";

import { AgentSession, SESSION_STATE } from "../../src/antigravity2/AgentSession.js";

const FIXTURES = fileURLToPath(new URL("../../../harness/fixtures/", import.meta.url));

function readFixture(name) {
	return fs.readFileSync(path.join(FIXTURES, name), "utf8");
}

/**
 * Doble del plugin nativo `window.Executor`. Reproduce su contrato documentado:
 * `start()` resuelve con un UUID y luego entrega `('stdout'|'stderr'|'exit', linea)`
 * con el salto de linea ya consumido.
 *
 * `resolveArgv()` modela la cadena REAL del sandbox, que es lo que v2.8.0 no
 * verifico y por lo que salio roto en dispositivo:
 *
 *   ProcessManager.createProcessBuilder:
 *     sh -c "source $PREFIX/init-sandbox.sh <cmd>"
 *   init-sandbox.sh:
 *     exec proot ... /bin/sh init-alpine.sh "$@"
 *   init-alpine.sh:
 *     [ $# -gt 0 ] && [ "${1#--}" = "$1" ] && exec agy "$@"
 *
 * O sea: el script de entrada PREPONE `agy`. El comando debe traer solo el argv.
 */
function makeFakeExecutor(options = {}) {
	const calls = [];
	let onData = null;

	const executor = {
		calls,
		start: vi.fn(async (command, cb, alpine) => {
			calls.push({ command, alpine });
			onData = cb;
			if (options.failStart) throw new Error("fallo de spawn");
			return options.uuid || "uuid-1";
		}),
		stop: vi.fn(async () => "stopped"),

		// Utilidades del doble, no parte del contrato del plugin.
		emitFixture(name) {
			for (const line of readFixture(name).split("\n")) {
				if (line.trim()) onData("stdout", line);
			}
		},
		emit(type, data) {
			onData(type, data);
		},

		/** argv efectivo que recibiria `agy` tras pasar por init-alpine.sh. */
		resolveArgv(index = 0) {
			const cmd = calls[index].command;
			const words = cmd.match(/'(?:[^']|'"'"')*'|\S+/g) || [];
			const argv = words.map((w) =>
				w.startsWith("'") && w.endsWith("'")
					? w.slice(1, -1).split(`'"'"'`).join("'")
					: w,
			);
			// init-alpine.sh omite el exec si el primer argumento empieza por "--".
			if (argv.length && argv[0].startsWith("--")) return null;
			return ["agy"].concat(argv);
		},
	};
	return executor;
}

describe("AgentSession - ciclo de vida del turno", () => {
	it("lanza agy con el comando citado dentro de Alpine", async () => {
		const executor = makeFakeExecutor();
		const session = new AgentSession({ executor });

		const accepted = await session.send("di hola");

		expect(accepted).toBe(true);
		expect(executor.start).toHaveBeenCalledTimes(1);

		const { command, alpine } = executor.calls[0];

		// El comando NO debe traer el binario: init-alpine.sh hace `exec agy "$@"`.
		// Incluirlo produjo `agy agy -p ...` y el CLI respondio
		// `unexpected argument "agy"` (regresion de v2.8.0).
		expect(command.startsWith("agy ")).toBe(false);
		expect(command.startsWith("'-p' 'di hola'")).toBe(true);
		expect(command).toContain("'--output-format' 'stream-json'");

		// agy vive en el rootfs Alpine bajo PRoot, no en el host Android.
		expect(alpine).toBe(true);
	});

	it("el argv que recibe agy tras el sandbox es el correcto, sin binario duplicado", () => {
		// Esta es la asercion que faltaba en v2.8.0.
		const executor = makeFakeExecutor();
		const session = new AgentSession({ executor });

		return session.send("di hola").then(() => {
			const argv = executor.resolveArgv(0);

			expect(argv, "init-alpine.sh omitiria el exec: el primer argumento empieza por --").not.toBeNull();
			expect(argv[0]).toBe("agy");
			expect(argv[1]).toBe("-p");
			expect(argv[2]).toBe("di hola");
			// Un solo "agy" en todo el argv.
			expect(argv.filter((a) => a === "agy")).toHaveLength(1);
		});
	});

	it("el primer argumento nunca empieza por doble guion", () => {
		// init-alpine.sh evalua [ "${1#--}" = "$1" ]: si empieza por "--", omite
		// el exec de agy entero y el turno no se ejecuta nunca.
		const executor = makeFakeExecutor();
		const session = new AgentSession({
			executor,
			model: "claude-sonnet-4-6",
			effort: "high",
			mode: "plan",
		});

		return session.send("con todas las opciones").then(() => {
			expect(executor.calls[0].command.startsWith("'-p'")).toBe(true);
			expect(executor.resolveArgv(0)).not.toBeNull();
		});
	});

	it("un prompt con comillas y metacaracteres llega intacto al argv", () => {
		const executor = makeFakeExecutor();
		const session = new AgentSession({ executor });
		const hostil = `o'reilly; rm -rf / $(x) \`y\` "z"`;

		return session.send(hostil).then(() => {
			const argv = executor.resolveArgv(0);
			expect(argv[2]).toBe(hostil);
		});
	});

	it("pasa de idle a running y vuelve a idle al salir el proceso", async () => {
		const executor = makeFakeExecutor();
		const states = [];
		const session = new AgentSession({
			executor,
			onStateChange: (s) => states.push(s),
		});

		expect(session.state).toBe(SESSION_STATE.IDLE);

		await session.send("di hola");
		expect(session.isRunning).toBe(true);

		executor.emitFixture("agy-print-stream-json.ndjson");
		executor.emit("exit", "0");

		expect(session.isRunning).toBe(false);
		expect(states).toEqual([SESSION_STATE.RUNNING, SESSION_STATE.IDLE]);
	});

	it("rechaza un segundo envio mientras hay un turno en curso", async () => {
		const executor = makeFakeExecutor();
		const session = new AgentSession({ executor });

		expect(await session.send("primero")).toBe(true);
		expect(await session.send("segundo")).toBe(false);
		expect(executor.start).toHaveBeenCalledTimes(1);

		executor.emit("exit", "0");
		expect(await session.send("tercero")).toBe(true);
	});

	it("rechaza texto vacio o solo espacios sin lanzar proceso", async () => {
		const executor = makeFakeExecutor();
		const session = new AgentSession({ executor });

		expect(await session.send("")).toBe(false);
		expect(await session.send("   \n\t ")).toBe(false);
		expect(executor.start).not.toHaveBeenCalled();
	});

	it("informa por onError si falta el executor nativo", async () => {
		const errors = [];
		const session = new AgentSession({
			executor: null,
			onError: (e) => errors.push(e),
		});

		expect(await session.send("hola")).toBe(false);
		expect(errors).toHaveLength(1);
		expect(errors[0].message).toContain("Executor nativo no disponible");
		expect(session.state).toBe(SESSION_STATE.IDLE);
	});

	it("vuelve a idle si el spawn falla", async () => {
		const executor = makeFakeExecutor({ failStart: true });
		const errors = [];
		const session = new AgentSession({ executor, onError: (e) => errors.push(e) });

		expect(await session.send("hola")).toBe(false);
		expect(session.state).toBe(SESSION_STATE.IDLE);
		expect(errors[0].message).toContain("fallo de spawn");
	});
});

describe("AgentSession - decodificacion de eventos", () => {
	it("propaga init, pasos y resultado del stream real", async () => {
		const executor = makeFakeExecutor();
		const seen = { init: null, steps: [], result: null };
		const session = new AgentSession({
			executor,
			onInit: (caps) => { seen.init = caps; },
			onStep: (step) => { seen.steps.push(step.index); },
			onResult: (r) => { seen.result = r; },
		});

		await session.send("di hola");
		executor.emitFixture("agy-print-stream-json.ndjson");
		executor.emit("exit", "0");

		expect(seen.init.cwd).toBe("/home/studio/workspace");
		expect(seen.init.permissionMode).toBe("request-review");
		expect(seen.steps).toEqual([0, 1, 1, 1]);
		expect(seen.result.status).toBe("SUCCESS");
		expect(seen.result.response).toBe("¡Hola! ¿En qué puedo ayudarte hoy?\n");
	});

	it("captura el conversation_id y lo reinyecta en el turno siguiente", async () => {
		const executor = makeFakeExecutor();
		const session = new AgentSession({ executor });

		await session.send("primer turno");
		executor.emitFixture("agy-print-stream-json.ndjson");
		executor.emit("exit", "0");

		expect(session.conversationId).toBe("b3a987ef-7ac3-4eaa-94ab-24230c7aaf8a");

		await session.send("segundo turno");
		expect(executor.calls[1].command)
			.toContain("'--conversation' 'b3a987ef-7ac3-4eaa-94ab-24230c7aaf8a'");
	});

	it("newConversation() descarta el contexto", async () => {
		const executor = makeFakeExecutor();
		const session = new AgentSession({ executor });

		await session.send("primero");
		executor.emitFixture("agy-print-stream-json.ndjson");
		executor.emit("exit", "0");

		session.newConversation();
		expect(session.conversationId).toBeNull();

		await session.send("otro");
		expect(executor.calls[1].command).not.toContain("--conversation");
	});

	it("propaga un result en ERROR conservando el contenido util", async () => {
		const executor = makeFakeExecutor();
		let result = null;
		const session = new AgentSession({ executor, onResult: (r) => { result = r; } });

		await session.send("lista archivos");
		executor.emitFixture("agy-print-stream-json-tools.ndjson");
		executor.emit("exit", "0");

		expect(result.isError).toBe(true);
		expect(result.error).toContain("UNAVAILABLE (code 503)");
		expect(result.response).toContain("vacío");
		expect(result.synthesized).toBeUndefined();
	});
});

describe("AgentSession - robustez", () => {
	// Sin esto la vista se quedaria en 'pensando' para siempre cuando agy muere.
	it("sintetiza un resultado de error si el proceso muere sin emitir result", async () => {
		const executor = makeFakeExecutor();
		let result = null;
		const session = new AgentSession({ executor, onResult: (r) => { result = r; } });

		await session.send("hola");
		executor.emit("stderr", "agy: segmentation fault");
		executor.emit("exit", "139");

		expect(result).not.toBeNull();
		expect(result.synthesized).toBe(true);
		expect(result.isError).toBe(true);
		expect(result.error).toContain("segmentation fault");
		expect(session.state).toBe(SESSION_STATE.IDLE);
	});

	it("el resultado sintetizado incluye el texto parcial ya emitido", async () => {
		const executor = makeFakeExecutor();
		let result = null;
		const session = new AgentSession({ executor, onResult: (r) => { result = r; } });

		await session.send("hola");
		executor.emit("stdout", '{"event":"step_update","step_update":{"conversation_id":"c1","step_index":0,"state":"ACTIVE","step_type":"agent_response","text_delta":"a medio "}}');
		executor.emit("stdout", '{"event":"step_update","step_update":{"conversation_id":"c1","step_index":0,"state":"ACTIVE","step_type":"agent_response","text_delta":"escribir"}}');
		executor.emit("exit", "1");

		expect(result.synthesized).toBe(true);
		expect(result.response).toBe("a medio escribir");
		expect(result.error).toContain("sin emitir resultado");
	});

	it("abort() detiene el proceso y libera el estado", async () => {
		const executor = makeFakeExecutor();
		const session = new AgentSession({ executor });

		await session.send("algo largo");
		expect(session.isRunning).toBe(true);

		expect(await session.abort()).toBe(true);
		expect(executor.stop).toHaveBeenCalledWith("uuid-1");
		expect(session.isRunning).toBe(false);

		// Un abort sin turno activo no revienta.
		expect(await session.abort()).toBe(false);
	});

	it("una linea corrupta no aborta el turno", async () => {
		const executor = makeFakeExecutor();
		const malformed = [];
		let result = null;
		const session = new AgentSession({
			executor,
			onMalformed: (line) => malformed.push(line),
			onResult: (r) => { result = r; },
		});

		await session.send("hola");
		executor.emit("stdout", "{ basura sin cerrar y sin sentido");
		executor.emitFixture("agy-print-stream-json.ndjson");
		executor.emit("exit", "0");

		expect(malformed.length).toBeGreaterThan(0);
		expect(result.status).toBe("SUCCESS");
	});

	it("setModel y setEffort llegan al argv del turno", async () => {
		const executor = makeFakeExecutor();
		const session = new AgentSession({ executor });

		session.setModel("claude-sonnet-4-6");
		session.setEffort("high");
		await session.send("hola");

		expect(executor.calls[0].command).toContain("'--model' 'claude-sonnet-4-6'");
		expect(executor.calls[0].command).toContain("'--effort' 'high'");
	});

	it("un mensaje sin prefijo reconocido se trata como stdout", async () => {
		const executor = makeFakeExecutor();
		let result = null;
		const session = new AgentSession({ executor, onResult: (r) => { result = r; } });

		await session.send("hola");
		executor.emit("unknown", '{"event":"result","result":{"status":"SUCCESS","response":"ok"}}');
		executor.emit("exit", "0");

		expect(result.status).toBe("SUCCESS");
		expect(result.synthesized).toBeUndefined();
	});
});
