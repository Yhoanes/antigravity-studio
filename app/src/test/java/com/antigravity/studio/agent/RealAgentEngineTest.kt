package com.antigravity.studio.agent

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealAgentEngineTest {

    @Test
    fun testExecuteAgentTaskWithoutAuthEmitsUnauthMessage() = runBlocking {
        val events = RealAgentEngine.executeAgentTask("Hola agente").toList()
        assertTrue(events.isNotEmpty())

        val firstEvent = events.first() as? AgentStreamEvent.TextDelta
        assertNotNull(firstEvent)
        assertTrue(firstEvent!!.text.contains("No se ha detectado una sesión activa"))
        assertTrue(firstEvent.text.contains("agy auth"))

        val lastEvent = events.last() as? AgentStreamEvent.Completed
        assertNotNull(lastEvent)
        assertEquals(0, lastEvent!!.totalTokens)
    }
}
