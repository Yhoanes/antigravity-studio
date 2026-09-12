package com.antigravity.studio.project

/**
 * Representa una plantilla base para inicializar un nuevo proyecto en el espacio de trabajo.
 * Conforme a SPEC-003.
 */
enum class ProjectTemplate(val displayName: String, val description: String) {
    EMPTY("Vacío", "Directorio en blanco listo para inicialización manual"),
    PYTHON_CLI("Python CLI", "Entorno preconfigurado con main.py, requirements.txt y venv"),
    NODEJS_API("Node.js API", "Entorno con package.json, TypeScript y script de arranque"),
    BASH_SCRIPT("Scripting POSIX", "Herramientas de automatización bash con soporte agéntico")
}
