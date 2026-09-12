package com.antigravity.studio.agent

import com.antigravity.studio.auth.GoogleOAuthManager
import com.antigravity.studio.core.auth.OAuthTokens
import com.antigravity.studio.formatModelListMessage
import com.antigravity.studio.handleKeyCommand
import com.antigravity.studio.handleModelSelectionCommand
import com.antigravity.studio.model.AntigravityModelCatalog
import com.antigravity.studio.settings.AgentSettingsManager
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
import java.io.File

class RealAgentEngineTest {

    private val originalClient = RealAgentEngine.httpClient

    @Before
    fun setUp() {
        AgentSettingsManager.resetInstanceForTest()
        AgentSettingsManager.getInstance().setGeminiApiKey(null)
        GoogleOAuthManager.setCurrentTokensForTest(null)
        RealAgentEngine.setCompanionProjectId(null)
        AntigravityModelCatalog.selectModel(AntigravityModelCatalog.defaultModel)
    }

    @After
    fun tearDown() {
        RealAgentEngine.httpClient = originalClient
        RealAgentEngine.setCompanionProjectId(null)
        GoogleOAuthManager.setCurrentTokensForTest(null)
        AgentSettingsManager.getInstance().setGeminiApiKey(null)
        AgentSettingsManager.resetInstanceForTest()
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
    fun testResolveCompanionProjectReturnsDefaultProject() = runBlocking {
        val resolved = RealAgentEngine.resolveCompanionProjectForTest("fake-bearer-token")
        assertEquals(RealAgentEngine.DEFAULT_INFERENCE_PROJECT, resolved)
        assertEquals("default-cli-project", resolved)
        assertEquals(RealAgentEngine.DEFAULT_INFERENCE_PROJECT, RealAgentEngine.getCompanionProjectId())
    }

    @Test
    fun testExecuteAgentTaskBuildsRequestWithDailyCloudCodeUrlAndDefaultProject() = runBlocking {
        val testToken = "ya29.test-bearer-token-12345"
        GoogleOAuthManager.setCurrentTokensForTest(
            OAuthTokens(
                accessToken = testToken,
                refreshToken = "test-refresh-token",
                expiresAtEpochMs = System.currentTimeMillis() + 3600_000,
                scope = "openid email profile https://www.googleapis.com/auth/cloud-platform",
                accountId = "testuser@gmail.com"
            )
        )

        var capturedUrl: String? = null
        var capturedProject: String? = null
        var capturedModel: String? = null
        var capturedAuth: String? = null
        var capturedUserAgentHeader: String? = null
        var capturedAcceptHeader: String? = null
        var capturedUserAgentPayload: String? = null
        var capturedRequestType: String? = null
        var capturedCreditType: String? = null
        var capturedRequestId: String? = null

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
                        .code(404)
                        .message("Not Found")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                } else if (url.contains("streamGenerateContent")) {
                    capturedUrl = url
                    capturedAuth = request.header("Authorization")
                    capturedUserAgentHeader = request.header("User-Agent")
                    capturedAcceptHeader = request.header("Accept")
                    val buffer = okio.Buffer()
                    request.body?.writeTo(buffer)
                    val bodyJson = JSONObject(buffer.readUtf8())
                    capturedProject = bodyJson.optString("project")
                    capturedModel = bodyJson.optString("model")
                    capturedUserAgentPayload = bodyJson.optString("userAgent")
                    capturedRequestType = bodyJson.optString("requestType")
                    capturedRequestId = bodyJson.optString("requestId")
                    capturedCreditType = bodyJson.optJSONArray("enabledCreditTypes")?.optString(0)

                    val sseResponse = "data: {\"response\":{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Respuesta streaming exitosa\"}]}}]}}\n\n"
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "text/event-stream")
                        .body(sseResponse.toResponseBody("text/event-stream".toMediaType()))
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

        val events = RealAgentEngine.executeAgentTask("Prueba").toList()
        assertEquals("https://daily-cloudcode-pa.googleapis.com/v1internal:streamGenerateContent?alt=sse", capturedUrl)
        assertEquals("Bearer $testToken", capturedAuth)
        assertEquals("antigravity/1.2.2", capturedUserAgentHeader)
        assertEquals("text/event-stream", capturedAcceptHeader)
        assertEquals(RealAgentEngine.DEFAULT_INFERENCE_PROJECT, capturedProject)
        assertEquals("default-cli-project", capturedProject)
        assertEquals("gemini-3.8-flash-tiered", capturedModel)
        assertEquals("antigravity/1.2.2", capturedUserAgentPayload)
        assertEquals("REQUEST_TYPE_CASCADE", capturedRequestType)
        assertNull(capturedCreditType)
        assertNotNull(capturedRequestId)
        assertTrue(capturedRequestId!!.isNotEmpty())

        val textEvent = events.filterIsInstance<AgentStreamEvent.TextDelta>().firstOrNull()
        assertNotNull(textEvent)
        assertEquals("Respuesta streaming exitosa", textEvent?.text)
    }

