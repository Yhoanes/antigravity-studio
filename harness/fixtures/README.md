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

## PROVENIENCIA — LEER ANTES DE USAR

| Archivo | Origen | Fidelidad |
| :--- | :--- | :--- |
| `agy-help-1.2.4.txt` | Transcrito de capturas de pantalla | **NO byte-exacto** |
| `agy-print-json.json` | Transcrito de capturas de pantalla | **NO byte-exacto** |
| `agy-print-stream-json.ndjson` | Transcrito de capturas de pantalla | **NO byte-exacto** |
| `agy-print-stream-json-tools.ndjson` | Transcrito de capturas de pantalla | **NO byte-exacto** |

**Estas capturas fueron transcritas a mano desde screenshots del dispositivo, no
volcadas a archivo.** Implicaciones:

- El espaciado, el orden de claves y el escapado pueden no ser literales.
- Algunas líneas aparecían truncadas o solapadas en las imágenes.
- La lista de `tools` puede tener omisiones.

Son suficientes para diseñar contra el protocolo. **No son suficientes para
afirmar conformidad.** Antes de tratar cualquier compuerta basada en estas
fixtures como autoritativa, reemplázalas por volcados reales:

```sh
agy --help > agy-help-1.2.4.txt 2>&1
agy -p "di hola" --output-format json > agy-print-json.json
agy -p "di hola" --output-format stream-json > agy-print-stream-json.ndjson
agy -p "lista los archivos de /home/studio/workspace" \
    --output-format stream-json --dangerously-skip-permissions \
    > agy-print-stream-json-tools.ndjson
```

Cuando se reemplacen, actualiza la columna de fidelidad a `byte-exacto` y anota
la versión del CLI.

## DISCREPANCIA CONOCIDA — `agy-print-stream-json-tools.ndjson`

La concatenación de los `text_delta` del paso 6 **no coincide** con
`result.response` en esta fixture:

```
deltas   : El directorio [`/home/studio/workspace`](file:///home/studio/workspace) se encuentra …
response : El directorio workspace se encuentra …
```

En `agy-print-stream-json.ndjson` sí coinciden exactamente, lo que sugiere que
esto es **un artefacto de transcripción y no una propiedad del protocolo**: en la
captura, `agy` renderizó el enlace markdown de su propia salida como texto
subrayado, y se transcribió la forma renderizada en lugar del JSON crudo.

No se ha corregido a mano para no inventar datos. Se resuelve con el volcado
real. Hasta entonces:

- `AC-STREAM-003` solo es exigible contra `agy-print-stream-json.ndjson`.
- La relación entre `text_delta` concatenados y `result.response` para contenido
  con markdown queda **SIN VERIFICAR**.

## Versión capturada

`Antigravity CLI 1.2.4` — 2026-09-16.

El protocolo no está versionado ni documentado públicamente. Asúmelo inestable
entre versiones del CLI y revalida las fixtures tras cada actualización de `agy`.
