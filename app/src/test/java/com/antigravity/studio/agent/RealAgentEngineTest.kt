package com.antigravity.studio.agent

import com.antigravity.studio.formatModelListMessage
import com.antigravity.studio.handleModelSelectionCommand
import com.antigravity.studio.model.AntigravityModelCatalog
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealAgentEngineTest {

    private val originalClient = RealAgentEngine.httpClient

    @Before
    fun setUp() {
        RealAgentEngine.setCompanionProjectId(null)
        AntigravityModelCatalog.selectModel(AntigravityModelCatalog.defaultModel)
    }

    @After
    fun tearDown() {
        RealAgentEngine.httpClient = originalClient
        RealAgentEngine.setCompanionProjectId(null)
        AntigravityModelCatalog.selectModel(AntigravityModelCatalog.defaultModel)
    }

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

    @Test
    fun testExtractProjectIdWithString() {
        val json = JSONObject("""{"cloudaicompanionProject": "projects/cloud-ai-ultra-999"}""")
        val result = RealAgentEngine.extractProjectId(json)
        assertEquals("projects/cloud-ai-ultra-999", result)
    }

    @Test
    fun testExtractProjectIdWithJsonObject() {
        val json = JSONObject("""{"cloudaicompanionProject": {"id": "projects/project-with-id-123"}}""")
        val result = RealAgentEngine.extractProjectId(json)
        assertEquals("projects/project-with-id-123", result)
    }

    @Test
    fun testExtractProjectIdWithJsonObjectAndName() {
        val json = JSONObject("""{"cloudaicompanionProject": {"name": "projects/project-with-name-456"}}""")
        val result = RealAgentEngine.extractProjectId(json)
        assertEquals("projects/project-with-name-456", result)
    }

    @Test
    fun testExtractProjectIdWithNullAndMissing() {
        val emptyJson = JSONObject("{}")
        assertNull(RealAgentEngine.extractProjectId(emptyJson))

        val nullStrJson = JSONObject("""{"cloudaicompanionProject": "null"}""")
        assertNull(RealAgentEngine.extractProjectId(nullStrJson))
    }

    @Test
    fun testResolveCompanionProjectLoadCodeAssistSuccess() = runBlocking {
        RealAgentEngine.httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                if (url.contains("loadCodeAssist")) {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"cloudaicompanionProject": "projects/ultra-load-success"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        val resolved = RealAgentEngine.resolveCompanionProjectForTest("fake-bearer-token")
        assertEquals("projects/ultra-load-success", resolved)
        assertEquals("projects/ultra-load-success", RealAgentEngine.getCompanionProjectId())
    }

    @Test
    fun testResolveCompanionProjectFallbackOnboardUser() = runBlocking {
        RealAgentEngine.httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                if (url.contains("loadCodeAssist")) {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                } else if (url.contains("onboardUser")) {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"cloudaicompanionProject": {"id": "projects/ultra-onboarded-project"}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Error")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        val resolved = RealAgentEngine.resolveCompanionProjectForTest("fake-bearer-token")
        assertEquals("projects/ultra-onboarded-project", resolved)
        assertEquals("projects/ultra-onboarded-project", RealAgentEngine.getCompanionProjectId())
    }

    @Test
    fun testResolveCompanionProjectBothFailReturnsEmpty() = runBlocking {
        RealAgentEngine.httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val resolved = RealAgentEngine.resolveCompanionProjectForTest("fake-bearer-token")
        assertEquals("", resolved)
        assertNull(RealAgentEngine.getCompanionProjectId())
    }

    @Test
    fun testFormatModelListMessage() {
        AntigravityModelCatalog.selectModelById("claude-sonnet-4.6")
        val message = formatModelListMessage()

        assertTrue(message.contains("Modelos disponibles en Antigravity Studio:"))
        assertTrue(message.contains("Usa /model <nombre> para cambiar o toca el badge inferior."))
        assertTrue(message.contains("Claude Sonnet 4.6"))
        assertTrue(message.contains("* ACTIVO"))

        for (model in AntigravityModelCatalog.models) {
            assertTrue("Message should contain model ${model.id}", message.contains(model.id))
        }
    }

    @Test
    fun testHandleModelSelectionCommandSuccessAndFailure() {
        val successMsg = handleModelSelectionCommand("claude-opus-4.6")
        assertTrue(successMsg.contains("✓ Modelo cambiado a: Claude Opus 4.6 (Thinking)"))
        assertEquals("claude-opus-4.6", AntigravityModelCatalog.selectedModel.value.id)

        val failMsg = handleModelSelectionCommand("invalid-model-name")
        assertTrue(failMsg.contains("✖ Modelo no encontrado: 'invalid-model-name'. Escribe /model para ver la lista."))
        assertEquals("claude-opus-4.6", AntigravityModelCatalog.selectedModel.value.id)
    }
}
