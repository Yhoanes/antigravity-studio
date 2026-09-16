# SPEC-052: Hardening del Detector de Menú y Scroll Tradicional

| Metadato | Valor |
| :--- | :--- |
| **Identificador** | `SPEC-052` |
| **Título** | Endurecimiento del Detector de Menú Interactivo y Scroll Tradicional |
| **Autor** | `@spec-architect` |
| **Estado** | `APPROVED FOR IMPLEMENTATION` |
| **Fecha de Creación** | 2026-09-16 |
| **Versión de Release** | `v2.7.0` (VersionCode: `20700`) |
| **Artefacto Binario Target** | `GoogleAntigravity-v2.7.0-ARM64.apk` ($\le 42.0\,\text{MB}$) |
| **Dispositivo Objetivo** | Xiaomi Pad 6 (Qualcomm Snapdragon 870, ARM64-v8a, 6-8 GB RAM, 11" 2.8K $2880\times 1800$, 144Hz, Xiaomi HyperOS / Android 13/14) y Ecosistema Android Móvil (API 26+) |
| **Runtime Target** | Cordova Android WebView / PRoot Linux Sandbox / AXS PTY Daemon (puerto 8767) / Xterm.js v5+ / Google Antigravity CLI (`agy`) |
| **Módulos Afectados** | `nova-src/src/antigravity2/TerminalTouchNavigation.js`, `nova-src/src/antigravity2/CleanAgentTerminal.js`, `nova-src/src/antigravity2/clean-terminal.scss`, `nova-src/config.xml`, `nova-src/package.json`, `harness/test_clean_agent_terminal.sh` |

---

## 1. Contexto y Objetivos

En las pruebas de la versión `v2.6.2` en la Xiaomi Pad 6, se identificaron varios problemas críticos de usabilidad (UX) relacionados con la interacción táctil en la terminal. El más notable es la incapacidad del usuario para visualizar el historial del chat mediante deslizamiento hacia arriba debido a una inversión en la dirección del scroll (Natural vs. Traditional Scrolling).

Un diagnóstico exhaustivo reveló las siguientes deficiencias:
1. **Polaridad de Scroll Invertida.** La terminal implementa "Natural Scrolling", pero el usuario espera "Traditional Scrolling" (deslizar hacia arriba = subir en el documento/ver historial).
2. **Falsos Positivos en la Detección de Menús.** El detector de menús interactivos (`isInteractiveMenu()`) es excesivamente sensible y reacciona a contenido normal de texto del agente que contiene viñetas o patrones que coinciden con elementos de menú de consola, como círculos (●/○).
3. **Inestabilidad del Gesto de Dos Dedos.** El deslizamiento simultáneo con dos dedos carece de robustez y puede ser activado por la palma de la mano o toques asíncronos.
4. **Carencia de Feedback de Scroll.** No hay un indicador visual que comunique la posición de la ventana gráfica en el historial.

Esta especificación detalla las correcciones precisas para estas incidencias.

---

## 2. Arquitectura de la Solución

### Diagramas de Decisión: Detección de Gestos y Menús

**ANTES (Problemas Activos)**
```mermaid
graph TD
    A[Touch Event] --> B{is 2 fingers?}
    B -- Yes --> C[Map drag to Arrow keys<br>Down=Up / Up=Down]
    B -- No --> D{isInteractiveMenu()}
    D -- Yes (false pos via ●/○) --> E[Activate D-Pad]
    D -- No --> F[scrollLines(-deltaY)]
    F --> G[Natural Scroll<br>Drag Up = Scroll Down!]
```

**DESPUÉS (Flujo Corregido)**
```mermaid
graph TD
    A[Touch Event] --> B1{2 fingers & diff < 100ms & not in edge?}
    B1 -- Yes --> C[Map drag to Arrow keys<br>UP=ArrowUp / DOWN=ArrowDown]
    B1 -- No --> D1{isInteractiveMenu() & no ●/○ & only last 5 lines}
    D1 -- Yes --> E[Activate D-Pad]
    D1 -- No --> F1[scrollLines(deltaY)]
    F1 --> G1[Traditional Scroll<br>Drag Up = Scroll Up/History]
    G1 --> H{viewportY < baseY}
    H -- Yes --> I[Show Ephemeral Scrollbar]
    H -- No --> J[Hide Scrollbar]
```

---

## 3. Contratos de Interfaz

```javascript
// Detección robusta de menús interactivos (MENU-01, MENU-03)
// Elimina los patrones de "●|○" del array EXPLICIT_PATTERNS
// Añade validación de que (Use arrow keys) o ❯ estén en el fondo.
class CleanAgentTerminal {
    _isInteractiveMenu() {
        // ...
        // Requisito 1: viewport debe estar al fondo
        if (viewportY !== baseY) return false;
        
        // Requisito 2: patrones sólo en las últimas 5 líneas VISIBLES
        // Se omiten los círculos ●|○
        // ...
    }
}

// Navegación Táctil (SCROLL-01, SCROLL-02, 2F-01, 2F-02)
class TerminalTouchNavigation {
    
    // Inversión de polaridad: Traditional Scrolling
    scrollByPixels(deltaY) {
        // ...
        // ANTES: this.terminal.scrollLines(-lines);
        // DESPUÉS: scrollLines(lines);
        this.terminal.scrollLines(lines);
        // ...
    }
    
    // El inicio de momentum usará la misma dirección implícitamente
    startMomentum() {
        // ...
    }

    // Manejo de gesto 2 dedos (ArrowUp/ArrowDown)
    // - Comprobar ventana de 100ms para simultaneidad.
    // - Ignorar eventos a < 15px de los bordes laterales (prevención de palma).
    // - Invertir mapeo: drag UP -> ArrowUp, drag DOWN -> ArrowDown
    onTouchMove(e) {
        // ...
    }

    // Indicador visual efímero (VIS-01, VIS-02)
    _updateScrollbar() {
        // Muestra un thin bar en viewportY < baseY, se oculta tras 1.5s
    }
}
```

---

## 4. Criterios de Aceptación Verificables

| Identificador | Módulo | Criterio / Requisito | Método de Verificación |
| :--- | :--- | :--- | :--- |
| `AC-SCROLL-001` | `TerminalTouchNavigation.js` | Scroll 1 dedo hacia arriba (deltaY < 0) resulta en un scrollLines(negativo), mostrando el historial (Traditional Scrolling). | Inspección de `scrollByPixels(deltaY)`. |
| `AC-SCROLL-002` | `TerminalTouchNavigation.js` | Scroll 1 dedo hacia abajo (deltaY > 0) resulta en un scrollLines(positivo), volviendo al prompt de la terminal. | Inspección de `scrollByPixels(deltaY)`. |
| `AC-SCROLL-003` | `TerminalTouchNavigation.js` | Momentum scrolling preserva coherencia direccional respecto al traditional scrolling. | Inspección de `startMomentum()`. |
| `AC-SCROLL-004` | `TerminalTouchNavigation.js` | Gesto 2 dedos arrastrando arriba activa `ArrowUp` (historial de comandos). | Inspección de eventos 2 dedos en `onTouchMove`. |
| `AC-SCROLL-005` | `TerminalTouchNavigation.js` | Gesto 2 dedos arrastrando abajo activa `ArrowDown` (comando siguiente). | Inspección de eventos 2 dedos en `onTouchMove`. |
| `AC-MENU-001` | `CleanAgentTerminal.js` | `isInteractiveMenu()` NO se activa si la salida del agente contiene los caracteres ● u ○. | Verificación de array de patrones eliminados. |
| `AC-MENU-002` | `CleanAgentTerminal.js` | `isInteractiveMenu()` se activa correctamente ante componentes Inquirer genuinos. | Verificación del resto de patrones en JS. |
| `AC-MENU-003` | `CleanAgentTerminal.js` | Los patrones de menú interactivo se limitan y analizan solo dentro del umbral de las últimas 5 líneas visibles. | Inspección de la lógica iterativa de buffers. |
| `AC-2F-001` | `TerminalTouchNavigation.js` | Despliegue simultáneo de 2 dedos requiere una ventana temporal delta de máximo 100ms, descartándose de lo contrario. | Inspección de timestamps en `onTouchStart`. |
| `AC-2F-002` | `TerminalTouchNavigation.js` | Se ignoran toques iniciales dentro del borde lateral de 15px de la pantalla (evitando toques de palma de la mano). | Validación de coordenadas X del TouchEvent. |
| `AC-VIS-001` | `clean-terminal.scss` / JS | Aparece scrollbar efímero (3px `rgba(138,180,248,0.4)`, alineado al borde derecho, `pointer-events: none`) al hacer scroll, únicamente cuando `viewportY < baseY`. | Inspección de manipulación del DOM y SCSS. |
| `AC-VIS-002` | `TerminalTouchNavigation.js` / SCSS | El indicador de scroll desaparece a los 1.5s de detenido el scroll, utilizando una transición de *fade-out*. | Inspección del timer de *debounce* y estilos CSS. |
| `AC-UIX-001` | Global | Ningún uso de emojis en los archivos modificados; solo SVG vectoriales. | Barrido del pipeline de build o inspección estática. |
| `AC-HARNESS-001` | Harness Suite | El sistema contiene compuertas automatizadas (`harness/test_clean_agent_terminal.sh`) que validan exitosamente todo lo descrito para SPEC-052. | Ejecución del script. |
| `AC-BUILD-001` | Build System | Incremento a versión formal `2.7.0` (versionCode `20700`), compilación limpia del artefacto APK menor o igual a 42.0 MB. | Chequeo estático de `config.xml`, `package.json` y logs del build Gradle. |

---

## 5. Restricciones y No-Objetivos

- **Cero emojis.** Aplica estricta política global del proyecto.
- **Sin nuevas dependencias de UI.** Todo implementado con el DOM, JavaScript estándar de ES6 y `xterm.js` actual.
- **Mantenimiento del Momentum Scrolling.** No se alterarán las variables cinemáticas fundamentales ni el amortiguamiento de fricción del momentum scrolling actual salvo el cambio en su polaridad (implícito).
- **No es objetivo** un rediseño del teclado numérico o los D-Pads, solamente el *bypass* por falsos positivos de los menúes.
- El indicador de scroll visual se inyectará sobre la jerarquía del DOM ya existente, *jamás* sustituirá el viewport base renderizado de Xterm.js y debe ser 100% pasivo a nivel punteros (sin bloquear eventos touch del layout inferior).
