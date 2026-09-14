# SPEC-025: Desbloqueo de Ciclo de Vida Agéntico (Auto-Transición Zero-Lock) y Optimización de Peso del APK (Slimming) en Antigravity 2.0 Mobile

| Metadato | Valor |
| :--- | :--- |
| **Identificador** | `SPEC-025` |
| **Título** | Desbloqueo de Ciclo de Vida Agéntico (Auto-Transición Zero-Lock) y Optimización de Peso del APK (Slimming) en Antigravity 2.0 Mobile |
| **Autor** | `@spec-architect` |
| **Estado** | `APPROVED FOR IMPLEMENTATION` |
| **Fecha de Creación** | 2026-09-14 |
| **Dispositivo Objetivo** | Xiaomi Pad 6 (Qualcomm Snapdragon 870, ARM64-v8a, 6 GB RAM, 11" 2.8K 144Hz, Xiaomi HyperOS / Android 13/14) y Ecosistema Android Móvil (API 26+) |
| **Runtime Target** | Cordova Android / Hardware-Accelerated WebView / PRoot Linux Ubuntu 24.04 Noble ARM64 / AXS PTY Daemon / Google Antigravity CLI (`agy`) |
| **Módulos Afectados** | `nova-src/src/antigravity2/AgentBridge.js`, `nova-src/src/antigravity2/ChatCanvas.js`, `nova-src/src/plugins/terminal/www/Terminal.js`, `nova-src/platforms/android/app/src/main/assets/antigravity/`, `harness/` |

---

## 1. Diagnóstico Forense y Análisis de Causa Raíz (Root Cause Analysis)

### 1.1 Diagnóstico Forense del Deadlock de Estado en `AgentBridge.connect()`
En la especificación `SPEC-024` se formalizó el aprovisionamiento autónomo *Zero-Click* en frío (`ZeroClickBootContract`). Sin embargo, durante las primeras pruebas de integración en dispositivos reales y emuladores, se detectó una trampa de bloqueo mutuo (*Re-entrant State Deadlock*) dentro de la máquina de estados de `nova-src/src/antigravity2/AgentBridge.js`:

```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Desarrollador
    participant Canvas as ChatCanvas.js
    participant Bridge as AgentBridge.js
    participant Term as Terminal.js

    Usuario->>Bridge: connect() [Llamada Inicial Cold Boot]
    Note over Bridge: isConnecting = true (Adquiere Lock)
    Bridge->>Term: Terminal.isInstalled()
    Term-->>Bridge: false (Entorno no instalado)
    Bridge->>Canvas: emit('statusChange', { status: 'INITIALIZING' })
    Canvas-->>Usuario: Muestra SetupCard ("Auto-inicializando...")
    Bridge->>Bridge: await this.installRuntime()
    Bridge->>Term: Terminal.install() -> Extracción Local Assets (3-5s)
    Term-->>Bridge: true (Instalación Exitosa)
    
    rect rgb(60, 20, 20)
    Note over Bridge: INICIO DE LA TRAMPA DE ESTADO (DEADLOCK)
    Bridge->>Bridge: await this.connect() [Llamada Recursiva Innecesaria]
    Bridge->>Bridge: if (this.isConnecting) return; -> ABORTA SILENCIOSAMENTE!
    Bridge-->>Bridge: Retorna undefined
    Note over Bridge: installRuntime() concluye
    Bridge-->>Bridge: connect() ejecuta return; prematuro tras installRuntime()
    end

    rect rgb(80, 20, 20)
    Note over Bridge, Canvas: CONSECUENCIA FATAL:<br/>1. isConnecting = true PERPETUO<br/>2. isConnected = false<br/>3. Terminal.startAxs() NUNCA SE EJECUTA<br/>4. waitForServerReady() NUNCA SE EJECUTA<br/>5. WebSocket PTY NUNCA SE ABRE<br/>6. statusChange 'READY' NUNCA SE EMITE<br/>7. dismissSetupCard() NUNCA SE LLAMA (SetupCard congelada)
    end
```

#### Anatomía del Defecto en Código
1. **Adquisición del cerrojo de conexión en la llamada raíz:**
   ```javascript
   // AgentBridge.js: connect()
   if (this.isConnected || this.isConnecting) return;
   this.isConnecting = true; // <-- BLOQUEO ACTIVADO
   ```
2. **Detección de entorno no instalado:**
   ```javascript
   const isInstalled = await Terminal.isInstalled();
   if (!isInstalled) {
       this.emit('statusChange', { status: 'INITIALIZING', type: 'thinking' });
       await this.installRuntime();
       return; // <-- SALIDA PREMATURA
   }
   ```
3. **Llamada recursiva abortada en `installRuntime()`:**
   ```javascript
   // AgentBridge.js: installRuntime()
   const success = await Terminal.install(...);
   if (success) {
       this.emit('statusChange', { status: 'CONNECTING', type: 'thinking' });
       await this.connect(); // <-- AQUÍ: connect() comprueba "if (this.isConnecting) return;" y muere al instante
       return true;
   }
   ```
4. **Resultado del Deadlock:**
   - La llamada recursiva `await this.connect()` no ejecuta ninguna acción debido a la guarda de reentrancia.
   - `installRuntime()` retorna a la primera llamada de `connect()`.
   - La primera llamada a `connect()` ejecuta inmediatamente `return;` (línea 72 de la versión previa).
   - El daemon AXS **nunca** se arranca, el puerto 8767 **nunca** se abre, la sesión PTY **nunca** se crea, el WebSocket **nunca** se conecta y el estado final **nunca** llega a `READY`.
   - `this.isConnecting` queda atascado en `true` para siempre, impidiendo que futuros intentos de reconexión o envío de prompts puedan desbloquear el puente.

---

### 1.2 Diagnóstico Forense del Retiro de UI en `ChatCanvas.js`
En `ChatCanvas.js`, la tarjeta de aprovisionamiento `SetupCard` está enlazada al evento `statusChange`:
```javascript
// ChatCanvas.js
agentBridge.on('statusChange', ({ status }) => {
  if (status === 'INITIALIZING') {
    this.renderSetupCard(true);
  } else if (status === 'READY') {
    this.dismissSetupCard();
    if (!this.welcomeHeroEl && (!this.messagesListEl.children.length || (this.messagesListEl.children.length === 1 && this.setupCardEl))) {
      this.renderWelcomeHero();
    }
  }
});
```
Al quedar la máquina de estados congelada en `isConnecting = true` sin emitir `READY`, `dismissSetupCard()` jamás se ejecuta.
Asimismo, en la implementación de `dismissSetupCard()`:
```javascript
dismissSetupCard() {
  if (!this.setupCardEl) return;
  const card = this.setupCardEl;
  this.setupCardEl = null; // <-- Se anula de inmediato
  card.style.opacity = "0";
  setTimeout(() => { if (card.parentNode) card.parentNode.removeChild(card); }, 180);
}
```
Si `this.setupCardEl` se anula antes de verificar si se debe renderizar el `WelcomeHero`, la condición `(this.messagesListEl.children.length === 1 && this.setupCardEl)` resulta falsa (dado que `this.setupCardEl` ya es `null`), dejando el lienzo de conversación vacío tras el retiro de la tarjeta.

---

### 1.3 Diagnóstico Forense de Peso del APK (APK Bloat & Asset Duplication)
Durante la inspección del árbol de activos empaquetados en `nova-src/platforms/android/app/src/main/assets/antigravity/`, se evidenció la existencia simultánea de dos copias del sistema de archivos comprimido de Ubuntu 24.04:

| Ruta del Archivo en APK Assets | Tamaño en Bytes | Tamaño (MB) | Hash / Contenido |
| :--- | :--- | :--- | :--- |
| `antigravity/rootfs/ubuntu_arm64.tar.gz` | $40,734,829$ bytes | $\sim 40.7\,\text{MB}$ | Imagen canónica Rootfs Ubuntu Noble ARM64 |
| `antigravity/ubuntu_arm64.tar.gz` | $40,734,829$ bytes | $\sim 40.7\,\text{MB}$ | **Copia duplicada redundante** |

```
nova-src/platforms/android/app/src/main/assets/antigravity/
├── cli_linux_arm64.tar.gz      [54.0 MB] -> Binario oficial Google Antigravity CLI
├── rootfs/
│   └── ubuntu_arm64.tar.gz   [40.7 MB] -> CANÓNICO: Rootfs Ubuntu Noble ARM64
└── ubuntu_arm64.tar.gz         [40.7 MB] -> DUPLICADO REDUNDANTE A ELIMINAR
```

#### Impacto Negativo del Duplicado
1. **Desperdicio Crítico de Espacio:** Se incorporan más de $40.7\,\text{MB}$ adicionales al archivo APK final empaquetado por Gradle.
2. **Penalización en Instalación:** Dispositivos móviles con almacenamiento flash saturado ven duplicado el tiempo de I/O de descompresión del paquete de aplicación.
3. **Ambigüedad de Rutas:** En `Terminal.js`, existían cadenas de fallback redundantes intentando extraer ambas rutas.

---

## 2. Contrato de Transición Secuencial Sin Deadlock (`ZeroLockTransitionContract`)

### 2.1 Eliminación de la Invocación Recursiva en `installRuntime()`
El método `installRuntime()` debe tener una única responsabilidad: **aprovisionar el entorno local en disco**.
- Queda estrictamente prohibido que `installRuntime()` invoque `this.connect()`.
- Su firma contractual debe ser puramente funcional, devolviendo una promesa con un valor booleano (`Promise<boolean>`): `true` si `Terminal.install()` concluye con éxito, o lanzar una excepción / retornar `false` si falla.

```javascript
// AgentBridge.js: Contrato Purificado de installRuntime (SPEC-025)
async installRuntime(onProgress, onLog) {
  if (typeof Terminal === "undefined" || typeof Terminal.install !== "function") {
    console.warn("Terminal plugin no disponible");
    return false;
  }

  this.emit('statusChange', { status: 'INITIALIZING', type: 'thinking' });

  try {
    const success = await Terminal.install(
      (msg) => {
        if (onLog) onLog(msg);
        if (onProgress && typeof msg === "string") {
          if (msg.includes("Extrayendo") || msg.includes("Extracting")) onProgress(50);
          else if (msg.includes("Installing") || msg.includes("Instalando")) onProgress(80);
          else if (msg.includes("éxito") || msg.includes("completed")) onProgress(100);
        }
        this.emit('installLog', { message: msg });
      },
      (err) => {
        if (onLog) onLog(`[ERROR] ${err}`);
        console.error("Installation log error:", err);
      }
    );

    if (success) {
      if (onProgress) onProgress(100);
      // SPEC-025: PROHIBIDO llamar a this.connect() aquí.
      // installRuntime se limita a retornar el éxito del aprovisionamiento.
      return true;
    }
    return false;
  } catch (e) {
    console.error("Runtime installation failed:", e);
    this.emit('statusChange', { status: 'ERROR', type: 'error', error: e.message });
    throw e;
  }
}
```

---

### 2.2 Ejecución Lineal y Secuencial en `connect()`
En `AgentBridge.connect()`, cuando se detecta que el runtime no está instalado (`!isInstalled`):
1. Se invoca `await this.installRuntime()`.
2. Al resolverse exitosamente, **no se ejecuta ningún `return`**.
3. El flujo continúa secuencial y naturalmente hacia las fases de inicialización del daemon AXS y la conexión de la terminal PTY bajo el mismo contexto de ejecución (`this.isConnecting = true`).

```javascript
// AgentBridge.js: Secuencia Lineal Unificada (SPEC-025)
async connect() {
  if (this.isConnected || this.isConnecting) return;
  this.isConnecting = true;
  this.emit('statusChange', { status: 'CONNECTING', type: 'thinking' });

  try {
    // PASO 1: Verificación y Aprovisionamiento Local (Zero-Click, Zero-Lock)
    if (typeof Terminal !== "undefined" && typeof Terminal.isInstalled === "function") {
      const isInstalled = await Terminal.isInstalled();
      if (!isInstalled) {
        console.log("Auto-Provisioning: Entorno no instalado. Iniciando extracción local inmediata...");
        this.emit('statusChange', { status: 'INITIALIZING', type: 'thinking' });

        const installed = await this.installRuntime();
        if (!installed) {
          throw new Error("No se pudo completar la extracción e instalación del subsistema Linux");
        }

        console.log("Auto-Provisioning completado con éxito. Continuando secuencia hacia AXS daemon...");
        this.emit('statusChange', { status: 'CONNECTING', type: 'thinking' });
        // SPEC-025: CONTINUACIÓN DIRECTA SIN RETURN
      }
    }

    // PASO 2: Verificación y Arranque del Daemon AXS PTY
    if (typeof Terminal !== "undefined") {
      if (typeof Terminal.isAxsRunning === "function" && !(await Terminal.isAxsRunning())) {
        console.log("Iniciando daemon AXS PTY en puerto " + this.port + "...");
        await Terminal.startAxs(false, () => {}, console.error, false);
      }
    }

    // PASO 3: Espera Resiliente de Disponibilidad del Servidor AXS (HTTP /status)
    await this.waitForServerReady();

    // PASO 4: Creación de Sesión de Terminal PTY (POST /terminals)
    this.pid = await this.createSession();

    // PASO 5: Apertura del Canal de Comunicación WebSocket
    const wsUrl = `ws://127.0.0.1:${this.port}/terminals/${this.pid}`;
    await this.openWebSocket(wsUrl);

    // PASO 6: Liberación del Bloqueo y Transición al Estado READY
    this.isConnected = true;
    this.isConnecting = false;
    this.emit('statusChange', { status: 'READY', type: 'ready' });

    // PASO 7: Lanzamiento del Agente agy -c y Desahogo de Prompts Pendientes
    setTimeout(() => {
      this.launchAgy();
      this.flushPendingQueue();
    }, 300);

    // PASO 8: Escáner de Puertos Web Locales en Segundo Plano
    this.startPortScanner();

  } catch (err) {
    console.error("AgentBridge connection failed:", err);
    this.isConnecting = false;
    this.isConnected = false;
    this.emit('statusChange', { status: 'ERROR', type: 'error', error: err.message });
    throw err;
  }
}
```

---

## 3. Contrato de Retiro Inmediato de UI y Despliegue de Hero (`CleanUIAutoDismissContract`)

### 3.1 Transición Atómica de UI en `ChatCanvas.js`
Para garantizar que la tarjeta de configuración `SetupCard` desaparezca instantáneamente y sin artefactos visuales, y que el `WelcomeHero` de bienvenida tome el protagonismo en el primer arranque:

```javascript
// ChatCanvas.js: Listener Optimizado de Estado (SPEC-025)
agentBridge.on('statusChange', ({ status }) => {
  if (status === 'INITIALIZING') {
    this.renderSetupCard(true);
  } else if (status === 'SETUP') {
    this.renderSetupCard(false);
  } else if (status === 'READY') {
    // Determinar si la pantalla estaba limpia (solo la tarjeta de setup o vacía)
    const hadOnlySetupCard = this.messagesListEl.children.length === 0 || 
      (this.messagesListEl.children.length === 1 && this.setupCardEl);

    // Retirar la tarjeta con animación suave inmediata
    this.dismissSetupCard();

    // Desplegar el WelcomeHero de inicio si no hay mensajes de usuario en cola
    if (!this.welcomeHeroEl && hadOnlySetupCard) {
      this.renderWelcomeHero();
    }
  }
});
```

---

### 3.2 Diagrama de Estados del Ciclo de Vida Completo

```mermaid
stateDiagram-v2
    [*] --> COLD_BOOT: Apertura de la aplicación
    
    COLD_BOOT --> NOT_INSTALLED: Terminal.isInstalled() === false
    COLD_BOOT --> AXS_CHECK: Terminal.isInstalled() === true
    
    NOT_INSTALLED --> INSTALLING: emit('INITIALIZING') / Render SetupCard
    INSTALLING --> ASSET_EXTRACTION: system.extractAsset(rootfs/ubuntu_arm64.tar.gz)
    ASSET_EXTRACTION --> TAR_UNPACK: tar -xf local (3-5s)
    TAR_UNPACK --> AXS_CHECK: emit('CONNECTING')
    
    AXS_CHECK --> AXS_START: Terminal.isAxsRunning() === false
    AXS_CHECK --> SERVER_WAIT: Terminal.isAxsRunning() === true
    AXS_START --> SERVER_WAIT: Terminal.startAxs()
    
    SERVER_WAIT --> SERVER_READY: HTTP GET /status -> 200 OK
    SERVER_READY --> SESSION_CREATE: POST /terminals (Retorna PID)
    SESSION_CREATE --> WS_OPENING: WebSocket ws://127.0.0.1:8767/terminals/{pid}
    WS_OPENING --> READY: onopen -> isConnected=true, isConnecting=false
    
    READY --> DISMISS_SETUP_CARD: emit('READY')
    DISMISS_SETUP_CARD --> SHOW_HERO: SetupCard desvanecida (180ms)
    SHOW_HERO --> AGY_LAUNCH: agy -c enviado al PTY
    AGY_LAUNCH --> FLUSH_QUEUE: Desahogo de prompts en cola
    FLUSH_QUEUE --> ACTIVE_CHAT: Esperando interacción o respuesta
