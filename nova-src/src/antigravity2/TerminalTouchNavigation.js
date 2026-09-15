/**
 * TerminalTouchNavigation - Controlador Gestual Táctil para Xterm.js (SPEC-030)
 * Provee emulación de teclas de dirección (ArrowUp \x1b[A, ArrowDown \x1b[B)
 * y pegado desde portapapeles por pulsación prolongada (longPress 400ms) o doble toque.
 */

export class TerminalTouchNavigation {
    /**
     * @param {HTMLElement} element
     * @param {Object} options
     * @param {number} [options.swipeThreshold=28]
     * @param {number} [options.longPressMs=400]
     * @param {Function} options.onArrowUp - Callback emisión ArrowUp (\x1b[A)
     * @param {Function} options.onArrowDown - Callback emisión ArrowDown (\x1b[B)
     * @param {Function} options.onPaste - Callback pegado desde portapapeles
     */
    constructor(element, options = {}) {
        this.element = element;
        this.options = {
            swipeThreshold: options.swipeThreshold || 28,
            longPressMs: options.longPressMs || 400,
            onArrowUp: options.onArrowUp || (() => {}),
            onArrowDown: options.onArrowDown || (() => {}),
            onPaste: options.onPaste || (() => {})
        };
        this.startX = 0;
        this.startY = 0;
        this.lastY = 0;
        this.accumulatedDeltaY = 0;
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
        this.lastY = touch.clientY;
        this.accumulatedDeltaY = 0;
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
        const deltaX = Math.abs(touch.clientX - this.startX);
        const deltaYCurrent = touch.clientY - this.lastY;
        const totalDistance = Math.hypot(touch.clientX - this.startX, touch.clientY - this.startY);

        // Cancelar longPress si hay arrastre perceptible (>8px)
        if (totalDistance > 8) {
            this.cancelLongPress();
        }

        // Acumular desplazamiento vertical
        this.accumulatedDeltaY += deltaYCurrent;
        this.lastY = touch.clientY;

        // Emulación de Flechas por umbral calibrado (28px)
        while (Math.abs(this.accumulatedDeltaY) >= this.options.swipeThreshold) {
            if (this.accumulatedDeltaY > 0) {
                // Deslizar hacia abajo -> Emite ArrowUp (\x1b[A)
                this.options.onArrowUp();
                this.accumulatedDeltaY -= this.options.swipeThreshold;
            } else {
                // Deslizar hacia arriba -> Emite ArrowDown (\x1b[B)
                this.options.onArrowDown();
                this.accumulatedDeltaY += this.options.swipeThreshold;
            }
        }
    }

    onTouchEnd(e) {
        this.cancelLongPress();
    }

    onTouchCancel(e) {
        this.cancelLongPress();
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
