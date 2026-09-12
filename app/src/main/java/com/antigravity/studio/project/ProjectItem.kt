package com.antigravity.studio.project

import java.io.File

/**
 * Entidad de dominio que describe un proyecto local alojado en $filesDir/projects/.
 * Conforme a SPEC-003.
 */
data class ProjectItem(
    val id: String,
    val name: String,
    val absolutePath: String,
    val lastModified: Long,
    val gitBranch: String? = null,
    val hasAntigravityConfig: Boolean = false,
    val rootDirectory: File = File(absolutePath),
    val fileCount: Int = rootDirectory.listFiles()?.size ?: 0,
    val sizeBytes: Long = 0L
) {
    val activeGitBranch: String? get() = gitBranch
}
