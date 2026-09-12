package com.antigravity.studio.project

import android.util.Log
import com.antigravity.studio.agent.RealAgentEngine
import com.antigravity.studio.auth.GoogleOAuthManager
import com.antigravity.studio.pty.TerminalSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Gestor reactivo de proyectos en Antigravity Studio.
 * Administra el almacenamiento local en $filesDir/projects/, detección de repositorios Git,
 * inicialización de plantillas y vinculación atómica con el motor agéntico y la sesión PTY.
 * Conforme a SPEC-003.
 */
class ProjectManager(
    val projectsDir: File,
    private var ptySessionProvider: (() -> TerminalSession?)? = null,
    var onProjectChangedListener: ((ProjectItem) -> Unit)? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _projectsFlow = MutableStateFlow<List<ProjectItem>>(emptyList())
    val projectsFlow: StateFlow<List<ProjectItem>> = _projectsFlow.asStateFlow()
    val projects: StateFlow<List<ProjectItem>> get() = projectsFlow

    private val _activeProject = MutableStateFlow<ProjectItem?>(null)
    val activeProject: StateFlow<ProjectItem?> = _activeProject.asStateFlow()

    init {
        ensureDirectoryExists(projectsDir)
        initializeDemoProjectsIfEmpty()
        scanProjects()
        if (_activeProject.value == null && _projectsFlow.value.isNotEmpty()) {
            selectProject(_projectsFlow.value.first())
        }
    }

    fun setPtySessionProvider(provider: () -> TerminalSession?) {
        this.ptySessionProvider = provider
    }

    fun getProjectsRoot(): File = projectsDir

    /**
     * Escanea el directorio de proyectos y actualiza el StateFlow ordenado por
     * lastModified descendente.
     */
    @Synchronized
    fun scanProjects(): List<ProjectItem> {
        ensureDirectoryExists(projectsDir)
        val directories = projectsDir.listFiles { file -> file.isDirectory } ?: emptyArray()

        val items = directories.map { dir ->
            val branch = detectGitBranch(dir)
            val hasAntigravity = File(dir, ".antigravity").exists() || File(dir, ".antigravity/config.json").exists()
            val totalSize = calculateDirectorySize(dir)
            ProjectItem(
                id = dir.name,
                name = dir.name,
                absolutePath = dir.absolutePath,
                lastModified = dir.lastModified(),
                gitBranch = branch,
                hasAntigravityConfig = hasAntigravity,
                rootDirectory = dir,
                fileCount = dir.listFiles()?.size ?: 0,
                sizeBytes = totalSize
            )
        }.sortedByDescending { it.lastModified }

        _projectsFlow.value = items

        // Si el proyecto activo ya no existe o es nulo, actualizarlo
        val currentActive = _activeProject.value
        if (currentActive == null || items.none { it.id == currentActive.id }) {
            if (items.isNotEmpty()) {
                val newActive = items.first()
                _activeProject.value = newActive
                RealAgentEngine.workspaceDir = File(newActive.absolutePath)
            } else {
                _activeProject.value = null
                RealAgentEngine.workspaceDir = null
            }
        } else {
            // Actualizar referencia del activo con los metadatos actualizados
            val updatedActive = items.firstOrNull { it.id == currentActive.id }
            if (updatedActive != null) {
                _activeProject.value = updatedActive
            }
        }

        return items
    }

    /**
     * Crea un nuevo proyecto a partir de una plantilla dada.
     */
    @Synchronized
    fun createProject(name: String, template: ProjectTemplate): ProjectItem {
        val sanitizedName = sanitizeProjectName(name)
        val projectDir = File(projectsDir, sanitizedName)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }

        val antigravityDir = File(projectDir, ".antigravity").apply { mkdirs() }
        File(antigravityDir, "config.json").writeText(
            """
            {
              "name": "$sanitizedName",
              "template": "${template.name}",
              "createdAt": ${System.currentTimeMillis()}
            }
            """.trimIndent(),
            Charsets.UTF_8
        )

        when (template) {
            ProjectTemplate.EMPTY -> {
                File(projectDir, "README.md").writeText(
                    "# $sanitizedName\n\nProyecto vacío creado en Antigravity Studio IDE.\n",
                    Charsets.UTF_8
                )
            }
            ProjectTemplate.PYTHON_CLI -> {
                File(projectDir, "main.py").writeText(
                    """
                    #!/usr/bin/env python3
                    import sys

                    def main():
                        print("⚡ Hola desde $sanitizedName en Antigravity Studio!")
                        print(f"Python versión: {sys.version}")

                    if __name__ == "__main__":
                        main()
                    """.trimIndent(),
                    Charsets.UTF_8
                )
                File(projectDir, "requirements.txt").writeText(
                    "# Dependencias del proyecto\npytest>=8.0.0\n",
                    Charsets.UTF_8
                )
                File(projectDir, "README.md").writeText(
                    "# $sanitizedName (Python CLI)\n\nEjecuta con:\n```bash\npython3 main.py\n```\n",
                    Charsets.UTF_8
                )
            }
            ProjectTemplate.NODEJS_API -> {
                File(projectDir, "package.json").writeText(
                    """
                    {
                      "name": "$sanitizedName",
                      "version": "1.0.0",
                      "description": "Node.js API creada en Antigravity Studio",
                      "main": "index.js",
                      "scripts": {
                        "start": "node index.js"
                      }
                    }
                    """.trimIndent(),
                    Charsets.UTF_8
                )
                File(projectDir, "index.js").writeText(
                    """
                    const http = require('http');
                    const port = 3000;

                    const server = http.createServer((req, res) => {
                      res.statusCode = 200;
                      res.setHeader('Content-Type', 'application/json');
                      res.end(JSON.stringify({ message: "Servidor activo en $sanitizedName", uptime: process.uptime() }));
                    });

                    server.listen(port, () => {
                      console.log(`Servidor escuchando en http://localhost:${'$'}{port}`);
                    });
                    """.trimIndent(),
                    Charsets.UTF_8
                )
                File(projectDir, "README.md").writeText(
                    "# $sanitizedName (Node.js API)\n\nInicia con:\n```bash\nnode index.js\n```\n",
                    Charsets.UTF_8
                )
            }
            ProjectTemplate.BASH_SCRIPT -> {
                val script = File(projectDir, "script.sh")
                script.writeText(
                    """
                    #!/usr/bin/env bash
                    set -euo pipefail

                    echo "🚀 Ejecutando script de automatización en $sanitizedName"
                    echo "Directorio actual: $(pwd)"
                    echo "Fecha: $(date)"
                    """.trimIndent(),
                    Charsets.UTF_8
                )
                script.setExecutable(true, false)
                File(projectDir, "README.md").writeText(
                    "# $sanitizedName (Bash Script)\n\nEjecuta con:\n```bash\nbash script.sh\n```\n",
                    Charsets.UTF_8
                )
            }
        }

        projectDir.setLastModified(System.currentTimeMillis())
        val scanned = scanProjects()
        val created = scanned.firstOrNull { it.name == sanitizedName }
            ?: ProjectItem(
                id = sanitizedName,
                name = sanitizedName,
                absolutePath = projectDir.absolutePath,
                lastModified = projectDir.lastModified(),
                gitBranch = null,
                hasAntigravityConfig = true,
                rootDirectory = projectDir,
                fileCount = projectDir.listFiles()?.size ?: 0
            )

        selectProject(created)
        return created
    }

    /**
     * Selecciona el proyecto activo, actualiza RealAgentEngine.workspaceDir
     * y notifica a la sesión PTY activa para cambiar de directorio de trabajo.
     */
    fun selectProject(project: ProjectItem) {
        _activeProject.value = project
        RealAgentEngine.workspaceDir = File(project.absolutePath)

        // Notificar al listener registrado si existe
        onProjectChangedListener?.invoke(project)

        // Notificar a la sesión PTY activa
        val ptySession = ptySessionProvider?.invoke()
        if (ptySession != null && ptySession.isRunning) {
            val cdCommand = "cd \"${project.absolutePath}\" && clear\n"
            ptySession.tryWrite(cdCommand.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Cambia de proyecto mediante su identificador único.
     */
    fun switchProject(projectId: String): Result<ProjectItem> {
        val target = _projectsFlow.value.firstOrNull { it.id == projectId }
            ?: scanProjects().firstOrNull { it.id == projectId }

        return if (target != null) {
            selectProject(target)
            Result.success(target)
        } else {
            Result.failure(NoSuchElementException("Proyecto no encontrado: $projectId"))
        }
    }

    /**
     * Clona un repositorio Git remoto en el directorio de proyectos.
     */
    fun cloneGitRepository(url: String, targetName: String): Result<ProjectItem> {
        val sanitized = sanitizeProjectName(targetName)
        val targetDir = File(projectsDir, sanitized)

        if (targetDir.exists() && (targetDir.listFiles()?.isNotEmpty() == true)) {
            return Result.failure(IllegalStateException("El directorio de destino ya existe y no está vacío: $sanitized"))
        }

        return try {
            val process = ProcessBuilder("git", "clone", url, sanitized)
                .directory(projectsDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                val scanned = scanProjects()
                val clonedItem = scanned.firstOrNull { it.name == sanitized }
                if (clonedItem != null) {
                    selectProject(clonedItem)
                    Result.success(clonedItem)
                } else {
                    val fallbackItem = ProjectItem(
                        id = sanitized,
                        name = sanitized,
                        absolutePath = targetDir.absolutePath,
                        lastModified = targetDir.lastModified(),
                        gitBranch = detectGitBranch(targetDir),
                        hasAntigravityConfig = File(targetDir, ".antigravity").exists(),
                        rootDirectory = targetDir,
                        fileCount = targetDir.listFiles()?.size ?: 0
                    )
                    selectProject(fallbackItem)
                    Result.success(fallbackItem)
                }
            } else {
                Result.failure(RuntimeException("git clone falló con código $exitCode: $output"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Elimina un proyecto del almacenamiento.
     */
    fun deleteProject(projectId: String): Result<Unit> {
        val target = _projectsFlow.value.firstOrNull { it.id == projectId }
            ?: return Result.failure(NoSuchElementException("Proyecto no encontrado: $projectId"))

        val dir = File(target.absolutePath)
        val deleted = dir.deleteRecursively()
        scanProjects()
        return if (deleted) Result.success(Unit) else Result.failure(RuntimeException("No se pudo eliminar el proyecto: $projectId"))
    }

    /**
     * Inicializa los proyectos de demostración preconfigurados si el directorio está vacío:
     * - `tateti`: Aplicación interactiva con consola y tests.
     * - `backend`: Microservicio API REST local.
     * - `Analista`: Agente de procesamiento de datos con dataset CSV.
     */
    private fun initializeDemoProjectsIfEmpty() {
        val existing = projectsDir.listFiles { file -> file.isDirectory }
        if (!existing.isNullOrEmpty()) return

        // 1. tateti
        val tatetiDir = File(projectsDir, "tateti").apply { mkdirs() }
        File(tatetiDir, ".antigravity").apply { mkdirs() }
        File(tatetiDir, ".antigravity/config.json").writeText(
            """{"name": "tateti", "version": "1.0.0", "entry": "main.py"}""".trimIndent(),
            Charsets.UTF_8
        )
        File(tatetiDir, "main.py").writeText(
            """
            #!/usr/bin/env python3
            """ + """"Ta-Te-Ti (Tic-Tac-Toe) interactivo para Antigravity Studio IDE."""
            + """
            
            def print_board(b):
                print(f" {b[0]} | {b[1]} | {b[2]} ")
                print("---|---|---")
                print(f" {b[3]} | {b[4]} | {b[5]} ")
                print("---|---|---")
                print(f" {b[6]} | {b[7]} | {b[8]} ")

            def check_winner(b, player):
                wins = [
                    [0,1,2],[3,4,5],[6,7,8],
                    [0,3,6],[1,4,7],[2,5,8],
                    [0,4,8],[2,4,6]
                ]
                return any(all(b[idx] == player for idx in line) for line in wins)

            def is_board_full(b):
                return all(cell != " " for cell in b)

            if __name__ == "__main__":
                print("⚡ Ta-Te-Ti iniciado en Xiaomi Pad 6 / Antigravity Studio.")
                board = [" "] * 9
                print_board(board)
            """.trimIndent(),
            Charsets.UTF_8
        )
        File(tatetiDir, "test_tateti.py").writeText(
            """
            import unittest
            from main import check_winner, is_board_full

            class TestTateti(unittest.TestCase):
                def test_winner_row(self):
                    board = ["X", "X", "X", " ", " ", " ", " ", " ", " "]
                    self.assertTrue(check_winner(board, "X"))
                    self.assertFalse(check_winner(board, "O"))

                def test_winner_diagonal(self):
                    board = ["O", " ", " ", " ", "O", " ", " ", " ", "O"]
                    self.assertTrue(check_winner(board, "O"))

                def test_board_not_full(self):
                    board = ["X", "O", "X", " ", "O", "X", "O", "X", "O"]
                    self.assertFalse(is_board_full(board))

                def test_board_full(self):
                    board = ["X", "O", "X", "X", "O", "X", "O", "X", "O"]
                    self.assertTrue(is_board_full(board))

            if __name__ == "__main__":
                unittest.main()
            """.trimIndent(),
            Charsets.UTF_8
        )
        File(tatetiDir, "rules.md").writeText(
            """
            # Reglas de Ta-Te-Ti (Tic-Tac-Toe)

            1. Dos jugadores juegan por turnos: `X` y `O`.
            2. El tablero es de $3 \times 3$.
            3. El primero en alinear 3 fichas en horizontal, vertical o diagonal gana.
            4. Si el tablero se llena sin ganador, la partida termina en empate.
            """.trimIndent(),
            Charsets.UTF_8
        )
        // Fijar timestamp escalonado
        tatetiDir.setLastModified(System.currentTimeMillis() - 10_000)

        // 2. backend
        val backendDir = File(projectsDir, "backend").apply { mkdirs() }
        File(backendDir, ".antigravity").apply { mkdirs() }
        File(backendDir, ".antigravity/config.json").writeText(
            """{"name": "backend", "version": "1.0.0", "entry": "app.py"}""".trimIndent(),
            Charsets.UTF_8
        )
        File(backendDir, "app.py").writeText(
            """
            #!/usr/bin/env python3
            """ + """"Backend Microservicio de Antigravity Studio."""
            + """
            import json
            from http.server import HTTPServer, BaseHTTPRequestHandler

            class SimpleHandler(BaseHTTPRequestHandler):
                def do_GET(self):
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json')
                    self.end_headers()
                    response = {"status": "ok", "service": "antigravity-backend", "version": "1.0.0"}
                    self.wfile.write(json.dumps(response).encode('utf-8'))

            if __name__ == "__main__":
                server = HTTPServer(('127.0.0.1', 8080), SimpleHandler)
                print("⚡ Antigravity Backend escuchando en http://127.0.0.1:8080")
            """.trimIndent(),
            Charsets.UTF_8
        )
        File(backendDir, "requirements.txt").writeText(
            "fastapi>=0.111.0\nuvicorn>=0.30.1\n",
            Charsets.UTF_8
        )
        File(backendDir, "README.md").writeText(
            "# Backend Service\n\nMicroservicio REST local para Antigravity Studio IDE.\n",
            Charsets.UTF_8
        )
        backendDir.setLastModified(System.currentTimeMillis() - 20_000)

        // 3. Analista
        val analistaDir = File(projectsDir, "Analista").apply { mkdirs() }
        File(analistaDir, ".antigravity").apply { mkdirs() }
        File(analistaDir, ".antigravity/config.json").writeText(
            """{"name": "Analista", "version": "1.0.0", "entry": "analyzer.py"}""".trimIndent(),
            Charsets.UTF_8
        )
        File(analistaDir, "analyzer.py").writeText(
            """
            #!/usr/bin/env python3
            """ + """"Módulo de análisis exploratorio y telemetría de rendimiento."""
            + """
            import csv

            def analyze(csv_file):
                rows = []
                with open(csv_file, 'r', encoding='utf-8') as f:
                    reader = csv.DictReader(f)
                    for row in reader:
                        rows.append(float(row['cpu_usage']))
                if not rows:
                    return 0.0, 0.0
                return min(rows), max(rows)

            if __name__ == "__main__":
                min_cpu, max_cpu = analyze('data.csv')
                print(f"📊 Análisis de Telemetría: CPU Mín={min_cpu}%, CPU Máx={max_cpu}%")
            """.trimIndent(),
            Charsets.UTF_8
        )
        File(analistaDir, "data.csv").writeText(
            """timestamp,metric,cpu_usage,memory_mb
2026-09-12T10:00:00Z,proc_perf,12.5,450
2026-09-12T10:01:00Z,proc_perf,25.8,512
2026-09-12T10:02:00Z,proc_perf,42.1,620
2026-09-12T10:03:00Z,proc_perf,18.3,490
""".trimIndent(),
            Charsets.UTF_8
        )
        File(analistaDir, "README.md").writeText(
            "# Analista de Datos\n\nAgente de procesamiento y análisis autónomo de datos.\n",
            Charsets.UTF_8
        )
        analistaDir.setLastModified(System.currentTimeMillis() - 30_000)
    }

    private fun detectGitBranch(dir: File): String? {
        val gitHead = File(dir, ".git/HEAD")
        if (!gitHead.exists() || !gitHead.isFile) return null
        return try {
            val content = gitHead.readText(Charsets.UTF_8).trim()
            if (content.startsWith("ref: refs/heads/")) {
                content.removePrefix("ref: refs/heads/").trim()
            } else if (content.length >= 7) {
                content.take(7)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().maxDepth(5).forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }

    private fun ensureDirectoryExists(dir: File) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    private fun sanitizeProjectName(name: String): String {
        return name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    companion object {
        @Volatile
        private var defaultInstance: ProjectManager? = null

        fun getInstance(): ProjectManager {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: run {
                    val appContext = GoogleOAuthManager.getAppContext()
                    val filesDir = appContext?.filesDir ?: File("/data/user/0/com.antigravity.studio/files")
                    val mgr = ProjectManager(File(filesDir, "projects"))
                    defaultInstance = mgr
                    mgr
                }
            }
        }

        fun init(
            projectsDir: File,
            onProjectChanged: ((ProjectItem) -> Unit)? = null,
            ptySessionProvider: (() -> TerminalSession?)? = null
        ): ProjectManager {
            return synchronized(this) {
                val mgr = ProjectManager(projectsDir, ptySessionProvider, onProjectChanged)
                defaultInstance = mgr
                mgr
            }
        }

        fun resetInstanceForTest() {
            synchronized(this) {
                defaultInstance = null
            }
        }
    }
}