    @Test
    fun testExecuteAgentTask403With3501RetriesWithFallbackProject() = runBlocking {
        val testToken = "ya29.test-bearer-token-12345"
        GoogleOAuthManager.setCurrentTokensForTest(
            OAuthTokens(
                accessToken = testToken,
                refreshToken = "test-refresh-token",
                expiresAtEpochMs = System.currentTimeMillis() + 3600_000,
                scope = "openid email profile https://www.googleapis.com/auth/cloud-platform",
                accountId = "testuser@gmail.com"
            )
        )
        RealAgentEngine.setCompanionProjectId(RealAgentEngine.DEFAULT_INFERENCE_PROJECT)

        var attemptCount = 0
        val requestProjects = mutableListOf<String>()

        RealAgentEngine.httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                if (url.contains("streamGenerateContent")) {
                    attemptCount++
                    val buffer = okio.Buffer()
                    request.body?.writeTo(buffer)
                    val bodyJson = JSONObject(buffer.readUtf8())
                    requestProjects.add(bodyJson.optString("project"))

                    if (attemptCount == 1) {
                        // Return HTTP 403 with #3501
                        val errorJson = """{"error": {"code": 403, "message": "#3501 (SUBSCRIPTION_REQUIRED): Cloud AI Companion project is required"}}"""
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(403)
                            .message("Forbidden")
                            .body(errorJson.toResponseBody("application/json".toMediaType()))
                            .build()
                    } else {
                        // Second attempt with fallback project succeeds
                        val sseResponse = "data: {\"response\":{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Recuperado con aicode-consumers\"}]}}]}}\n\n"
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "text/event-stream")
                            .body(sseResponse.toResponseBody("text/event-stream".toMediaType()))
                            .build()
                    }
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

        val events = RealAgentEngine.executeAgentTask("Prueba con 403").toList()
        assertEquals(2, attemptCount)
        assertEquals(RealAgentEngine.DEFAULT_INFERENCE_PROJECT, requestProjects[0])
        assertEquals("default-cli-project", requestProjects[0])
        assertEquals(RealAgentEngine.FALLBACK_INFERENCE_PROJECT, requestProjects[1])
        assertEquals("aicode-consumers", requestProjects[1])
        assertEquals(RealAgentEngine.FALLBACK_INFERENCE_PROJECT, RealAgentEngine.getCompanionProjectId())

        val textEvent = events.filterIsInstance<AgentStreamEvent.TextDelta>().firstOrNull()
        assertNotNull(textEvent)
        assertEquals("Recuperado con aicode-consumers", textEvent?.text)
    }

    @Test
    fun testExecuteAgentTask404RetriesViceVersaWithDefaultProject() = runBlocking {
        val testToken = "ya29.test-bearer-token-12345"
        GoogleOAuthManager.setCurrentTokensForTest(
            OAuthTokens(
                accessToken = testToken,
                refreshToken = "test-refresh-token",
                expiresAtEpochMs = System.currentTimeMillis() + 3600_000,
                scope = "openid email profile https://www.googleapis.com/auth/cloud-platform",
                accountId = "testuser@gmail.com"
            )
        )
        RealAgentEngine.setCompanionProjectId(RealAgentEngine.FALLBACK_INFERENCE_PROJECT)

        var attemptCount = 0
        val requestProjects = mutableListOf<String>()

        RealAgentEngine.httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                if (url.contains("streamGenerateContent")) {
                    attemptCount++
                    val buffer = okio.Buffer()
                    request.body?.writeTo(buffer)
                    val bodyJson = JSONObject(buffer.readUtf8())
                    requestProjects.add(bodyJson.optString("project"))

                    if (attemptCount == 1) {
                        // Return HTTP 404
                        val errorJson = """{"error": {"code": 404, "message": "Project not found"}}"""
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body(errorJson.toResponseBody("application/json".toMediaType()))
                            .build()
                    } else {
                        // Second attempt with default-cli-project succeeds
                        val sseResponse = "data: {\"response\":{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Recuperado con default-cli-project\"}]}}]}}\n\n"
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "text/event-stream")
                            .body(sseResponse.toResponseBody("text/event-stream".toMediaType()))
                            .build()
                    }
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

        val events = RealAgentEngine.executeAgentTask("Prueba con 404").toList()
        assertEquals(2, attemptCount)
        assertEquals(RealAgentEngine.FALLBACK_INFERENCE_PROJECT, requestProjects[0])
        assertEquals("aicode-consumers", requestProjects[0])
        assertEquals(RealAgentEngine.DEFAULT_INFERENCE_PROJECT, requestProjects[1])
        assertEquals("default-cli-project", requestProjects[1])
        assertEquals(RealAgentEngine.DEFAULT_INFERENCE_PROJECT, RealAgentEngine.getCompanionProjectId())

        val textEvent = events.filterIsInstance<AgentStreamEvent.TextDelta>().firstOrNull()
        assertNotNull(textEvent)
        assertEquals("Recuperado con default-cli-project", textEvent?.text)
    }