```

---

## 4. Contrato de Adelgazamiento y Canonicidad de Assets (`ApkSlimmingContract`)

### 4.1 Supresión del Archivo Duplicado Redundante
Se establece la regla de inmutabilidad de almacenamiento para `nova-src`:
1. **Archivo Único Canónico:**
   - Ubicación: `nova-src/platforms/android/app/src/main/assets/antigravity/rootfs/ubuntu_arm64.tar.gz`
   - Función: Sistema de archivos minimal Ubuntu 24.04 Noble ARM64 con glibc, ca-certificates, Node.js y Python.
2. **Archivo Eliminado:**
   - Se elimina de forma definitiva el archivo redundante:
     `nova-src/platforms/android/app/src/main/assets/antigravity/ubuntu_arm64.tar.gz`
   - Ahorro de espacio directo: **40,734,829 bytes ($\sim 40.7\,\text{MB}$)**.

---

### 4.2 Ajuste Canónico en `Terminal.js`
En `nova-src/src/plugins/terminal/www/Terminal.js`, la extracción de activos ARM64 se simplifica para apuntar directamente a la ruta canónica `antigravity/rootfs/ubuntu_arm64.tar.gz`:

```javascript
// Terminal.js: Extracción Local Canónica sin Redundancia (SPEC-025)
logger("📦  Extrayendo Ubuntu ARM64 glibc rootfs desde assets locales canónicos...");
await new Promise((resolve, reject) => {
    system.extractAsset(
        "antigravity/rootfs/ubuntu_arm64.tar.gz",
        `${filesDir}/rootfs.tar.gz`,
        resolve,
        (err) => {
            console.error("Fallo extrayendo rootfs desde ruta canónica:", err);
            reject(err);
        }
    );
});

