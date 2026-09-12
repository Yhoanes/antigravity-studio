package com.antigravity.studio.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AgyBootstrapTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDeployedAgyScriptCompliesWithAutonomousAgentSpec() {
        val homeDir = tempFolder.newFolder("home")
        val binDir = File(homeDir, "bin").apply { mkdirs() }
        val workspaceDir = File(homeDir, "workspace").apply { mkdirs() }
        val agyFile = File(binDir, "agy")

        AgyBootstrap.deployAgyScript(
            agyFile = agyFile,
            homePath = homeDir.absolutePath,
            workspacePath = workspaceDir.absolutePath
        )

        assertTrue("Agy file must be created", agyFile.exists())
        val scriptContent = agyFile.readText(Charsets.UTF_8)

        // 1. Must not contain mock run_test or QA Harness
        assertFalse("Script must not contain run_test function", scriptContent.contains("run_test"))
        assertFalse("Script must not contain QA Harness mock code", scriptContent.contains("QA Harness"))
        assertFalse("Script must not contain mock PASS responses", scriptContent.contains("Criterios Verificados"))

        // 2. Must not depend on curl for agent execution
        assertFalse("Script must not use curl for Gemini calls", scriptContent.contains("curl "))

        // 3. Must use native Android ActivityManager broadcast
        assertTrue(
            "Script must invoke am broadcast for RUN_AGENT",
            scriptContent.contains("/system/bin/am broadcast -a com.antigravity.studio.RUN_AGENT --es prompt")
        )

        // 4. Must display Antigravity 2.0 (Gemini 2.5) branding in banners and runtime
        assertTrue(
            "Script banner must reflect Antigravity 2.0 (Gemini 2.5)",
            scriptContent.contains("Antigravity 2.0 (Gemini 2.5)")
        )

        // 5. Interactive agent loop prompt must be agy:agent>
        assertTrue(
            "Script must present agy:agent> prompt",
            scriptContent.contains("agy:agent>")
        )
    }
}
