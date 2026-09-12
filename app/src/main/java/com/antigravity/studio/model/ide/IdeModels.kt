package com.antigravity.studio.model.ide

import java.io.File

typealias ProjectItem = com.antigravity.studio.project.ProjectItem
typealias ProjectTemplate = com.antigravity.studio.project.ProjectTemplate
typealias ProjectManager = com.antigravity.studio.project.ProjectManager

/**
 * Modos de visualización soportados en el lienzo central de la Xiaomi Pad 6.
 * Conforme a SPEC-003 §4.2 y §6.3.
 */
enum class CanvasDisplayMode {
    SINGLE_TAB,
    SPLIT_HORIZONTAL,
    SPLIT_VERTICAL
}

/**
 * Tipos de pestañas disponibles en el Dual Canvas.
 */
enum class CanvasTabType(val title: String) {
    AGENT_TERMINAL("Agent Terminal (144Hz)"),
    WORKSPACE_CODE("Workspace Canvas")
}

/**
 * Estado que gobierna el lienzo dual en resolución 2.8K a 144Hz.
 */
data class DualCanvasState(
    val activeTab: CanvasTabType = CanvasTabType.AGENT_TERMINAL,
    val displayMode: CanvasDisplayMode = CanvasDisplayMode.SINGLE_TAB,
    val splitRatio: Float = 0.5f,
    val currentOpenFilePath: String? = null,
    val currentFileContent: String? = null,
    val isFileModified: Boolean = false,
    val terminalRefreshRateHz: Int = 144
)

/**
 * Estado de ejecución y ciclo de vida de un subagente orquestado.
 */
enum class SubagentExecutionStatus(val label: String) {
    IDLE("Idle"),
    REASONING("Reasoning 🟣"),
    EXECUTING("Running ⟳"),
    COMPLETED("Completed ✓"),
    FAILED("Failed 🔴")
}

/**
 * Modelo de datos para la tarjeta de visualización de un subagente.
 */
data class SubagentCardData(
    val id: String,
    val name: String,
    val roleDescription: String,
    val durationSeconds: Long = 0L,
    val toolCallsCount: Int = 0,
    val status: SubagentExecutionStatus = SubagentExecutionStatus.IDLE,
    val progressPercent: Float = 0.0f
) {
    val durationFormatted: String
        get() {
            val mins = durationSeconds / 60
            val secs = durationSeconds % 60
            return "${mins}m ${secs}s"
        }
}

/**
 * Resumen de cambios por archivo modificado por el agente.
 */
data class FileChangeSummary(
    val relativePath: String,
    val linesAdded: Int = 0,
    val linesDeleted: Int = 0,
    val isBinary: Boolean = false
) {
    val linesSummary: String
        get() = when {
            linesAdded > 0 && linesDeleted > 0 -> "+$linesAdded / -$linesDeleted"
            linesAdded > 0 -> "+$linesAdded lines"
            linesDeleted > 0 -> "-$linesDeleted lines"
            else -> "modified"
        }
}

/**
 * Estado reactivo del panel auxiliar derecho de inspección agéntica.
 */
data class InspectionDrawerState(
    val isOpen: Boolean = true,
    val subagents: List<SubagentCardData> = emptyList(),
    val modifiedFiles: List<FileChangeSummary> = emptyList(),
    val isHyperOsWakelockActive: Boolean = true
)
