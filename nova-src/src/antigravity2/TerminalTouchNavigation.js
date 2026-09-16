/**
 * TerminalTouchNavigation - Controlador Gestual Híbrido Cuadridireccional (SPEC-030, SPEC-031, SPEC-032 & SPEC-035)
 * Soporta Momentum Scrolling táctil en chat a 144Hz, D-Pad contextual inteligente en menús con háptica 25ms,
 * 2 dedos para historial de comandos, y pegado por pulsación prolongada (long-press 400ms) o doble toque.
 */

export class TerminalTouchNavigation {
    /**
     * @param {HTMLElement|Object} elementOrTerminal
     * @param {Object|HTMLElement} optionsOrElement
     * @param {Object} [maybeOptions]
     */
    constructor(elementOrTerminal, optionsOrElement = {}, maybeOptions = {}) {
        let terminal = null;
        let element = null;
        let options = {};

        if (elementOrTerminal && elementOrTerminal.buffer) {
            terminal = elementOrTerminal;
            element = optionsOrElement;
            options = maybeOptions || {};
        } else {
            element = elementOrTerminal;
            options = optionsOrElement || {};
            terminal = options.terminal || null;
        }

        this.terminal = terminal;
        this.element = element;
        this.options = {
            swipeThreshold: options.swipeThreshold || 28,
            deadzone: options.deadzone || 10,
            longPressMs: options.longPressMs || 800,
            friction: options.friction || 0.92,
            minVelocity: options.minVelocity || 0.5,
            onArrowUp: options.onArrowUp || (() => {}),
            onArrowDown: options.onArrowDown || (() => {}),
            onArrowLeft: options.onArrowLeft || (() => {}),
            onArrowRight: options.onArrowRight || (() => {}),
            onPaste: options.onPaste || (() => Promise.resolve())
        };

        // Estado táctil
        this.startX = 0;
        this.startY = 0;
        this.lastX = 0;
        this.lastY = 0;
        this.touchStartTime = 0;
        this.accumulatedDeltaX = 0;
        this.accumulatedDeltaY = 0;
        this.axisLock = null; // null | 'horizontal' | 'vertical'
        this.lockedAxis = null; // alias para compatibilidad SPEC-031
        this.isTwoFingerGesture = false;
        this.longPressTimer = null;
        this.isLongPressTriggered = false;
        this.firstTouchTime = 0;
        this.lastTapTime = 0;

        // Visual Scroll Indicator
        this.scrollIndicator = document.createElement("div");
        this.scrollIndicator.className = "scroll-indicator";
        if (this.element) {
            this.element.appendChild(this.scrollIndicator);
        }
        this.scrollIndicatorTimer = null;

        // Física de Inercia (Momentum Scrolling)
        this.velocity = 0;
        this.velocitySamples = [];
        this.animationId = null;
        this.scrollRemainder = 0;

        this.onTouchStart = this.onTouchStart.bind(this);
        this.onTouchMove = this.onTouchMove.bind(this);
        this.onTouchEnd = this.onTouchEnd.bind(this);
        this.onTouchCancel = this.onTouchCancel.bind(this);

        this.attach();
    }

    attach() {
        if (!this.element) return;
        this.element.addEventListener("touchstart", this.onTouchStart, { passive: false });
        this.element.addEventListener("touchmove", this.onTouchMove, { passive: false });
        this.element.addEventListener("touchend", this.onTouchEnd, { passive: false });
        this.element.addEventListener("touchcancel", this.onTouchCancel, { passive: false });
    }

    getCellHeight() {
        if (!this.element) return 16;
        const screen = this.element.querySelector(".xterm-screen");
        const screenHeight = screen?.getBoundingClientRect().height || 0;
        const rows = this.terminal?.rows || 24;
        return (screenHeight > 0 && rows > 0)
            ? screenHeight / rows
            : (this.terminal?.options?.fontSize || 14) * 1.2;
    }

