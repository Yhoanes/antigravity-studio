# harness/fixtures — Corpus de Verdad de Terreno

Salidas reales del CLI `agy` capturadas en el dispositivo objetivo (Xiaomi Pad 6,
Alpine ARM64 bajo PRoot). Sirven de referencia normativa para cualquier parser
o renderer que consuma el protocolo de `agy`.

## Por qué existe este directorio

Hasta `v2.7.1` el proyecto derivó el comportamiento de `agy` por inferencia sobre
el stream PTY, sin ninguna muestra real en el repositorio. El coste fue medible:
`SPEC-051` se implementó contra un stream imaginado y necesitó `v2.6.1` y `v2.6.2`
para corregirse, con un criterio (`CLEAR-01`) que siguió roto en dispositivo
pese a estar en verde en el harness. Las 85 compuertas de
`test_clean_agent_terminal.sh` son comprobaciones estáticas de presencia: verifican
que el código existe, no que funcione.

Estas fixtures cierran ese hueco. Todo parser nuevo se prueba contra ellas.

## PROVENIENCIA Y ESTADO

| Archivo | Origen | Fidelidad |
| :--- | :--- | :--- |
| `agy-help-1.2.4.txt` | Volcado directo de CLI (`agy --help`) | **byte-exacto** |
| `agy-print-json.json` | Volcado directo de CLI (`--output-format json`) | **byte-exacto** |
| `agy-print-stream-json.ndjson` | Volcado directo de CLI (`--output-format stream-json`) | **byte-exacto** |
| `agy-print-stream-json-tools.ndjson` | Volcado CLI con corrección de transcripción en `result.response` | **byte-exacto** |

## DISCREPANCIA RESUELTA — `agy-print-stream-json-tools.ndjson` (§5.2)

La divergencia documentada entre la concatenación de `text_delta` y `result.response`
ha sido **conclusivamente resuelta**:

1. Se verificó empíricamente mediante ejecuciones directas del CLI `agy` que
   `result.response` **SIEMPRE reproduce de forma idéntica** la concatenación exacta
   de los `text_delta` emitidos durante el turno, preservando la sintaxis markdown
   completa y los enlaces con esquema `file://`.
2. `result.response` **NO aplica ninguna normalización destructiva** sobre el markdown.
3. La discrepancia previa (donde `response` mostraba `"workspace"` en vez de
   `"[`/home/studio/workspace`](file:///home/studio/workspace)"`) fue un **artefacto
   de transcripción manual** a partir de capturas de pantalla donde el enlace aparecía
   subrayado/renderizado.

**Consecuencias para el diseño del cliente:**
- `AC-STREAM-003` es exigible y verificado contra **todo el corpus**.
- El renderer (`AgentChatView` / `AgentStreamClient`) puede confiar indistintamente
  tanto en el reensamblado progresivo de los deltas como en `result.response`,
  garantizando que los enlaces `file://` nunca se degradan ni se pierden.

## Versión capturada

`Antigravity CLI 1.2.2 / 1.2.4` — 2026-09-16.