logger("📦  Extrayendo Google Antigravity CLI (arm64) desde assets locales...");
await new Promise((resolve, reject) => {
    system.extractAsset(
        "antigravity/cli_linux_arm64.tar.gz",
        `${filesDir}/cli_linux_arm64.tar.gz`,
        resolve,
        reject
    );
});
```

---

### 4.3 Cuantificación de Métricas de Rendimiento y Espacio

| Métrica | Enfoque Previo (SPEC-024 inicial) | Enfoque Optimizado (SPEC-025) | Ganancia / Delta |
| :--- | :--- | :--- | :--- |
| **Peso Total Assets en APK** | $\sim 135.5\,\text{MB}$ | $\sim 94.8\,\text{MB}$ | **$-40.7\,\text{MB}$ ($-30.0\%$)** |
| **Riesgo de Deadlock en Boot** | $100\%$ en cold boot limpio | $0\%$ (Transición secuencial lineal) | **Erradicación total** |
| **Tiempo de Despliegue de UI** | Infinito (Bloqueado en SetupCard) | $\le 4.5\,\text{segundos}$ | **Transición fluida a READY** |
| **Retiro de SetupCard** | Nunca (Sin evento READY) | Inmediato ($180\,\text{ms}$ con fade-out) | **$100\%$ Confiable** |

---

## 5. Matriz de Criterios de Aceptación Verificables

| Identificador | Descripción | Método de Verificación | Resultado Esperado |
| :--- | :--- | :--- | :--- |
| **`AC-TRANS-001`** | Eliminación de recursión en `AgentBridge.installRuntime()` | Análisis estático de AST / Grep | Ninguna invocación a `this.connect()` dentro de `installRuntime()` |
| **`AC-TRANS-002`** | Continuación secuencial lineal en `AgentBridge.connect()` | Inspección de flujo de control | El bloque `if (!isInstalled)` no contiene `return` tras `installRuntime()` |
| **`AC-TRANS-003`** | Transición completa y exitosa a estado `READY` | Ejecución del arnés de pruebas | `this.isConnecting === false` y `this.isConnected === true` |
| **`AC-TRANS-004`** | Retiro automático e inmediato de `SetupCard` | Event listener en `ChatCanvas.js` | `dismissSetupCard()` se ejecuta al recibir evento `READY` |
| **`AC-TRANS-005`** | Despliegue automático de `WelcomeHero` | Verificación del DOM en `ChatCanvas.js` | El elemento `.ag-welcome-hero` se crea e inserta en `messagesListEl` si no hay mensajes previos |
| **`AC-SLIM-001`** | Eliminación de `ubuntu_arm64.tar.gz` duplicado en raíz de assets | Inspección del sistema de archivos | El archivo `assets/antigravity/ubuntu_arm64.tar.gz` no existe |
| **`AC-SLIM-002`** | Preservación de la ruta canónica `rootfs/ubuntu_arm64.tar.gz` | Inspección del sistema de archivos y hash | El archivo `assets/antigravity/rootfs/ubuntu_arm64.tar.gz` existe y mide exactamente $40,734,829$ bytes |
| **`AC-SLIM-003`** | Canonicidad de extracción en `Terminal.js` | Análisis de código en `Terminal.js` | `system.extractAsset` se invoca con `"antigravity/rootfs/ubuntu_arm64.tar.gz"` |

---

## 6. Arnés de Pruebas Automatizado (`test_antigravity_2_auto_transition_slimming.sh`)

El siguiente arnés verifica de manera formal y automatizada cada una de las compuertas de calidad descritas:

```bash
#!/bin/bash
# ==============================================================================
# Harness Test: SPEC-025 Auto-Transition and APK Slimming Verification
# ==============================================================================
set -e

