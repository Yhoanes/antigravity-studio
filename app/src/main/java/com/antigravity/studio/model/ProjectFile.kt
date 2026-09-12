package com.antigravity.studio.model

/**
 * Model representing files and directories in the Antigravity project explorer.
 */
data class ProjectFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<ProjectFile> = emptyList(),
    val isExpanded: Boolean = false,
    val gitStatus: GitFileStatus = GitFileStatus.UNMODIFIED,
    val sizeBytes: Long = 0
)

enum class GitFileStatus {
    UNMODIFIED,
    MODIFIED,
    STAGED,
    UNTRACKED
}
