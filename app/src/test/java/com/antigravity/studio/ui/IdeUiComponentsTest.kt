package com.antigravity.studio.ui

import com.antigravity.studio.model.ide.CanvasDisplayMode
import com.antigravity.studio.model.ide.CanvasTabType
import com.antigravity.studio.model.ide.DualCanvasState
import com.antigravity.studio.model.ide.FileChangeSummary
import com.antigravity.studio.model.ide.InspectionDrawerState
import com.antigravity.studio.model.ide.SubagentCardData
import com.antigravity.studio.model.ide.SubagentExecutionStatus
import com.antigravity.studio.service.HyperOsTelemetryState
import com.antigravity.studio.service.StudioBackgroundService
import com.antigravity.studio.settings.AgentPermissionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de los modelos, estados y acciones de UI para Antigravity Studio IDE (SPEC-003).
 */
class IdeUiComponentsTest {

    @Test
    fun testDualCanvasStateDefaultsAndTabs() {
        val defaultState = DualCanvasState()
        assertEquals(CanvasTabType.AGENT_TERMINAL, defaultState.activeTab)
        assertEquals(CanvasDisplayMode.SINGLE_TAB, defaultState.displayMode)
        assertEquals(0.5f, defaultState.splitRatio, 0.001f)
        assertEquals(144, defaultState.terminalRefreshRateHz)

        assertEquals("Agent Terminal (144Hz)", CanvasTabType.AGENT_TERMINAL.title)
        assertEquals("Workspace Canvas", CanvasTabType.WORKSPACE_CODE.title)
    }

    @Test
    fun testSubagentCardDataFormattingAndStatus() {
        val subagent = SubagentCardData(
            id = "agent-arch",
            name = "tateti_architect",
            roleDescription = "Architecture generator",
            durationSeconds = 84L,
            toolCallsCount = 12,
            status = SubagentExecutionStatus.COMPLETED
        )

        assertEquals("tateti_architect", subagent.name)
        assertEquals("1m 24s", subagent.durationFormatted)
        assertEquals(SubagentExecutionStatus.COMPLETED, subagent.status)
        assertEquals("Completed ✓", subagent.status.label)

        val runningAgent = SubagentCardData(
            id = "agent-run",
            name = "code_runner",
            roleDescription = "Test runner",
            durationSeconds = 28L,
            status = SubagentExecutionStatus.EXECUTING,
            progressPercent = 0.72f
        )
        assertEquals("0m 28s", runningAgent.durationFormatted)
        assertEquals("Running ⟳", runningAgent.status.label)
    }

    @Test
    fun testFileChangeSummaryLinesCalculation() {
        val addedOnly = FileChangeSummary(relativePath = "main.py", linesAdded = 124)
        assertEquals("+124 lines", addedOnly.linesSummary)

        val both = FileChangeSummary(relativePath = "app.py", linesAdded = 14, linesDeleted = 2)
        assertEquals("+14 / -2", both.linesSummary)

        val deletedOnly = FileChangeSummary(relativePath = "old.py", linesDeleted = 5)
        assertEquals("-5 lines", deletedOnly.linesSummary)
    }

    @Test
    fun testKeyActionControlBytes() {
        // [✓ Aprobar (Ctrl+K)] emite 0x0B (\u000B)
        assertEquals(0x0B.toByte(), KeyAction.Approve.controlByte)

        // [⏹ Detener] emite 0x03 (SIGINT)
        assertEquals(0x03.toByte(), KeyAction.StopExecution.controlByte)

        val rawK = KeyAction.RawBytes(byteArrayOf(0x0B))
        val rawC = KeyAction.RawBytes(byteArrayOf(0x03))
        assertEquals(0x0B.toByte(), rawK.bytes[0])
        assertEquals(0x03.toByte(), rawC.bytes[0])
    }

    @Test
    fun testHyperOsTelemetryFlowCompatibility() {
        // Verificar que telemetryFlow es idéntico a telemetryState
        assertEquals(
            StudioBackgroundService.telemetryState.value,
            StudioBackgroundService.telemetryFlow.value
        )

        // Modificar directamente para prueba
        val testState = HyperOsTelemetryState(
            isServiceRunning = true,
            isWakeLockHeld = true,
            isWifiLockHeld = true,
            processRamUsageMb = 450L,
            totalDeviceRamMb = 6144L,
            cpuTemperatureCelsius = 38.5f
        )
        StudioBackgroundService.updateTelemetryStateDirectly(testState)

        assertEquals(testState, StudioBackgroundService.telemetryFlow.value)
        assertTrue(StudioBackgroundService.telemetryFlow.value.isWakeLockHeld)
        assertEquals(450L, StudioBackgroundService.telemetryFlow.value.processRamUsageMb)
        assertEquals(6144L, StudioBackgroundService.telemetryFlow.value.totalDeviceRamMb)
    }

    @Test
    fun testDefaultDemoDataPresence() {
        assertNotNull(defaultDemoSubagents)
        assertTrue(defaultDemoSubagents.isNotEmpty())
        assertEquals("tateti_architect", defaultDemoSubagents[0].name)
        assertEquals(SubagentExecutionStatus.COMPLETED, defaultDemoSubagents[0].status)

        assertNotNull(defaultDemoFilesChanged)
        assertTrue(defaultDemoFilesChanged.isNotEmpty())
        assertEquals("main.py", defaultDemoFilesChanged[0].relativePath)
        assertEquals(124, defaultDemoFilesChanged[0].linesAdded)
    }
}