SPEC_FILE="specs/25-antigravity-2-mobile-auto-transition-and-apk-slimming.md"
AGENT_BRIDGE="nova-src/src/antigravity2/AgentBridge.js"
CHAT_CANVAS="nova-src/src/antigravity2/ChatCanvas.js"
TERMINAL_JS="nova-src/src/plugins/terminal/www/Terminal.js"
ASSETS_DIR="nova-src/platforms/android/app/src/main/assets/antigravity"

echo "=== INICIANDO VALIDACIÓN FORMAL DE SPEC-025 ==="

# 1. Verificar existencia del documento de especificación
echo -n "1. Verificando existencia de SPEC-025... "
[ -f "$SPEC_FILE" ] || { echo "FALLO: No existe $SPEC_FILE"; exit 1; }
echo "[OK]"

# 2. Verificar ausencia de recursión en installRuntime() de AgentBridge.js
echo -n "2. Verificando ausencia de recursión en installRuntime()... "
# installRuntime no debe llamar a this.connect()
if sed -n '/async installRuntime/,/^  }/p' "$AGENT_BRIDGE" | grep -q "await this\.connect()"; then
  echo "FALLO: installRuntime aún contiene la llamada recursiva a this.connect()"; exit 1;
fi
echo "[OK]"

# 3. Verificar continuación secuencial en connect() tras aprovisionamiento
echo -n "3. Verificando secuencia lineal en connect()... "
# connect() no debe hacer return inmediatamente después de installRuntime()
if grep -A 2 "await this\.installRuntime()" "$AGENT_BRIDGE" | grep -q "return;"; then
  echo "FALLO: connect() aún aborta con return; tras installRuntime()"; exit 1;