    /**
     * SPEC-032 & SPEC-035: AC-GESTURE-01
     * Discriminador contextual inteligente: diferencia estrictamente entre el prompt de entrada
     * de texto (chat) y un menú interactivo real (Inquirer, curses, selección CLI).
     */
    isInteractiveMenu() {
        if (!this.terminal || !this.terminal.buffer || !this.terminal.buffer.active) {
            return false;
        }
        const buffer = this.terminal.buffer.active;

        // Buffer alterno siempre corresponde a aplicaciones interactivas (curses/vim/less)
        if (buffer.type === "alternate") return true;

        // Si el usuario se ha desplazado hacia arriba en el búfer, está leyendo chat
        if (buffer.viewportY < buffer.baseY) return false;

        const rows = this.terminal.rows || 24;
        const endLine = buffer.baseY + rows;
        const startLine = Math.max(0, endLine - 5);
        const lines = [];

        for (let i = startLine; i < endLine; i++) {
            const line = buffer.getLine(i);
            if (line) lines.push(line.translateToString(true));
        }

        const fullText = lines.join("\n");

        // 1. Patrones explícitos de instrucciones de selección
        const EXPLICIT_PATTERNS = [
            /\(Use arrow keys\)/i,
            /\(Press <space> to select\)/i,
            /\? Select/i,
            /\? Choose/i,
            /\[y\/N\]/i,
            /\[Y\/n\]/i
        ];

        if (EXPLICIT_PATTERNS.some(p => p.test(fullText))) {
            return true;
        }

        // 2. Discriminación de prompt de entrada: si ❯ solo aparece al final sin opciones hermanas, es chat
        const chevronLines = lines.filter(l => l.includes("❯"));
        if (chevronLines.length === 1) {
            const lastLine = lines[lines.length - 1] || "";
            const prevLine = lines[lines.length - 2] || "";
            // Si el chevron está en la línea de comando y la anterior no es menú, es prompt de texto
            if (lastLine.includes("❯") && !prevLine.trim().startsWith("  ")) {
                return false;
            }
        }

        // 3. Menú multi-opción: presencia de ❯ en una línea acompañada de opciones con sangría '  '
        const hasMenuStructure = lines.some((l, idx) => 
            l.includes("❯") && (lines[idx + 1]?.startsWith("  ") || lines[idx - 1]?.startsWith("  "))
        );

        return hasMenuStructure;
    }

    onTouchStart(e) {
        this.stopMomentum();

        // Gesto con 2 Dedos: Modo Historial Intencional
        if (e.touches.length === 2) {
            const timeSinceFirst = performance.now() - this.firstTouchTime;
            if (timeSinceFirst > 100) return;

            const touch1 = e.touches[0];
            const touch2 = e.touches[1];
            if (touch1.clientX < 15 || touch1.clientX > window.innerWidth - 15 ||
                touch2.clientX < 15 || touch2.clientX > window.innerWidth - 15) {
                return;
            }

            this.cancelLongPress();
            this.isTwoFingerGesture = true;
            this.startY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
            this.lastY = this.startY;
            this.accumulatedDeltaY = 0;
            return;
        }

        if (e.touches.length !== 1) return;
        this.isTwoFingerGesture = false;
        this.firstTouchTime = performance.now();

        const touch = e.touches[0];
        this.startX = touch.clientX;
        this.startY = touch.clientY;
        this.lastX = touch.clientX;
        this.lastY = touch.clientY;
        this.touchStartTime = performance.now();
        this.accumulatedDeltaX = 0;
        this.accumulatedDeltaY = 0;
        this.axisLock = null;
        this.lockedAxis = null;
        this.velocity = 0;
        this.velocitySamples = [];
        this.scrollRemainder = 0;
        this.isLongPressTriggered = false;

        // Doble Toque descartado para evitar pegados accidentales
        const now = Date.now();
        this.lastTapTime = now;

        // Temporizador Long-Press (800ms)
        this.longPressTimer = setTimeout(() => {
            this.isLongPressTriggered = true;
            if (navigator.vibrate) {
                try { navigator.vibrate(50); } catch (err) {}
            }
            this.options.onPaste();
        }, this.options.longPressMs);
    }

