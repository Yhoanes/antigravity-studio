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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.antigravity.studio.project.ProjectItem
import com.antigravity.studio.project.ProjectManager
import com.antigravity.studio.project.ProjectTemplate
import com.antigravity.studio.theme.AccentAmber
import com.antigravity.studio.theme.BorderObsidian
import com.antigravity.studio.theme.CosmicViolet
import com.antigravity.studio.theme.CyberObsidian
import com.antigravity.studio.theme.NeonCyan
import com.antigravity.studio.theme.StatusSuccess
import com.antigravity.studio.theme.SurfaceElevated
import com.antigravity.studio.theme.SurfaceObsidian
import com.antigravity.studio.theme.TextMuted
import com.antigravity.studio.theme.TextPrimary
import com.antigravity.studio.theme.TextSecondary
import java.io.File

/**
 * Barra Lateral Izquierda de Proyectos y Archivos para Antigravity Studio IDE en Xiaomi Pad 6.
 *
 * Estructura de dos secciones:
 * 1. Sección Superior: "Projects" con lista de tarjetas de proyectos y botón `+ New Project`.
 * 2. Sección Inferior: "Project file" con árbol jerárquico de archivos del proyecto activo.
 *
 * Conforme a SPEC-003 §4.1.
 */
@Composable
fun ProjectsSidebar(
    modifier: Modifier = Modifier,
    projectManager: ProjectManager = ProjectManager.getInstance(),
    onFileSelected: ((File) -> Unit)? = null,
    onProjectChanged: ((ProjectItem) -> Unit)? = null
) {
    val projects by projectManager.projectsFlow.collectAsState()
    val activeProject by projectManager.activeProject.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(SurfaceObsidian)
            .border(width = 1.dp, color = BorderObsidian)
    ) {
        // ====================================================================
        // SECCIÓN SUPERIOR: "Projects"
        // ====================================================================
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
                Text(text = "📁", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PROJECTS",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Botón + New Project
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonCyan.copy(alpha = 0.15f))
                    .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .clickable { showCreateDialog = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "+",
                        color = NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "New Project",
                        color = NeonCyan,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Lista de proyectos tipo tarjetas compactas
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(projects, key = { it.id }) { project ->
                val isActive = project.id == activeProject?.id
                ProjectCardItem(
                    project = project,
                    isActive = isActive,
                    onClick = {
                        projectManager.selectProject(project)
                        onProjectChanged?.invoke(project)
                    }
                )
            }

            if (projects.isEmpty()) {
                item {
                    Text(
                        text = "No hay proyectos locales",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = BorderObsidian, thickness = 1.dp)

        // ====================================================================
        // SECCIÓN INFERIOR: "Project file" (Árbol de archivos del proyecto activo)
        // ====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(SurfaceElevated.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(text = "📂", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PROJECT FILES",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (activeProject != null) {
                Text(
                    text = activeProject?.name ?: "",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Explorador recursivo de archivos del directorio activo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val activeDir = activeProject?.let { File(it.absolutePath) }
            if (activeDir != null && activeDir.exists() && activeDir.isDirectory) {
                ActiveProjectFileTree(
                    rootDirectory = activeDir,
                    onFileSelected = onFileSelected
                )
            } else {
                Box(
                    modifier = Modifier.padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Selecciona o crea un proyecto para ver sus archivos.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Barra de estado de almacenamiento inferior
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
                text = "${projects.size} proyectos",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "PRoot /projects",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    // Diálogo interactivo para crear o clonar proyecto
    if (showCreateDialog) {
        CreateProjectDialog(
            isOpen = showCreateDialog,
            onDismiss = { showCreateDialog = false },
            onCreateProject = { name, template ->
                val created = projectManager.createProject(name, template)
                onProjectChanged?.invoke(created)
                showCreateDialog = false
            },
            onCloneProject = { url, name ->
                val result = projectManager.cloneGitRepository(url, name)
                result.onSuccess { cloned ->
                    onProjectChanged?.invoke(cloned)
                }
                showCreateDialog = false
            }
        )
    }
}

/**
 * Tarjeta compacta para cada proyecto en la lista superior.
 */
@Composable
private fun ProjectCardItem(
    project: ProjectItem,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (isActive) NeonCyan else BorderObsidian
    val backgroundColor = if (isActive) {
        SurfaceElevated
    } else {
        SurfaceObsidian.copy(alpha = 0.8f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(width = if (isActive) 1.5.dp else 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Icono de carpeta (abierta si activo)
            Text(
                text = if (isActive) "📂" else "📁",
                fontSize = 13.sp
            )

            Column {
                Text(
                    text = project.name,
                    color = if (isActive) NeonCyan else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (project.gitBranch != null) {
                    Text(
                        text = "⎇ ${project.gitBranch}",
                        color = CosmicViolet,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Indicador circular cian si es el proyecto activo
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(NeonCyan)
            )
        }
    }
}

/**
 * Árbol jerárquico recursivo de archivos del directorio del proyecto activo.
 */
@Composable
private fun ActiveProjectFileTree(
    rootDirectory: File,
    onFileSelected: ((File) -> Unit)?
) {
    // Mapa de directorios expandidos
    val expandedState = remember(rootDirectory.absolutePath) {
        mutableStateMapOf<String, Boolean>().apply {
            put(rootDirectory.absolutePath, true)
        }
    }

    val files = remember(rootDirectory.lastModified(), rootDirectory.absolutePath) {
        getSortedFiles(rootDirectory)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        items(files) { file ->
            RenderFileNode(
                file = file,
                depth = 0,
                expandedState = expandedState,
                onFileSelected = onFileSelected
            )
        }
    }
}

@Composable
private fun RenderFileNode(
    file: File,
    depth: Int,
    expandedState: MutableMap<String, Boolean>,
    onFileSelected: ((File) -> Unit)?
) {
    val isDir = file.isDirectory
    val isExpanded = expandedState[file.absolutePath] ?: false

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clickable {
                    if (isDir) {
                        expandedState[file.absolutePath] = !isExpanded
                    } else {
                        onFileSelected?.invoke(file)
                    }
                }
                .padding(start = (10 + depth * 12).dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flecha colapsable para directorios
            if (isDir) {
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    color = TextSecondary,
                    fontSize = 8.sp,
                    modifier = Modifier.width(12.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Ícono por tipo de archivo
            val iconGlyph = when {
                isDir -> if (isExpanded) "📂" else "📁"
                file.name.endsWith(".py") -> "🐍"
                file.name.endsWith(".md") -> "📝"
                file.name.endsWith(".json") -> "⚙️"
                file.name.endsWith(".kt") -> "🟣"
                file.name.endsWith(".sh") || file.name.endsWith(".bash") -> "🐚"
                file.name.endsWith(".js") || file.name.endsWith(".ts") -> "🟨"
                file.name.endsWith(".html") || file.name.endsWith(".css") -> "🌐"
                file.name.endsWith(".txt") -> "📄"
                else -> "📄"
            }

            Text(
                text = iconGlyph,
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 6.dp)
            )

            Text(
                text = file.name,
                color = if (isDir) TextPrimary else TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Hijos recursivos si está expandido
        if (isDir && isExpanded) {
            val children = remember(file.lastModified(), file.absolutePath) {
                getSortedFiles(file)
            }
            children.forEach { child ->
                RenderFileNode(
                    file = child,
                    depth = depth + 1,
                    expandedState = expandedState,
                    onFileSelected = onFileSelected
                )
            }
        }
    }
}

private fun getSortedFiles(dir: File): List<File> {
    val items = dir.listFiles() ?: return emptyList()
    // Ocultar .git interno por claridad
    val filtered = items.filter { it.name != ".git" }
    // Directorios primero, luego archivos, alfabéticamente
    return filtered.sortedWith(
        compareBy<File> { !it.isDirectory }
            .thenBy { it.name.lowercase() }
    )
}

/**
 * Diálogo interactivo CreateProjectDialog para crear un proyecto con plantillas
 * o clonarlo desde un repositorio Git remoto.
 */
@Composable
fun CreateProjectDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onCreateProject: (name: String, template: ProjectTemplate) -> Unit,
    onCloneProject: (url: String, targetName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    var selectedTab by remember { mutableStateOf(0) }
    var projectName by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(ProjectTemplate.PYTHON_CLI) }
    var gitRepoUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.width(500.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "⚡", fontSize = 18.sp)
                Text(
                    text = "Nuevo Proyecto en Antigravity Studio",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selector de modo: Plantilla vs Clonar
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceObsidian,
                    contentColor = NeonCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = NeonCyan
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "Plantilla Base",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) NeonCyan else TextSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "Clonar desde GitHub",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 1) NeonCyan else TextSecondary
                            )
                        }
                    )
                }

                if (selectedTab == 0) {
                    // MODO PLANTILLA
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Nombre del Proyecto") },
                        placeholder = { Text("ej. mi_nuevo_agente") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderObsidian,
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Seleccionar Plantilla:",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProjectTemplate.values().forEach { template ->
                            val isSelected = template == selectedTemplate
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) NeonCyan.copy(alpha = 0.12f) else SurfaceObsidian)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) NeonCyan else BorderObsidian,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedTemplate = template }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = template.displayName,
                                            color = if (isSelected) NeonCyan else TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = template.description,
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp
                                        )
                                    }

                                    if (isSelected) {
                                        Text(
                                            text = "✓",
                                            color = NeonCyan,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // MODO CLONAR DESDE GITHUB
                    OutlinedTextField(
                        value = gitRepoUrl,
                        onValueChange = { gitRepoUrl = it },
                        label = { Text("URL del Repositorio Git") },
                        placeholder = { Text("https://github.com/usuario/repo.git") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmicViolet,
                            unfocusedBorderColor = BorderObsidian,
                            focusedLabelColor = CosmicViolet,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Nombre Local de la Carpeta") },
                        placeholder = { Text("dejar vacío para usar nombre del repo") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderObsidian,
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        val name = projectName.ifBlank { "proyecto_${System.currentTimeMillis() % 1000}" }
                        onCreateProject(name, selectedTemplate)
                    } else {
                        val target = if (projectName.isNotBlank()) {
                            projectName
                        } else {
                            gitRepoUrl.substringAfterLast("/").removeSuffix(".git").ifBlank { "cloned_repo" }
                        }
                        onCloneProject(gitRepoUrl, target)
                    }
                },
                enabled = if (selectedTab == 0) projectName.isNotBlank() else gitRepoUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = CyberObsidian
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (selectedTab == 0) "Crear Proyecto" else "Clonar desde GitHub",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(BorderObsidian)
                ),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text(text = "Cancelar", fontSize = 13.sp)
            }
        }
    )
}
