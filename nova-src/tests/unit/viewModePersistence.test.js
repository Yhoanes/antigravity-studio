// @vitest-environment happy-dom

import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import {
    VIEW_MODE_STORAGE_KEY,
    VIEW_MODE_CHAT,
    VIEW_MODE_TERMINAL,
    resolveInitialViewMode,
    CleanAgentTerminal
} from "../../src/antigravity2/CleanAgentTerminal.js";

describe("SPEC-055: View Mode Persistence & Default Chat View", () => {
    let originalRAF;
    let originalSetTimeout;

    beforeEach(() => {
        localStorage.clear();
        vi.restoreAllMocks();
        originalRAF = globalThis.requestAnimationFrame;
        originalSetTimeout = globalThis.setTimeout;
    });

    afterEach(() => {
        globalThis.requestAnimationFrame = originalRAF;
        globalThis.setTimeout = originalSetTimeout;
        document.body.innerHTML = "";
    });

    describe("resolveInitialViewMode contract (AC-PERSIST-001)", () => {
        it("resuelve a 'chat' por defecto cuando localStorage está vacío", () => {
            expect(resolveInitialViewMode()).toBe(VIEW_MODE_CHAT);
        });

        it("resuelve a 'chat' con valores nulos, indefinidos o vacíos", () => {
            expect(resolveInitialViewMode(null)).toBe(VIEW_MODE_CHAT);
            expect(resolveInitialViewMode(undefined)).toBe(VIEW_MODE_CHAT);

            localStorage.setItem(VIEW_MODE_STORAGE_KEY, "");
            expect(resolveInitialViewMode()).toBe(VIEW_MODE_CHAT);
        });

        it("resuelve a 'chat' ante valores corruptos o desconocidos", () => {
            localStorage.setItem(VIEW_MODE_STORAGE_KEY, "invalido");
            expect(resolveInitialViewMode()).toBe(VIEW_MODE_CHAT);

            localStorage.setItem(VIEW_MODE_STORAGE_KEY, "random_string_123");
            expect(resolveInitialViewMode()).toBe(VIEW_MODE_CHAT);
        });

        it("resuelve a 'chat' explícitamente cuando está guardado 'chat'", () => {
            localStorage.setItem(VIEW_MODE_STORAGE_KEY, VIEW_MODE_CHAT);
            expect(resolveInitialViewMode()).toBe(VIEW_MODE_CHAT);
        });

        it("resuelve a 'terminal' ÚNICAMENTE cuando está explícitamente guardado 'terminal'", () => {
            localStorage.setItem(VIEW_MODE_STORAGE_KEY, VIEW_MODE_TERMINAL);
            expect(resolveInitialViewMode()).toBe(VIEW_MODE_TERMINAL);
        });

        it("tolera excepciones si el acceso a localStorage falla (AC-PERSIST-001)", () => {
            const brokenStorage = {
                getItem: vi.fn(() => {
                    throw new Error("SecurityError: Access denied");
                })
            };
            expect(resolveInitialViewMode(brokenStorage)).toBe(VIEW_MODE_CHAT);
        });
    });

    describe("Persistencia en switchToChatView y switchToTerminalView", () => {
        it("persiste 'chat' en localStorage al invocar switchToChatView (AC-TOGGLE-002)", async () => {
            const terminalInstance = new CleanAgentTerminal();
            terminalInstance.containerEl = document.createElement("div");
            terminalInstance.floatingPill = { hide: vi.fn(), show: vi.fn() };
            terminalInstance.agentChatView = {
                show: vi.fn(),
                hide: vi.fn()
            };

            const result = await terminalInstance.switchToChatView();
            expect(result).toBe(true);
            expect(localStorage.getItem(VIEW_MODE_STORAGE_KEY)).toBe(VIEW_MODE_CHAT);
            expect(terminalInstance.containerEl.style.display).toBe("none");
            expect(terminalInstance.floatingPill.hide).toHaveBeenCalled();
            expect(terminalInstance.agentChatView.show).toHaveBeenCalled();
        });

        it("persiste 'terminal' en localStorage y ejecuta reflow en 2 fases al invocar switchToTerminalView (AC-TOGGLE-001)", () => {
            const terminalInstance = new CleanAgentTerminal();
            terminalInstance.containerEl = document.createElement("div");
            terminalInstance.containerEl.style.display = "none";
            terminalInstance.floatingPill = { hide: vi.fn(), show: vi.fn() };
            terminalInstance.agentChatView = {
                show: vi.fn(),
                hide: vi.fn()
            };
            terminalInstance.fitAddon = { fit: vi.fn() };
            terminalInstance.terminal = {
                cols: 80,
                rows: 24,
                refresh: vi.fn(),
                focus: vi.fn()
            };
            terminalInstance.notifyServerResize = vi.fn();

            const rafCallbacks = [];
            globalThis.requestAnimationFrame = vi.fn((cb) => {
                rafCallbacks.push(cb);
                return 1;
            });
            const timeoutCallbacks = [];
            globalThis.setTimeout = vi.fn((cb, delay) => {
                timeoutCallbacks.push({ cb, delay });
                return 2;
            });

            const result = terminalInstance.switchToTerminalView();
            expect(result).toBe(true);
            expect(localStorage.getItem(VIEW_MODE_STORAGE_KEY)).toBe(VIEW_MODE_TERMINAL);
            expect(terminalInstance.containerEl.style.display).toBe("");
            expect(terminalInstance.agentChatView.hide).toHaveBeenCalled();
            expect(terminalInstance.floatingPill.show).toHaveBeenCalled();
            expect(terminalInstance.terminal.focus).toHaveBeenCalled();

            // Pulso 1: requestAnimationFrame
            expect(globalThis.requestAnimationFrame).toHaveBeenCalled();
            expect(rafCallbacks.length).toBeGreaterThan(0);
            rafCallbacks[0]();
            expect(terminalInstance.fitAddon.fit).toHaveBeenCalledTimes(1);
            expect(terminalInstance.notifyServerResize).toHaveBeenCalledWith(80, 24);
            expect(terminalInstance.terminal.refresh).toHaveBeenCalledWith(0, 23);

            // Pulso 2: setTimeout a 150ms
            const pulse150 = timeoutCallbacks.find(t => t.delay === 150);
            expect(pulse150).toBeDefined();
            pulse150.cb();
            expect(terminalInstance.fitAddon.fit).toHaveBeenCalledTimes(2);
            expect(terminalInstance.notifyServerResize).toHaveBeenCalledTimes(2);
            expect(terminalInstance.terminal.refresh).toHaveBeenCalledTimes(2);
        });
    });

    describe("Ciclo de vida en mount() y Modos Iniciales (AC-STARTUP-001 / AC-STARTUP-002)", () => {
        it("[AC-STARTUP-001] Sin configuración previa en localStorage, inicia en modo Chat", async () => {
            // localStorage vacío
            expect(localStorage.getItem(VIEW_MODE_STORAGE_KEY)).toBeNull();

            const terminalInstance = new CleanAgentTerminal();
            // Mockeamos initTerminal y connect para aislar el test de lifecycle DOM
            terminalInstance.initTerminal = vi.fn();
            terminalInstance.connect = vi.fn();
            terminalInstance.switchToChatView = vi.fn().mockResolvedValue(true);

            const root = document.createElement("div");
            terminalInstance.mount(root);

            // Contenedor terminal debe estar oculto
            expect(terminalInstance.containerEl.style.display).toBe("none");
            // Se debe haber invocado switchToChatView
            expect(terminalInstance.switchToChatView).toHaveBeenCalled();
            // initTerminal debe haberse llamado (PTY keepalive)
            expect(terminalInstance.initTerminal).toHaveBeenCalled();
        });

        it("[AC-STARTUP-002] Con 'terminal' en storage, inicia en modo Terminal clásico", () => {
            localStorage.setItem(VIEW_MODE_STORAGE_KEY, VIEW_MODE_TERMINAL);

            const terminalInstance = new CleanAgentTerminal();
            terminalInstance.initTerminal = vi.fn();
            terminalInstance.connect = vi.fn();
            terminalInstance.switchToChatView = vi.fn();

            const root = document.createElement("div");
            terminalInstance.mount(root);

            // Contenedor terminal NO debe estar display: none
            expect(terminalInstance.containerEl.style.display).not.toBe("none");
            // No debe haberse invocado switchToChatView
            expect(terminalInstance.switchToChatView).not.toHaveBeenCalled();
            // agentChatView debe permanecer null / no inicializado
            expect(terminalInstance.agentChatView).toBeNull();
        });

        it("[AC-TOGGLE-002] Conmutador en Top App Bar (#chat-view-btn / #top-bar-to-chat) activa switchToChatView", () => {
            localStorage.setItem(VIEW_MODE_STORAGE_KEY, VIEW_MODE_TERMINAL);

            const terminalInstance = new CleanAgentTerminal();
            terminalInstance.initTerminal = vi.fn();
            terminalInstance.connect = vi.fn();
            terminalInstance.switchToChatView = vi.fn().mockResolvedValue(true);

            const root = document.createElement("div");
            terminalInstance.mount(root);

            // Botón canónico #chat-view-btn
            const chatBtn = terminalInstance.topBarEl.querySelector("#chat-view-btn");
            expect(chatBtn).toBeDefined();
            expect(chatBtn).not.toBeNull();

            // Debe responder a click
            chatBtn.click();
            expect(terminalInstance.switchToChatView).toHaveBeenCalledTimes(1);

            // Soporte retrocompatible de selector #top-bar-to-chat
            const legacyBtn = terminalInstance.topBarEl.querySelector("#top-bar-to-chat");
            expect(legacyBtn).toBe(chatBtn);
            legacyBtn.click();
            expect(terminalInstance.switchToChatView).toHaveBeenCalledTimes(2);
        });

        it("[AC-COEX-001] Keepalive: connect() se programa en background aun en modo chat", () => {
            localStorage.setItem(VIEW_MODE_STORAGE_KEY, VIEW_MODE_CHAT);

            const timeoutSpies = [];
            globalThis.setTimeout = vi.fn((cb, ms) => {
                timeoutSpies.push({ cb, ms });
                return 999;
            });

            const terminalInstance = new CleanAgentTerminal();
            terminalInstance.initTerminal = vi.fn();
            terminalInstance.connect = vi.fn();
            terminalInstance.switchToChatView = vi.fn().mockResolvedValue(true);

            const root = document.createElement("div");
            terminalInstance.mount(root);

            // Verifica que se programa connect en background (50ms)
            const connectTimer = timeoutSpies.find(t => t.ms === 50);
            expect(connectTimer).toBeDefined();
            connectTimer.cb();
            expect(terminalInstance.connect).toHaveBeenCalled();
        });
    });
});