    onTouchMove(e) {
        // Gesto de Historial con 2 Dedos (SPEC-032: AC-CHAT-04)
        if (this.isTwoFingerGesture && e.touches.length === 2) {
            e.preventDefault();
            const currentY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
            const deltaY = currentY - this.lastY;
            this.accumulatedDeltaY += deltaY;
            this.lastY = currentY;

            while (Math.abs(this.accumulatedDeltaY) >= this.options.swipeThreshold) {
                if (this.accumulatedDeltaY > 0) {
                    this.options.onArrowUp();
                    this.accumulatedDeltaY -= this.options.swipeThreshold;
                } else {
                    this.options.onArrowDown();
                    this.accumulatedDeltaY += this.options.swipeThreshold;
                }
            }
            return;
        }

        if (e.touches.length !== 1) return;
        const touch = e.touches[0];
        const currentX = touch.clientX;
        const currentY = touch.clientY;
        const totalDist = Math.hypot(currentX - this.startX, currentY - this.startY);

        if (totalDist > 8) {
            this.cancelLongPress();
        }

        // Bloqueo Cinemático de Eje (Axis-Locking) tras superar la zona muerta (10px)
        if (!this.axisLock && totalDist >= this.options.deadzone) {
            const absDx = Math.abs(currentX - this.startX);
            const absDy = Math.abs(currentY - this.startY);
            this.axisLock = absDx > absDy ? "horizontal" : "vertical";
            this.lockedAxis = this.axisLock;
        }

        if (!this.axisLock) {
            this.lastX = currentX;
            this.lastY = currentY;
            return;
        }

        const deltaXCurrent = currentX - this.lastX;
        const deltaYCurrent = currentY - this.lastY;
        const now = performance.now();
        const deltaTime = now - this.touchStartTime;

        if (this.axisLock === "horizontal") {
            // Modo Horizontal (4-Way D-Pad): ArrowLeft / ArrowRight
            e.preventDefault();
            this.accumulatedDeltaX += deltaXCurrent;
            while (Math.abs(this.accumulatedDeltaX) >= this.options.swipeThreshold) {
                if (this.accumulatedDeltaX > 0) {
                    this.options.onArrowRight();
                    this.accumulatedDeltaX -= this.options.swipeThreshold;
                } else {
                    this.options.onArrowLeft();
                    this.accumulatedDeltaX += this.options.swipeThreshold;
                }
            }
        } else if (this.axisLock === "vertical") {
            // Muestreo de velocidad para inercia (Momentum Scrolling)
            if (deltaTime > 0) {
                const instantVelocity = (deltaYCurrent / (deltaTime || 16.6)) * 16.67;
                this.velocitySamples.push(instantVelocity);
                if (this.velocitySamples.length > 5) this.velocitySamples.shift();
            }

            if (this.isInteractiveMenu()) {
                // Modo D-Pad en Menú Selector interactivo (ArrowUp / ArrowDown)
                e.preventDefault();
                this.accumulatedDeltaY += deltaYCurrent;
                while (Math.abs(this.accumulatedDeltaY) >= this.options.swipeThreshold) {
                    if (navigator.vibrate) {
                        try { navigator.vibrate(25); } catch (err) {}
                    }
                    if (this.accumulatedDeltaY > 0) {
                        this.options.onArrowUp();
                        this.accumulatedDeltaY -= this.options.swipeThreshold;
                    } else {
                        this.options.onArrowDown();
                        this.accumulatedDeltaY += this.options.swipeThreshold;
                    }
                }
            } else {
                // Modo Scroll Táctil Nativo de Mensajes del Chat
                e.preventDefault();
                this.scrollByPixels(deltaYCurrent);
            }
        }

        this.lastX = currentX;
        this.lastY = currentY;
        this.touchStartTime = now;
    }

