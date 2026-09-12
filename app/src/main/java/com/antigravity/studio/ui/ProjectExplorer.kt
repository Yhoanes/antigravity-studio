package com.antigravity.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.studio.model.GitFileStatus
import com.antigravity.studio.model.ProjectFile
import com.antigravity.studio.theme.AccentAmber
import com.antigravity.studio.theme.BorderObsidian
import com.antigravity.studio.theme.CosmicViolet
import com.antigravity.studio.theme.NeonCyan
import com.antigravity.studio.theme.StatusError
import com.antigravity.studio.theme.StatusSuccess
import com.antigravity.studio.theme.SurfaceElevated
import com.antigravity.studio.theme.SurfaceObsidian
import com.antigravity.studio.theme.TextMuted
import com.antigravity.studio.theme.TextPrimary
import com.antigravity.studio.theme.TextSecondary

/**
 * Collapsible Project Explorer panel displaying the workspace tree, Git statuses,
 * and quick-navigation items.
 */
@Composable
fun ProjectExplorer(
    workspaceRoot: String = "antigravity",
    files: List<ProjectFile>,
    onFileSelected: (ProjectFile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SurfaceObsidian)
            .border(width = 1.dp, color = BorderObsidian)
    ) {
        // --- Header Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(SurfaceElevated)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📁",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = workspaceRoot.uppercase(),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Quick Git branch pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(CosmicViolet.copy(alpha = 0.2f))
                    .border(0.5.dp, CosmicViolet.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "⎇ main",
                    color = CosmicViolet,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // --- File Tree ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            items(files) { file ->
                FileTreeItemRow(
                    file = file,
                    depth = 0,
                    onFileSelected = onFileSelected
                )
            }
        }

        // --- Bottom Workspace Metrics ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(SurfaceObsidian)
                .border(width = 0.5.dp, color = BorderObsidian)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "PRoot Ubuntu ARM64",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "UTF-8",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun FileTreeItemRow(
    file: ProjectFile,
    depth: Int,
    onFileSelected: (ProjectFile) -> Unit
) {
    var isExpanded by remember { mutableStateOf(file.isExpanded) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clickable {
                    if (file.isDirectory) {
                        isExpanded = !isExpanded
                    }
                    onFileSelected(file)
                }
                .padding(start = (12 + depth * 14).dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folder arrow or blank space
            if (file.isDirectory) {
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    modifier = Modifier.width(14.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(14.dp))
            }

            // File / Folder icon
            val iconGlyph = when {
                file.isDirectory -> if (isExpanded) "📂" else "📁"
                file.name.endsWith(".kt") -> "🟣"
                file.name.endsWith(".cpp") || file.name.endsWith(".h") -> "🔵"
                file.name.endsWith(".py") -> "🐍"
                file.name.endsWith(".md") -> "📝"
                file.name.endsWith(".json") -> "⚙️"
                file.name.endsWith(".html") -> "🌐"
                else -> "📄"
            }

            Text(
                text = iconGlyph,
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 6.dp)
            )

            // File name
            Text(
                text = file.name,
                color = if (file.isDirectory) TextPrimary else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Git status badge
            when (file.gitStatus) {
                GitFileStatus.MODIFIED -> {
                    Text(
                        text = "M",
                        color = AccentAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                GitFileStatus.STAGED -> {
                    Text(
                        text = "A",
                        color = StatusSuccess,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                GitFileStatus.UNTRACKED -> {
                    Text(
                        text = "U",
                        color = StatusError,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                GitFileStatus.UNMODIFIED -> Unit
            }
        }

        // Render children recursively if expanded
        if (file.isDirectory && isExpanded) {
            file.children.forEach { child ->
                FileTreeItemRow(
                    file = child,
                    depth = depth + 1,
                    onFileSelected = onFileSelected
                )
            }
        }
    }
}