    @Test
    fun testExecuteAgentTaskBothFailEmitsFriendlyMessage() = runBlocking {
        val testToken = "ya29.test-bearer-token-12345"
        GoogleOAuthManager.setCurrentTokensForTest(
            OAuthTokens(
                accessToken = testToken,
                refreshToken = "test-refresh-token",
                expiresAtEpochMs = System.currentTimeMillis() + 3600_000,
                scope = "openid email profile https://www.googleapis.com/auth/cloud-platform",
                accountId = "testuser@gmail.com"
            )
        )
        RealAgentEngine.setCompanionProjectId(RealAgentEngine.DEFAULT_INFERENCE_PROJECT)

        var attemptCount = 0

        RealAgentEngine.httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                if (url.contains("streamGenerateContent")) {
                    attemptCount++
                    val errorJson = """{"error": {"code": 403, "message": "#3501 (SUBSCRIPTION_REQUIRED): Cloud AI Companion project is required"}}"""
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(403)
                        .message("Forbidden")
                        .body(errorJson.toResponseBody("application/json".toMediaType()))
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

        val events = RealAgentEngine.executeAgentTask("Prueba con doble fallo").toList()
        assertEquals(2, attemptCount)

        val textEvent = events.filterIsInstance<AgentStreamEvent.TextDelta>().firstOrNull()
        assertNotNull(textEvent)
        assertTrue(textEvent!!.text.contains("[!] Para activar la IA en tu tablet:"))
        assertTrue(textEvent.text.contains("Ingresa tu clave gratuita de Gemini (Google AI Studio)"))
        assertTrue(textEvent.text.contains("/key <tu-api-key>"))

        val completed = events.filterIsInstance<AgentStreamEvent.Completed>().firstOrNull()
        assertNotNull(completed)
    }

    @Test
    fun testMapToCloudCodeModel() {
        assertEquals("gemini-3.8-flash-tiered", RealAgentEngine.mapToCloudCodeModel("gemini-3.8-flash-high"))
        assertEquals("gemini-3.8-flash-tiered", RealAgentEngine.mapToCloudCodeModel("gemini-3.8-flash"))
        assertEquals("gemini-3.7-flash-tiered", RealAgentEngine.mapToCloudCodeModel("gemini-3.7-flash-high"))
        assertEquals("gemini-3.7-flash-tiered", RealAgentEngine.mapToCloudCodeModel("gemini-3.7-flash"))
        assertEquals("gemini-3.6-flash-tiered", RealAgentEngine.mapToCloudCodeModel("gemini-3.6-flash-high"))
        assertEquals("gemini-3.6-flash-tiered", RealAgentEngine.mapToCloudCodeModel("gemini-3.6-flash"))
        assertEquals("gemini-2.5-pro", RealAgentEngine.mapToCloudCodeModel("gemini-3.1-pro-low"))
        assertEquals("gemini-2.5-pro", RealAgentEngine.mapToCloudCodeModel("gemini-3.1-pro"))
        assertEquals("gemini-2.5-flash", RealAgentEngine.mapToCloudCodeModel("gemini-2.5-flash"))
        assertEquals("gemini-2.5-pro", RealAgentEngine.mapToCloudCodeModel("gemini-2.5-pro"))
        assertEquals("claude-sonnet-4-6", RealAgentEngine.mapToCloudCodeModel("claude-sonnet-4-6"))
        assertEquals("claude-opus-4-6-thinking", RealAgentEngine.mapToCloudCodeModel("claude-opus-4-6-thinking"))
        assertEquals("gpt-oss-120b-medium", RealAgentEngine.mapToCloudCodeModel("gpt-oss-120b-medium"))
        assertEquals("gemini-3.8-flash-tiered", RealAgentEngine.mapToCloudCodeModel("unknown-model-fallback"))
    }

    @Test
    fun testExecuteAgentTaskParsesThoughtParts() = runBlocking {
        val testToken = "ya29.test-bearer-token-12345"
        GoogleOAuthManager.setCurrentTokensForTest(
            OAuthTokens(
                accessToken = testToken,
                refreshToken = "test-refresh-token",
                expiresAtEpochMs = System.currentTimeMillis() + 3600_000,
                scope = "openid email profile https://www.googleapis.com/auth/cloud-platform",
                accountId = "testuser@gmail.com"
            )
        )
        RealAgentEngine.setCompanionProjectId(RealAgentEngine.DEFAULT_INFERENCE_PROJECT)

        RealAgentEngine.httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val sseResponse = "data: {\"response\":{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":\"Thinking deeply...\"},{\"text\":\"Here is the answer\"}]}}]}}\n\n"
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/event-stream")
                    .body(sseResponse.toResponseBody("text/event-stream".toMediaType()))
                    .build()
            }
            .build()

        val events = RealAgentEngine.executeAgentTask("Pensar").toList()
        val textEvents = events.filterIsInstance<AgentStreamEvent.TextDelta>()
        assertEquals(2, textEvents.size)
        assertTrue(textEvents[0].text.contains("Thinking deeply..."))
        assertTrue(textEvents[0].text.contains("\u001b[3m"))
        assertEquals("Here is the answer", textEvents[1].text)
    }