    onTouchEnd(e) {
        this.cancelLongPress();
        this.isTwoFingerGesture = false;

        // Iniciar momentum si estábamos scrolleando en vertical y no es menú
        if (this.axisLock === "vertical" && !this.isInteractiveMenu()) {
            if (this.velocitySamples.length > 0) {
                this.velocity = (this.velocitySamples.reduce((a, b) => a + b, 0) / this.velocitySamples.length) * 1.1;
            }
            if (Math.abs(this.velocity) >= this.options.minVelocity) {
                this.startMomentum();
            }
        }

        this.axisLock = null;
        this.lockedAxis = null;
        this.accumulatedDeltaX = 0;
        this.accumulatedDeltaY = 0;
        this.velocitySamples = [];
    }

    onTouchCancel(e) {
        this.cancelLongPress();
        this.isTwoFingerGesture = false;
        this.axisLock = null;
        this.lockedAxis = null;
        this.accumulatedDeltaX = 0;
        this.accumulatedDeltaY = 0;
        this.stopMomentum();
    }

    scrollByPixels(deltaY) {
        if (!this.terminal || typeof this.terminal.scrollLines !== "function") return;
        const cellHeight = this.getCellHeight();
        if (!cellHeight || cellHeight <= 0) return;

        this.scrollRemainder += deltaY;
        const lines = Math.trunc(this.scrollRemainder / cellHeight);
        if (lines === 0) return;

        // Deslizar hacia abajo (deltaY > 0) -> scroll hacia arriba en historial (lines)
        this.terminal.scrollLines(-lines);
        this.scrollRemainder -= lines * cellHeight;

        this.showScrollIndicator();
    }

    showScrollIndicator() {
        if (!this.terminal || !this.terminal.buffer || !this.terminal.buffer.active) return;
        const buffer = this.terminal.buffer.active;
        if (buffer.viewportY >= buffer.baseY) return;

        const totalRows = buffer.baseY + this.terminal.rows;
        if (totalRows <= this.terminal.rows) return;

        const viewportY = buffer.viewportY;
        const scrollHeight = this.element.clientHeight || window.innerHeight;
        const thumbHeight = Math.max(20, (this.terminal.rows / totalRows) * scrollHeight);
        const thumbTop = (viewportY / buffer.baseY) * (scrollHeight - thumbHeight);

        this.scrollIndicator.style.height = `${thumbHeight}px`;
        this.scrollIndicator.style.top = `${thumbTop}px`;
        this.scrollIndicator.style.opacity = "1";

        if (this.scrollIndicatorTimer) clearTimeout(this.scrollIndicatorTimer);
        this.scrollIndicatorTimer = setTimeout(() => {
            this.scrollIndicator.style.opacity = "0";
        }, 1500);
    }

    startMomentum() {
        this.stopMomentum();
        const step = () => {
            if (Math.abs(this.velocity) < this.options.minVelocity) {
                this.stopMomentum();
                return;
            }
            this.velocity *= this.options.friction;
            this.scrollByPixels(this.velocity);
            this.animationId = requestAnimationFrame(step);
        };
        this.animationId = requestAnimationFrame(step);
    }

    stopMomentum() {
        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
            this.animationId = null;
        }
        this.velocity = 0;
    }

    cancelLongPress() {
        if (this.longPressTimer) {
            clearTimeout(this.longPressTimer);
            this.longPressTimer = null;
        }
    }

    destroy() {
        this.stopMomentum();
        this.cancelLongPress();
        if (!this.element) return;
        this.element.removeEventListener("touchstart", this.onTouchStart);
        this.element.removeEventListener("touchmove", this.onTouchMove);
        this.element.removeEventListener("touchend", this.onTouchEnd);
        this.element.removeEventListener("touchcancel", this.onTouchCancel);
        this.element = null;
        this.terminal = null;
    }
}

export default TerminalTouchNavigation;
