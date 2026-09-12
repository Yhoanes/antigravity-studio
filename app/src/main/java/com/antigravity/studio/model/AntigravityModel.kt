package com.antigravity.studio.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Generative engine model entity supported in Antigravity Studio.
 */
data class AntigravityModel(
    val id: String,
    val displayName: String,
    val tags: List<String>,
    val isThinking: Boolean = false
)

/**
 * Official Antigravity Model Catalog providing real-time selection state across the workspace.
 */
object AntigravityModelCatalog {
    val models = listOf(
        AntigravityModel("gemini-3.8-flash", "Gemini 3.8 Flash", listOf("High", "Fast")),
        AntigravityModel("gemini-3.7-flash", "Gemini 3.7 Flash", listOf("Medium", "Fast")),
        AntigravityModel("gemini-3.6-flash", "Gemini 3.6 Flash", listOf("Medium", "Fast")),
        AntigravityModel("gemini-3.1-pro", "Gemini 3.1 Pro", listOf("Low")),
        AntigravityModel("claude-sonnet-4.6", "Claude Sonnet 4.6 (Thinking)", listOf("Thinking"), isThinking = true),
        AntigravityModel("claude-opus-4.6", "Claude Opus 4.6 (Thinking)", listOf("Thinking"), isThinking = true),
        AntigravityModel("gpt-oss-120b", "GPT-OSS 120B (Medium)", listOf("Medium"))
    )
    val defaultModel = models[0] // Gemini 3.8 Flash

    private val _selectedModel = MutableStateFlow(defaultModel)
    val selectedModel: StateFlow<AntigravityModel> = _selectedModel.asStateFlow()

    fun selectModel(model: AntigravityModel) {
        _selectedModel.value = model
    }

    fun selectModelById(id: String): Boolean {
        val found = models.find { it.id.equals(id, ignoreCase = true) || it.displayName.contains(id, ignoreCase = true) }
        if (found != null) {
            _selectedModel.value = found
            return true
        }
        return false
    }
}
