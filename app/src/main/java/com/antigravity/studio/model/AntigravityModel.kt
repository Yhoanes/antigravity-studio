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
        AntigravityModel("gemini-3.8-flash-high", "Gemini 3.8 Flash", listOf("High", "Fast")),
        AntigravityModel("gemini-3.7-flash-high", "Gemini 3.7 Flash", listOf("Medium", "Fast")),
        AntigravityModel("gemini-3.6-flash-high", "Gemini 3.6 Flash", listOf("Medium", "Fast")),
        AntigravityModel("gemini-3.1-pro-low", "Gemini 3.1 Pro", listOf("Low")),
        AntigravityModel("claude-sonnet-4-6", "Claude Sonnet 4.6 (Thinking)", listOf("Thinking"), isThinking = true),
        AntigravityModel("claude-opus-4-6-thinking", "Claude Opus 4.6 (Thinking)", listOf("Thinking"), isThinking = true),
        AntigravityModel("gpt-oss-120b-medium", "GPT-OSS 120B (Medium)", listOf("Medium"))
    )
    val defaultModel = models[0] // gemini-3.8-flash-high

    private val _selectedModel = MutableStateFlow(defaultModel)
    val selectedModel: StateFlow<AntigravityModel> = _selectedModel.asStateFlow()

    fun selectModel(model: AntigravityModel) {
        _selectedModel.value = model
    }

    fun selectModelById(id: String): Boolean {
        val query = id.trim()
        if (query.isEmpty()) return false

        // 1. Exact match by id
        var found = models.find { it.id.equals(query, ignoreCase = true) }

        // 2. Exact match by displayName
        if (found == null) {
            found = models.find { it.displayName.equals(query, ignoreCase = true) }
        }

        // 3. Flexible matching by id containing query or normalized query (dots <-> dashes)
        val normalizedDash = query.replace(".", "-")
        if (found == null) {
            found = models.find {
                it.id.contains(query, ignoreCase = true) ||
                it.id.contains(normalizedDash, ignoreCase = true)
            }
        }

        // 4. Flexible matching by displayName containing query or normalized query
        val normalizedDot = query.replace("-", ".")
        if (found == null) {
            found = models.find {
                it.displayName.contains(query, ignoreCase = true) ||
                it.displayName.contains(normalizedDot, ignoreCase = true)
            }
        }

        // 5. Match by tag
        if (found == null) {
            found = models.find { model ->
                model.tags.any { tag -> tag.equals(query, ignoreCase = true) }
            }
        }

        if (found != null) {
            _selectedModel.value = found
            return true
        }
        return false
    }
}
