package com.antigravity.studio.model

import androidx.compose.ui.graphics.Color
import com.antigravity.studio.theme.AccentAmber
import com.antigravity.studio.theme.CosmicViolet
import com.antigravity.studio.theme.NeonCyan
import com.antigravity.studio.theme.StatusError
import com.antigravity.studio.theme.StatusSuccess

/**
 * High-level state representing the Antigravity Autonomous Agent.
 */
enum class AgentStatus(
    val label: String,
    val badgeText: String,
    val badgeColor: Color,
    val isAnimated: Boolean
) {
    READY(
        label = "Agent Ready",
        badgeText = "🟢 AGENT READY",
        badgeColor = StatusSuccess,
        isAnimated = false
    ),
    REASONING(
        label = "Reasoning",
        badgeText = "🟣 REASONING",
        badgeColor = CosmicViolet,
        isAnimated = true
    ),
    EXECUTING(
        label = "Executing",
        badgeText = "🔵 EXECUTING",
        badgeColor = NeonCyan,
        isAnimated = true
    ),
    WARNING(
        label = "Linter Alert",
        badgeText = "🟡 WARNING",
        badgeColor = AccentAmber,
        isAnimated = false
    ),
    ERROR(
        label = "Agent Error",
        badgeText = "🔴 ERROR",
        badgeColor = StatusError,
        isAnimated = false
    )
}

data class AgentSessionState(
    val status: AgentStatus = AgentStatus.READY,
    val currentTask: String = "Idle - Waiting for instructions",
    val activeTool: String? = null,
    val iterationCount: Int = 0,
    val memoryNodesActive: Int = 12,
    val activeModel: String = "Gemini 2.5 Flash"
)
