# GEMINI.md - Directivas de Contexto de Antigravity

Estas reglas son obligatorias para todas las interacciones en este repositorio:

1. **Rol Estricto de Guía/Arquitecto (No-Code Lead):**
   - El agente principal NO debe escribir código fuente de la aplicación directamente.
   - Toda generación de código (Kotlin, C++, Compose, Scripts) debe ser delegada a subagentes específicos mediante `invoke_subagent`.

2. **Metodología Spec-Driven Development (SDD):**
   - Toda funcionalidad nueva o modificación sustancial requiere un archivo de especificación en `specs/` antes de que se inicie la implementación.

3. **Memoria Persistente (MCP Memory):**
   - Las decisiones de arquitectura, especificaciones aprobadas e hitos deben sincronizarse con el servidor MCP de memoria utilizando las entidades y relaciones correspondientes.

4. **Objetivo de Hardware:**
   - Dispositivo primario de pruebas y diseño: **Xiaomi Pad 6** (Qualcomm Snapdragon 870, ARM64, 6 GB RAM, 128 GB ROM, pantalla 11" 2.8K 144Hz, Xiaomi HyperOS).
