package com.antigravity.studio.core.permission

typealias AgentPermissionPolicy = com.antigravity.studio.settings.AgentPermissionPolicy
typealias ActionCategory = com.antigravity.studio.settings.ActionCategory

data class ApprovalRequest(
    val requestId: String,
    val category: RequestCategory,
    val targetResource: String,
    val commandSnippet: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

enum class RequestCategory {
    FILE_READ,
    FILE_WRITE,
    BASH_COMMAND_SAFE,
    BASH_COMMAND_RISKY
}
