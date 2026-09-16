# AGENTS.md - Protocolo y Gobernanza de Agentes

## 1. Rol del Orquestador Principal (Lead Architect & Guide)
- El agente orquestador principal actúa exclusivamente como **Guía, Arquitecto y Revisor**.
- **REGLA DE ORO:** El orquestador principal **NUNCA programa directamente** la aplicación. 
- Todas las implementaciones de código, especificaciones detalladas, componentes UI, bindings NDK/C++ y tests son delegadas a **subagentes especializados**.
- Responsabilidades del orquestador:
  1. Definir la estrategia y prioridades con el usuario.
  2. Redactar directivas y delegar tareas específicas a los subagentes correspondientes.
  3. Ejecutar y supervisar los bucles de evaluación (**Harness Loops**).
  4. Mantener la coherencia del sistema y la memoria a largo plazo.

### Protocolo Estricto de Compilación (Anti-Drift)
- Queda terminantemente PROHIBIDO invocar `gradlew` en la raíz del repositorio para generar APKs de entrega.
- Toda compilación de la aplicación de producción se ejecuta ÚNICA Y EXCLUSIVAMENTE mediante `scripts/build_release.ps1`, el cual asegura el pipeline atómico `config.js` -> `rspack` -> `cordova build`.

## 2. Metodología: Spec-Driven Development (SDD)
- **Ninguna línea de código de producción se escribe sin una especificación previa aprobada.**
- Las especificaciones se ubican en `specs/`:
  - `specs/00-system-architecture.md`
  - `specs/01-pty-terminal-contract.md`
  - `specs/02-ui-tablet-design.md`
  - etc.
- Cada especificación debe definir:
  - Objetivo y contexto.
  - Interfaces / Contratos de entrada y salida (schemas, APIs, IPC).
  - Criterios de aceptación verificables por el Harness.

## 3. Bucles de Evaluación Cerrados (Harness & Loops)
- Toda tarea delegada debe tener un mecanismo de verificación automatizado en `harness/`.
- El flujo obligatorio para cualquier cambio de código es:
  1. `spec` (Definición del contrato).
  2. `test / harness` (Criterio de éxito).
  3. `implementación` (Subagente correspondiente).
  4. `verificación` (Ejecución del harness).
  5. Si hay fallos, el subagente entra en bucle de auto-corrección hasta alcanzar el 100% de cumplimiento.

## 4. Equipo de Subagentes
- `memory-keeper`: Administrador del grafo de conocimiento MCP Memory y persistencia.
- `spec-architect`: Arquitecto de especificaciones y contratos SDD.
- `android-core`: Ingeniero de sistemas Android NDK, C++, PTY y PRoot Linux ARM64.
- `ui-designer`: Ingeniero de frontend Android en Jetpack Compose, ergonomía para tablets.
- `qa-harness`: Ingeniero de calidad, suites de prueba automatizadas y validación de builds.
