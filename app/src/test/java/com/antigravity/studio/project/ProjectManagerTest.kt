package com.antigravity.studio.project

import com.antigravity.studio.agent.RealAgentEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectsRoot: File
    private lateinit var projectManager: ProjectManager

    @Before
    fun setUp() {
        projectsRoot = tempFolder.newFolder("projects")
        ProjectManager.resetInstanceForTest()
        projectManager = ProjectManager(projectsRoot)
    }

    @After
    fun tearDown() {
        ProjectManager.resetInstanceForTest()
        RealAgentEngine.workspaceDir = null
    }

    @Test
    fun testInitializationWithEmptyDirectoryCreatesDemoProjects() {
        val projects = projectManager.projectsFlow.value
        assertEquals("Debe crear exactamente 3 proyectos de demostración", 3, projects.size)

        val names = projects.map { it.name }.toSet()
        assertTrue("Debe incluir tateti", names.contains("tateti"))
        assertTrue("Debe incluir backend", names.contains("backend"))
        assertTrue("Debe incluir Analista", names.contains("Analista"))

        // Verificar archivos de tateti
        val tateti = projects.first { it.name == "tateti" }
        val tatetiDir = File(tateti.absolutePath)
        assertTrue(File(tatetiDir, "main.py").exists())
        assertTrue(File(tatetiDir, "test_tateti.py").exists())
        assertTrue(File(tatetiDir, "rules.md").exists())
        assertTrue(File(tatetiDir, ".antigravity/config.json").exists())
        assertTrue(tateti.hasAntigravityConfig)

        // Verificar archivos de backend
        val backend = projects.first { it.name == "backend" }
        val backendDir = File(backend.absolutePath)
        assertTrue(File(backendDir, "app.py").exists())
        assertTrue(File(backendDir, "requirements.txt").exists())
        assertTrue(File(backendDir, "README.md").exists())

        // Verificar archivos de Analista
        val analista = projects.first { it.name == "Analista" }
        val analistaDir = File(analista.absolutePath)
        assertTrue(File(analistaDir, "analyzer.py").exists())
        assertTrue(File(analistaDir, "data.csv").exists())
        assertTrue(File(analistaDir, "README.md").exists())

        // Verificar proyecto activo por defecto
        val active = projectManager.activeProject.value
        assertNotNull("Debe haber un proyecto activo inicial", active)
        assertNotNull("RealAgentEngine.workspaceDir debe estar configurado", RealAgentEngine.workspaceDir)
    }

    @Test
    fun testProjectsSortedByLastModifiedDescending() {
        val tatetiDir = File(projectsRoot, "tateti")
        val backendDir = File(projectsRoot, "backend")
        val analistaDir = File(projectsRoot, "Analista")

        // Asignar timestamps específicos y diferenciados
        val baseTime = 1700000000000L
        tatetiDir.setLastModified(baseTime + 3000)
        backendDir.setLastModified(baseTime + 1000)
        analistaDir.setLastModified(baseTime + 2000)

        val scanned = projectManager.scanProjects()
        assertEquals("tateti", scanned[0].name)
        assertEquals("Analista", scanned[1].name)
        assertEquals("backend", scanned[2].name)

        assertTrue(scanned[0].lastModified >= scanned[1].lastModified)
        assertTrue(scanned[1].lastModified >= scanned[2].lastModified)
    }

    @Test
    fun testCreateProjectWithPythonCliTemplate() {
        val created = projectManager.createProject("cli_tool", ProjectTemplate.PYTHON_CLI)
        assertEquals("cli_tool", created.name)
        assertTrue(created.hasAntigravityConfig)

        val dir = File(created.absolutePath)
        assertTrue(File(dir, "main.py").exists())
        assertTrue(File(dir, "requirements.txt").exists())
        assertTrue(File(dir, "README.md").exists())

        val mainPy = File(dir, "main.py").readText(Charsets.UTF_8)
        assertTrue(mainPy.contains("cli_tool"))

        // Debe ser ahora el proyecto activo
        assertEquals("cli_tool", projectManager.activeProject.value?.name)
        assertEquals(dir.absolutePath, RealAgentEngine.workspaceDir?.absolutePath)
    }

    @Test
    fun testCreateProjectWithNodeJsApiTemplate() {
        val created = projectManager.createProject("express_api", ProjectTemplate.NODEJS_API)
        assertEquals("express_api", created.name)

        val dir = File(created.absolutePath)
        assertTrue(File(dir, "package.json").exists())
        assertTrue(File(dir, "index.js").exists())
        assertTrue(File(dir, "README.md").exists())

        val pkgJson = File(dir, "package.json").readText(Charsets.UTF_8)
        assertTrue(pkgJson.contains("express_api"))
    }

    @Test
    fun testCreateProjectWithBashScriptTemplate() {
        val created = projectManager.createProject("ops_scripts", ProjectTemplate.BASH_SCRIPT)
        assertEquals("ops_scripts", created.name)

        val dir = File(created.absolutePath)
        val script = File(dir, "script.sh")
        assertTrue(script.exists())
        assertTrue(File(dir, "README.md").exists())
        val content = script.readText(Charsets.UTF_8)
        assertTrue(content.contains("ops_scripts"))
    }

    @Test
    fun testCreateProjectWithEmptyTemplate() {
        val created = projectManager.createProject("blank_canvas", ProjectTemplate.EMPTY)
        assertEquals("blank_canvas", created.name)

        val dir = File(created.absolutePath)
        assertTrue(dir.exists())
        assertTrue(File(dir, "README.md").exists())
        assertTrue(File(dir, ".antigravity/config.json").exists())
    }

    @Test
    fun testSelectProjectUpdatesWorkspaceAndNotifiesListener() {
        var notifiedProject: ProjectItem? = null
        projectManager.onProjectChangedListener = { project ->
            notifiedProject = project
        }

        val backend = projectManager.projectsFlow.value.first { it.name == "backend" }
        projectManager.selectProject(backend)

        assertEquals("backend", projectManager.activeProject.value?.name)
        assertEquals(backend.absolutePath, RealAgentEngine.workspaceDir?.absolutePath)
        assertNotNull(notifiedProject)
        assertEquals("backend", notifiedProject?.name)
    }

    @Test
    fun testSwitchProjectSuccessAndFailure() {
        val resultSuccess = projectManager.switchProject("Analista")
        assertTrue(resultSuccess.isSuccess)
        assertEquals("Analista", resultSuccess.getOrNull()?.name)
        assertEquals("Analista", projectManager.activeProject.value?.name)

        val resultFailure = projectManager.switchProject("inexistente_123")
        assertTrue(resultFailure.isFailure)
    }

    @Test
    fun testDeleteProjectRemovesFromFlow() {
        val created = projectManager.createProject("temp_delete_test", ProjectTemplate.EMPTY)
        assertTrue(projectManager.projectsFlow.value.any { it.name == "temp_delete_test" })

        val delResult = projectManager.deleteProject("temp_delete_test")
        assertTrue(delResult.isSuccess)
        assertFalse(File(created.absolutePath).exists())
        assertFalse(projectManager.projectsFlow.value.any { it.name == "temp_delete_test" })
    }

    @Test
    fun testGitBranchDetection() {
        val gitProjectDir = File(projectsRoot, "git_project").apply { mkdirs() }
        val gitDir = File(gitProjectDir, ".git").apply { mkdirs() }
        val headFile = File(gitDir, "HEAD")
        headFile.writeText("ref: refs/heads/feature/snapdragon-870\n", Charsets.UTF_8)

        val scanned = projectManager.scanProjects()
        val gitProj = scanned.firstOrNull { it.name == "git_project" }
        assertNotNull(gitProj)
        assertEquals("feature/snapdragon-870", gitProj?.gitBranch)
    }

    @Test
    fun testCloneGitRepositoryInvalidUrlFailsGracefully() {
        val result = projectManager.cloneGitRepository(
            url = "https://invalid-host-that-does-not-exist.org/repo.git",
            targetName = "failing_clone"
        )
        assertTrue("El clonado con URL inválida debe retornar Result.failure", result.isFailure)
        assertFalse(File(projectsRoot, "failing_clone").exists())
    }
}