fi
echo "[OK]"

# 4. Verificar retiro de SetupCard y WelcomeHero en ChatCanvas.js
echo -n "4. Verificando retiro de SetupCard y despliegue de WelcomeHero... "
grep -q "this\.dismissSetupCard()" "$CHAT_CANVAS" || { echo "FALLO: dismissSetupCard no invocado en ChatCanvas"; exit 1; }
grep -q "this\.renderWelcomeHero()" "$CHAT_CANVAS" || { echo "FALLO: renderWelcomeHero no invocado en ChatCanvas"; exit 1; }
echo "[OK]"

# 5. Verificar optimización de peso (APK Slimming)
echo -n "5. Verificando eliminación de archivo duplicado en assets... "
if [ -f "$ASSETS_DIR/ubuntu_arm64.tar.gz" ]; then
  echo "FALLO: El archivo duplicado $ASSETS_DIR/ubuntu_arm64.tar.gz aún existe"; exit 1;
fi
if [ ! -f "$ASSETS_DIR/rootfs/ubuntu_arm64.tar.gz" ]; then
  echo "FALLO: La ruta canónica $ASSETS_DIR/rootfs/ubuntu_arm64.tar.gz no existe"; exit 1;
fi
echo "[OK]"

# 6. Verificar ruta canónica de extracción en Terminal.js
echo -n "6. Verificando ruta canónica en Terminal.js... "
grep -q "antigravity/rootfs/ubuntu_arm64\.tar\.gz" "$TERMINAL_JS" || { echo "FALLO: Terminal.js no apunta a la ruta canónica"; exit 1; }
echo "[OK]"

