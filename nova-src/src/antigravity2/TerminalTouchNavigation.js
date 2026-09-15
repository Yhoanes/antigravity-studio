/**
 * TerminalTouchNavigation - Controlador Gestual Cuadridireccional (4-Way D-Pad)
 * Conforme a SPEC-030 y SPEC-031: ArrowUp (\x1b[A), ArrowDown (\x1b[B), ArrowLeft (\x1b[D), ArrowRight (\x1b[C)
 * con Bloqueo Cinemático de Eje (Axis-Locking / lockedAxis) y Pegado desde Portapapeles por Long-Press (400ms) o Doble Toque.
 */

export class TerminalTouchNavigation {
    /**
     * @param {HTMLElement} element
     * @param {Object} options
     * @param {number} [options.swipeThreshold=28] - Desplazamiento en px por tecla emitida
     * @param {number} [options.deadzone=10] - Zona muerta para determinar el eje dominante
     * @param {number} [options.longPressMs=400] - Duración de pulsación para portapapeles
     * @param {Function} options.onArrowUp - Callback ArrowUp (\x1b[A, deslizar hacia abajo)
     * @param {Function} options.onArrowDown - Callback ArrowDown (\x1b[B, deslizar hacia arriba)
     * @param {Function} options.onArrowLeft - Callback ArrowLeft (\x1b[D, deslizar hacia la izquierda)
     * @param {Function} options.onArrowRight - Callback ArrowRight (\x1b[C, deslizar hacia la derecha)
     * @param {Function} options.onPaste - Callback pegado desde portapapeles
     */
    constructor(element, options = {}) {
        this.element = element;
        this.options = {
            swipeThreshold: options.swipeThreshold || 28,
            deadzone: options.deadzone || 10,
            longPressMs: options.longPressMs || 400,
            onArrowUp: options.onArrowUp || (() => {}),
            onArrowDown: options.onArrowDown || (() => {}),
            onArrowLeft: options.onArrowLeft || (() => {}),
            onArrowRight: options.onArrowRight || (() => {}),
            onPaste: options.onPaste || (() => Promise.resolve())
        };

        this.startX = 0;
        this.startY = 0;
        this.lastX = 0;
        this.lastY = 0;
        this.accumulatedDeltaX = 0;
        this.accumulatedDeltaY = 0;
        this.axisLock = null; // null | 'horizontal' | 'vertical'
        this.lockedAxis = null; // alias para compatibilidad SPEC-031
        this.longPressTimer = null;
        this.isLongPressTriggered = false;
        this.lastTapTime = 0;

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
        this.element.addEventListener("touchend", this.onTouchEnd, { passive: true });
        this.element.addEventListener("touchcancel", this.onTouchCancel, { passive: true });
    }

    onTouchStart(e) {
        if (e.touches.length !== 1) return;
        const touch = e.touches[0];
        this.startX = touch.clientX;
        this.startY = touch.clientY;
        this.lastX = touch.clientX;
        this.lastY = touch.clientY;
        this.accumulatedDeltaX = 0;
        this.accumulatedDeltaY = 0;
        this.axisLock = null;
        this.lockedAxis = null;
        this.isLongPressTriggered = false;

        // Detección de Doble Toque (Double-Tap <300ms)
        const now = Date.now();
        if (now - this.lastTapTime < 300) {
            this.cancelLongPress();
            this.options.onPaste();
            this.lastTapTime = 0;
            return;
        }
        this.lastTapTime = now;

        // Iniciar Temporizador de Pulsación Prolongada (longPress 400ms)
        this.longPressTimer = setTimeout(() => {
            this.isLongPressTriggered = true;
            if (navigator.vibrate) {
                try { navigator.vibrate(50); } catch (err) {}
            }
            this.options.onPaste();
        }, this.options.longPressMs);
    }

    onTouchMove(e) {
        if (e.touches.length !== 1) return;
        const touch = e.touches[0];
        const totalDistance = Math.hypot(touch.clientX - this.startX, touch.clientY - this.startY);

        // Cancelar longPress si hay arrastre perceptible (>8px)
        if (totalDistance > 8) {
            this.cancelLongPress();
        }

        const deltaXCurrent = touch.clientX - this.lastX;
        const deltaYCurrent = touch.clientY - this.lastY;

        // Zona de desambiguación y bloqueo de eje (Axis-Locking):
        // Si this.axisLock === null y totalDistance >= deadzone (10px), determinar eje dominante
        if (this.axisLock === null && totalDistance >= this.options.deadzone) {
            const absDx = Math.abs(touch.clientX - this.startX);
            const absDy = Math.abs(touch.clientY - this.startY);
            if (absDx > absDy) {
                this.axisLock = "horizontal";
                this.lockedAxis = "horizontal";
            } else {
                this.axisLock = "vertical";
                this.lockedAxis = "vertical";
            }
        }

        // Si aún no se supera la zona muerta, no emitir flechas
        if (this.axisLock === null) {
            this.lastX = touch.clientX;
            this.lastY = touch.clientY;
            return;
        }

        if (this.axisLock === "horizontal") {
            this.accumulatedDeltaX += deltaXCurrent;
            while (Math.abs(this.accumulatedDeltaX) >= this.options.swipeThreshold) {
                if (this.accumulatedDeltaX > 0) {
                    // Deslizar a la derecha -> ArrowRight (\x1b[C)
                    this.options.onArrowRight();
                    this.accumulatedDeltaX -= this.options.swipeThreshold;
                } else {
                    // Deslizar a la izquierda -> ArrowLeft (\x1b[D)
                    this.options.onArrowLeft();
                    this.accumulatedDeltaX += this.options.swipeThreshold;
                }
            }
        } else if (this.axisLock === "vertical") {
            this.accumulatedDeltaY += deltaYCurrent;
            while (Math.abs(this.accumulatedDeltaY) >= this.options.swipeThreshold) {
                if (this.accumulatedDeltaY > 0) {
                    // Deslizar hacia abajo -> ArrowUp (\x1b[A)
                    this.options.onArrowUp();
                    this.accumulatedDeltaY -= this.options.swipeThreshold;
                } else {
                    // Deslizar hacia arriba -> ArrowDown (\x1b[B)
                    this.options.onArrowDown();
                    this.accumulatedDeltaY += this.options.swipeThreshold;
                }
            }
        }

        this.lastX = touch.clientX;
        this.lastY = touch.clientY;
    }

    onTouchEnd(e) {
        this.cancelLongPress();
        this.axisLock = null;
        this.lockedAxis = null;
        this.accumulatedDeltaX = 0;
        this.accumulatedDeltaY = 0;
    }

    onTouchCancel(e) {
        this.cancelLongPress();
        this.axisLock = null;
        this.lockedAxis = null;
        this.accumulatedDeltaX = 0;
        this.accumulatedDeltaY = 0;
    }

    cancelLongPress() {
        if (this.longPressTimer) {
            clearTimeout(this.longPressTimer);
            this.longPressTimer = null;
        }
    }

    destroy() {
        this.cancelLongPress();
        if (!this.element) return;
        this.element.removeEventListener("touchstart", this.onTouchStart);
        this.element.removeEventListener("touchmove", this.onTouchMove);
        this.element.removeEventListener("touchend", this.onTouchEnd);
        this.element.removeEventListener("touchcancel", this.onTouchCancel);
        this.element = null;
    }
}

export default TerminalTouchNavigation;