    @Test
    fun testDirectGeminiApiKeyInference() = runBlocking {
        val testApiKey = "AIzaSy-direct-test-key-999"
        AgentSettingsManager.getInstance().setGeminiApiKey(testApiKey)

        var capturedUrl: String? = null
        var capturedKey: String? = null
        RealAgentEngine.httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedUrl = request.url.toString()
                capturedKey = request.url.queryParameter("key")

                val sseResponse = "data: {\"response\":{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Inferencia directa desde Gemini 2.0 Flash\"}]}}]}}\n\n"
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/event-stream")
                    .body(sseResponse.toResponseBody("text/event-stream".toMediaType()))
                    .build()
            }
            .build()

        val events = RealAgentEngine.executeAgentTask("Generar código").toList()
        assertNotNull(capturedUrl)
        assertTrue(capturedUrl!!.contains("generativelanguage.googleapis.com"))
        assertTrue(capturedUrl!!.contains("gemini-2.0-flash"))
        assertEquals(testApiKey, capturedKey)

        val textEvent = events.filterIsInstance<AgentStreamEvent.TextDelta>().firstOrNull()
        assertNotNull(textEvent)
        assertEquals("Inferencia directa desde Gemini 2.0 Flash", textEvent?.text)

        val completed = events.filterIsInstance<AgentStreamEvent.Completed>().firstOrNull()
        assertNotNull(completed)
    }

    @Test
    fun testHandleKeyCommandWorkflow() {
        val tempDir = java.nio.file.Files.createTempDirectory("gemini-key-test").toFile()
        try {
            val response = handleKeyCommand("/key AIzaSyCustomKey123", tempDir)
            assertNotNull(response)
            assertTrue(response!!.contains("✓ Clave de Gemini guardada correctamente. ¡Motor de IA activo!"))
            assertEquals("AIzaSyCustomKey123", AgentSettingsManager.getInstance().getGeminiApiKey())

            val keyFile = File(tempDir, ".gemini/api_key")
            assertTrue(keyFile.exists())
            assertEquals("AIzaSyCustomKey123", keyFile.readText().trim())

            val infoResponse = handleKeyCommand("/key", tempDir)
            assertNotNull(infoResponse)
            assertTrue(infoResponse!!.contains("● Clave de Gemini configurada:"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testFormatModelListMessage() {
        AntigravityModelCatalog.selectModelById("claude-sonnet-4-6")
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
        val successMsg = handleModelSelectionCommand("claude-opus-4-6-thinking")
        assertTrue(successMsg.contains("✓ Modelo cambiado a: Claude Opus 4.6 (Thinking)"))
        assertEquals("claude-opus-4-6-thinking", AntigravityModelCatalog.selectedModel.value.id)

        val flexibleMsg = handleModelSelectionCommand("claude-sonnet")
        assertTrue(flexibleMsg.contains("✓ Modelo cambiado a: Claude Sonnet 4.6 (Thinking)"))
        assertEquals("claude-sonnet-4-6", AntigravityModelCatalog.selectedModel.value.id)

        val failMsg = handleModelSelectionCommand("invalid-model-name")
        assertTrue(failMsg.contains("✖ Modelo no encontrado: 'invalid-model-name'. Escribe /model para ver la lista."))
        assertEquals("claude-sonnet-4-6", AntigravityModelCatalog.selectedModel.value.id)
    }
}