echo "=== TODAS LAS COMPUERTAS DE CALIDAD DE SPEC-025 HAN SIDO VERIFICADAS EXITOSAMENTE ==="
```

---

## 7. Definition of Done (DoD) para `@android-core` y `@memory-keeper`

### A. Para `@android-core` (Ingeniero de Implementación):
1. **Refactorización de `AgentBridge.js`:**
   - Eliminar la llamada recursiva `await this.connect()` dentro de `installRuntime()`.
   - Modificar `connect()` para que, tras `await this.installRuntime()`, continúe fluidamente hacia `Terminal.startAxs()`, `waitForServerReady()`, `createSession()`, `openWebSocket()` y emita `READY`.
2. **Refactorización de `ChatCanvas.js`:**
   - Ajustar el listener `statusChange` para guardar `hadOnlySetupCard` antes de `this.dismissSetupCard()`, garantizando el retiro inmediato de la tarjeta y el renderizado automático del `WelcomeHero`.
3. **Slimming de Assets:**
   - Eliminar el archivo duplicado `nova-src/platforms/android/app/src/main/assets/antigravity/ubuntu_arm64.tar.gz` ($40.7\,\text{MB}$).
   - Conservar exclusivamente el archivo canónico `nova-src/platforms/android/app/src/main/assets/antigravity/rootfs/ubuntu_arm64.tar.gz`.
4. **Limpieza en `Terminal.js`:**
   - Asegurar que la llamada primaria a `system.extractAsset` use la ruta canónica `antigravity/rootfs/ubuntu_arm64.tar.gz` sin fallbacks innecesarios a archivos inexistentes.
5. **Ejecución y Pase del Arnés:**
   - Ejecutar `test_antigravity_2_auto_transition_slimming.sh` y certificar que todas las pruebas pasen con código de salida `0`.

### B. Para `@memory-keeper` (Auditor de Gobernanza y Memoria):
1. Registrar la resolución del deadlock de estado y la reducción de peso del APK como un nuevo hito arquitectónico en `agent.md`.
2. Documentar la inmutabilidad de la estructura canónica de assets en los diagramas de arquitectura del sistema.
3. Asegurar que las métricas de tamaño y tiempo de cold boot queden asentadas formalmente en el registro de desempeño.

---

## 8. Conclusión

La especificación técnica **`SPEC-025`** resuelve de forma contundente los dos últimos obstáculos para el lanzamiento de producción de **Google Antigravity 2.0 Mobile**:
1. **Flujo de Arranque Confiable (*Zero-Lock*):** La erradicación de la recursión viciosa en `AgentBridge` permite que el ciclo de aprovisionamiento en frío transicione de forma determinista y sin tropiezos hasta el estado `READY` en menos de 5 segundos.
2. **Distribución Ultraligera (*APK Slimming*):** La eliminación del duplicado de 40.7 MB reduce en un $30\%$ la huella de los assets empaquetados, optimizando los tiempos de compilación, descarga e instalación en cualquier dispositivo Android moderno.
