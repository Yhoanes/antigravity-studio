package com.antigravity.studio.settings

/**
 * Matriz de políticas de auto-aprobación y gobernanza del motor agéntico.
 * Controla la intercepción táctil de operaciones de lectura, escritura y comandos de consola.
 * Conforme a SPEC-003.
 */
data class AgentPermissionPolicy(
    val autoApproveRead: Boolean = true,
    val autoApproveWrite: Boolean = false,
    val autoApproveSafeBash: Boolean = true,
    val requireApprovalForDestructive: Boolean = true
) {
    val autoApproveEdit: Boolean get() = autoApproveWrite
    val strictBlacklistEnabled: Boolean get() = requireApprovalForDestructive
}

/**
 * Categorías de acciones agénticas para evaluación de políticas.
 */
enum class ActionCategory {
    FILE_READ,
    FILE_WRITE,
    BASH_SAFE,
    BASH_DESTRUCTIVE
}
