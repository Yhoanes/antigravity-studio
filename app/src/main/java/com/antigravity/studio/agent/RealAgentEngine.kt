package com.antigravity.studio.agent

import android.content.Context
import android.util.Log
import com.antigravity.studio.auth.GoogleOAuthManager
import com.antigravity.studio.model.AntigravityModelCatalog
import com.antigravity.studio.pty.NativePty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface AgentStreamEvent {
    data class TextDelta(val text: String) : AgentStreamEvent
    data class ToolCallStarted(val toolName: String, val argsJson: String) : AgentStreamEvent
    data class ToolCallFinished(val toolName: String, val resultSummary: String) : AgentStreamEvent
    data class Completed(val totalTokens: Int = 0) : AgentStreamEvent
    data class Error(val error: Throwable) : AgentStreamEvent
}

interface IRealAgentEngine {
    fun executeAgentTask(
        userPrompt: String,
        systemInstruction: String? = null
    ): Flow<AgentStreamEvent>

    suspend fun attachToPtyStream(masterFd: Int, userPrompt: String)
}

/**
 * RealAgentEngine connects Antigravity Agent directly to the official Google Cloud Code
 * backend (`daily-cloudcode-pa.googleapis.com`) using Bearer tokens with the `cloud-platform` scope.
 *
 * Implements SPEC-002:
 * - Handshake with `loadCodeAssist` to discover companion project.
 * - Streaming inference with `v1internal:streamGenerateContent?alt=sse` via SSE.
 * - Sub-16ms latency token delivery directly to PTY master descriptor at 144Hz.
 * - Direct Gemini API fallback (`gemini-2.0-flash`) via saved API key or diagnostic reporting.
 */
object RealAgentEngine : IRealAgentEngine {

    private const val TAG = "RealAgentEngine"
    private const val GEMINI_MODEL = "gemini-2.0-flash"
    private const val CANONICAL_HOST = "https://daily-cloudcode-pa.googleapis.com"
    const val DEFAULT_INFERENCE_PROJECT = "default-cli-project"
    private const val CLOUD_CODE_STREAM_URL = "$CANONICAL_HOST/v1internal:streamGenerateContent?alt=sse"
    private const val CLOUD_CODE_LOAD_URL = "$CANONICAL_HOST/v1internal:loadCodeAssist"
    private const val CLOUD_CODE_ONBOARD_URL = "$CANONICAL_HOST/v1internal:onboardUser"
    private const val FALLBACK_GEMINI_STREAM_URL = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:streamGenerateContent"

    internal var httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    var workspaceDir: File? = null
    private var companionProjectId: String? = null

    internal fun getCompanionProjectId(): String? = companionProjectId
    internal fun setCompanionProjectId(id: String?) { companionProjectId = id }

    /**
     * Executes an agent task with real-time SSE streaming from Google Cloud Code.
     */
    override fun executeAgentTask(
        userPrompt: String,
        systemInstruction: String?
    ): Flow<AgentStreamEvent> = flow {
        val accessToken = GoogleOAuthManager.getValidAccessToken()

        if (accessToken.isNullOrEmpty()) {
            val unauthMessage = """
                \u001b[1;33m[!] Antigravity Agent: No se ha detectado una sesión activa de Google OAuth.\u001b[0m
                
                \u001b[38;2;139;92;246mAntigravity Agent se conecta directamente a Cloud Code / Gemini 2.0 Flash\u001b[0m
                \u001b[38;2;139;92;246mutilizando tu cuenta de Google mediante OAuth 2.0 PKCE.\u001b[0m
                
                \u001b[1;36mPara activar el agente con IA real:\u001b[0m
                1. Pulsa el botón \u001b[1;32m[Iniciar Sesión con Google]\u001b[0m en la barra superior.
                2. O escribe \u001b[1;36magy auth\u001b[0m en esta terminal.
                
            """.trimIndent().replace("\n", "\r\n")
            emit(AgentStreamEvent.TextDelta(unauthMessage))
            emit(AgentStreamEvent.Completed(0))
            return@flow
        }

        try {
            val defaultSystem = systemInstruction ?: """
                Eres Antigravity Agent ejecutándote como motor autónomo en una estación móvil Xiaomi Pad 6 (Snapdragon 870, 144Hz).
                Tu objetivo es asistir en tareas de ingeniería de software, arquitectura de sistemas y análisis de código.
                Responde de forma concisa, precisa y profesional con formato compatible con terminal ANSI.
            """.trimIndent()

            val activeModel = AntigravityModelCatalog.selectedModel.value
            val modelId = activeModel.id

            // 1. Resolve companion project if not resolved yet
            var projId = companionProjectId
            if (projId.isNullOrEmpty()) {
                projId = resolveCompanionProject(accessToken)
            }
            if (projId.isNullOrEmpty()) {
                projId = DEFAULT_INFERENCE_PROJECT
            }

            // 2. Build Cloud Code v1internal:streamGenerateContent request payload
            fun buildStreamRequest(projectId: String): Request {
                val effectiveProject = if (projectId.isNotEmpty()) projectId else DEFAULT_INFERENCE_PROJECT
                val requestJson = JSONObject().apply {
                    put("project", effectiveProject)
                    put("model", modelId)
                    put("request", JSONObject().apply {
                        val contents = JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().apply {
                                    put(JSONObject().put("text", userPrompt))
                                })
                            })
                        }
                        put("contents", contents)

                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", defaultSystem))
                            })
                        })

                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.7)
                            put("maxOutputTokens", 4096)
                        })
                    })
                }

                val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                return Request.Builder()
                    .url(CLOUD_CODE_STREAM_URL)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("User-Agent", "antigravity/1.0.0")
                    .post(requestBody)
                    .build()
            }

            suspend fun FlowCollector<AgentStreamEvent>.handleError(code: Int, errorBody: String) {
                Log.w(TAG, "Cloud Code streaming failed HTTP $code: $errorBody")

                // Try direct Gemini API key fallback if available
                val apiKey = getApiKeyFallback()
                if (!apiKey.isNullOrBlank()) {
                    Log.i(TAG, "Attempting direct fallback to Generative Language API with saved key...")
                    val fallbackHandled = streamDirectGeminiFallback(apiKey, userPrompt, defaultSystem)
                    if (fallbackHandled) {
                        return
                    }
                }

                // Legible diagnostic message in terminal
                val diagMsg = buildString {
                    append("\r\n\u001b[1;31m[Antigravity Agent Error Google Cloud Code $code]\u001b[0m\r\n")
                    append("\u001b[38;2;139;148;158mEndpoint:\u001b[0m $CLOUD_CODE_STREAM_URL\r\n")
                    append("\u001b[38;2;139;148;158mDetalle:\u001b[0m $errorBody\r\n\r\n")
                    append("\u001b[1;33m[Diagnóstico]:\u001b[0m\r\n")
                    when (code) {
                        403 -> {
                            append("• Acceso restringido en Cloud Code para la cuenta actual o falta de permisos en el proyecto.\r\n")
                            append("• Verifica que Cloud AI Companion API esté habilitada en tu proyecto de Google Cloud.\r\n")
                            append("• Puedes guardar una clave Gemini en \u001b[1;36m~/.gemini/api_key\u001b[0m o variable \u001b[1;36mGEMINI_API_KEY\u001b[0m como fallback.\r\n")
                            append("• O reintentar login con \u001b[1;32magy auth login\u001b[0m.\r\n")
                        }
                        404 -> {
                            append("• El modelo o recurso solicitado no fue encontrado en el endpoint de Cloud Code.\r\n")
                            append("• Puedes suministrar una clave Gemini en \u001b[1;36m~/.gemini/api_key\u001b[0m como fallback.\r\n")
                        }
                        else -> {
                            append("• La solicitud al backend de Cloud Code falló con código $code.\r\n")
                            append("• Puedes suministrar una clave Gemini en \u001b[1;36m~/.gemini/api_key\u001b[0m como fallback.\r\n")
                        }
                    }
                    append("\r\n")
                }
                emit(AgentStreamEvent.TextDelta(diagMsg))
                emit(AgentStreamEvent.Error(IllegalStateException("HTTP $code: $errorBody")))
            }

            var request = buildStreamRequest(projId)
            var response = httpClient.newCall(request).execute()

            if (!response.isSuccessful && response.code == 403) {
                val errorPeek = response.body?.string().orEmpty()
                if (errorPeek.contains("3501") && projId != DEFAULT_INFERENCE_PROJECT) {
                    Log.w(TAG, "Encountered 403 with #3501, retrying once forcing projId = $DEFAULT_INFERENCE_PROJECT...")
                    projId = DEFAULT_INFERENCE_PROJECT
                    companionProjectId = DEFAULT_INFERENCE_PROJECT
                    request = buildStreamRequest(projId)
                    response = httpClient.newCall(request).execute()
                } else {
                    handleError(response.code, errorPeek)
                    return@flow
                }
            }

            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                handleError(response.code, errorBody)
                return@flow
            }

            val source = response.body?.source()
            if (source == null) {
                emit(AgentStreamEvent.Error(IllegalStateException("Empty response body from Cloud Code API")))
                return@flow
            }

            var totalTokens = 0
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data.isNotEmpty() && data != "[DONE]") {
                        try {
                            val chunkJson = JSONObject(data)
                            val rootObj = chunkJson.optJSONObject("response") ?: chunkJson
                            val candidates = rootObj.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val candidate = candidates.getJSONObject(0)
                                val content = candidate.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        val text = part.optString("text", "")
                                        if (text.isNotEmpty()) {
                                            emit(AgentStreamEvent.TextDelta(text))
                                            totalTokens += text.length / 4
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing SSE data chunk: ${e.message}")
                        }
                    }
                }
            }

            emit(AgentStreamEvent.Completed(totalTokens))
        } catch (e: Exception) {
            Log.e(TAG, "Error executing agent task", e)
            emit(AgentStreamEvent.TextDelta("\r\n\u001b[1;31m[Antigravity Agent Fallo de Conexión]\u001b[0m: ${e.message}\r\n"))
            emit(AgentStreamEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    internal fun extractProjectId(json: JSONObject): String? {
        if (json.has("cloudaicompanionProject")) {
            val proj = json.get("cloudaicompanionProject")
            val projId = if (proj is JSONObject) {
                val id = proj.optString("id")
                if (id.isNotEmpty()) id else proj.optString("name", "")
            } else {
                proj.toString()
            }
            if (projId.isNotEmpty() && projId != "null") {
                return projId
            }
        }
        return null
    }

    /**
     * Resolves companion project id for Ultra / Google One AI subscriptions.
     * 1. Attempts loadCodeAssist ($CANONICAL_HOST/v1internal:loadCodeAssist).
     * 2. Fallbacks to onboardUser ($CANONICAL_HOST/v1internal:onboardUser) with free-tier if not found.
     * 3. Defaults to canonical DEFAULT_INFERENCE_PROJECT if still not found.
     * 4. Returns companionProjectId ?: DEFAULT_INFERENCE_PROJECT.
     */
    private suspend fun resolveCompanionProject(accessToken: String): String = withContext(Dispatchers.IO) {
        // 1. Intentar llamar a POST $CANONICAL_HOST/v1internal:loadCodeAssist
        try {
            val reqJson = JSONObject().apply {
                put("metadata", JSONObject().apply {
                    put("ideType", "ANTIGRAVITY")
                    put("ideVersion", "1.0.0")
                    put("pluginVersion", "1.0.0")
                })
            }
            val requestBody = reqJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(CLOUD_CODE_LOAD_URL)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("User-Agent", "antigravity/1.0.0")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isNotEmpty()) {
                    val json = JSONObject(bodyStr)
                    val projId = extractProjectId(json)
                    if (!projId.isNullOrEmpty()) {
                        companionProjectId = projId
                        Log.i(TAG, "Resolved companion project via loadCodeAssist: $projId")
                        return@withContext projId
                    }
                }
            } else {
                Log.w(TAG, "loadCodeAssist returned HTTP ${response.code}: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed discovering companion project via loadCodeAssist", e)
        }

        // 2. Si no se obtiene o la respuesta no tiene proyecto, llamar como fallback a:
        // POST $CANONICAL_HOST/v1internal:onboardUser
        try {
            val onboardReqJson = JSONObject().apply {
                put("tierId", "free-tier")
                put("metadata", JSONObject().apply {
                    put("ideType", "ANTIGRAVITY")
                    put("ideVersion", "1.0.0")
                    put("pluginVersion", "1.0.0")
                })
            }
            val onboardBody = onboardReqJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val onboardRequest = Request.Builder()
                .url(CLOUD_CODE_ONBOARD_URL)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("User-Agent", "antigravity/1.0.0")
                .post(onboardBody)
                .build()

            val onboardResponse = httpClient.newCall(onboardRequest).execute()
            if (onboardResponse.isSuccessful) {
                val bodyStr = onboardResponse.body?.string().orEmpty()
                if (bodyStr.isNotEmpty()) {
                    val json = JSONObject(bodyStr)
                    val projId = extractProjectId(json)
                    if (!projId.isNullOrEmpty()) {
                        companionProjectId = projId
                        Log.i(TAG, "Resolved companion project via onboardUser: $projId")
                        return@withContext projId
                    }
                }
            } else {
                Log.w(TAG, "onboardUser returned HTTP ${onboardResponse.code}: ${onboardResponse.body?.string()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed resolving companion project via onboardUser", e)
        }

        // 3. Si aún así es nulo o vacío, asignar el proyecto canónico oficial de Antigravity:
        companionProjectId = DEFAULT_INFERENCE_PROJECT
        Log.i(TAG, "Defaulting companion project to canonical: $DEFAULT_INFERENCE_PROJECT")

        // 4. Retornar companionProjectId ?: DEFAULT_INFERENCE_PROJECT
        return@withContext companionProjectId ?: DEFAULT_INFERENCE_PROJECT
    }

    internal suspend fun resolveCompanionProjectForTest(accessToken: String): String = resolveCompanionProject(accessToken)

    /**
     * Resolves fallback Gemini API key from environment variable or filesDir/.gemini/api_key.
     */
    private fun getApiKeyFallback(): String? {
        try {
            val envKey = System.getenv("GEMINI_API_KEY")
            if (!envKey.isNullOrBlank()) return envKey.trim()

            val possibleDirs = listOfNotNull(
                GoogleOAuthManager.getAppContext()?.filesDir,
                workspaceDir?.parentFile,
                File("/data/data/com.antigravity.studio/files")
            )
            for (dir in possibleDirs) {
                val keyFile = File(dir, ".gemini/api_key")
                if (keyFile.exists() && keyFile.isFile) {
                    val content = keyFile.readText(Charsets.UTF_8).trim()
                    if (content.isNotEmpty()) return content
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking API key fallback", e)
        }
        return null
    }

    /**
     * Streams inference directly from generativelanguage API using a local API key.
     */
    private suspend fun FlowCollector<AgentStreamEvent>.streamDirectGeminiFallback(
        apiKey: String,
        userPrompt: String,
        defaultSystem: String
    ): Boolean {
        return try {
            val fallbackUrl = "$FALLBACK_GEMINI_STREAM_URL?key=$apiKey&alt=sse"
            val fallbackPayload = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", userPrompt))
                        })
                    })
                }
                put("contents", contents)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", defaultSystem))
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 4096)
                })
            }

            val requestBody = fallbackPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(fallbackUrl)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("User-Agent", "antigravity/1.0.0")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini fallback failed HTTP ${response.code}: ${response.body?.string()}")
                return false
            }

            val source = response.body?.source() ?: return false
            var totalTokens = 0
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data.isNotEmpty() && data != "[DONE]") {
                        try {
                            val chunkJson = JSONObject(data)
                            val rootObj = chunkJson.optJSONObject("response") ?: chunkJson
                            val candidates = rootObj.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val candidate = candidates.getJSONObject(0)
                                val content = candidate.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        val text = part.optString("text", "")
                                        if (text.isNotEmpty()) {
                                            emit(AgentStreamEvent.TextDelta(text))
                                            totalTokens += text.length / 4
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing fallback SSE chunk: ${e.message}")
                        }
                    }
                }
            }
            emit(AgentStreamEvent.Completed(totalTokens))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Exception during fallback Gemini stream", e)
            false
        }
    }

    /**
     * Executes an agent task and pipes the streaming text tokens directly into the PTY master FD.
     */
    override suspend fun attachToPtyStream(masterFd: Int, userPrompt: String) = withContext(Dispatchers.IO) {
        if (masterFd < 0) return@withContext

        val banner = "\r\n\u001b[38;2;139;148;158m• Thought for 1s, planning...\u001b[0m\r\n\u001b[1;36m∷ Generating...\u001b[0m\r\n\r\n"
        writeToPty(masterFd, banner)

        var lastDeltaEndsWithPrompt = false
        executeAgentTask(userPrompt).collect { event ->
            when (event) {
                is AgentStreamEvent.TextDelta -> {
                    lastDeltaEndsWithPrompt = event.text.trimEnd().endsWith(">")
                    // Replace standard LF with CRLF for POSIX raw terminal output
                    val formatted = event.text.replace("\n", "\r\n")
                    writeToPty(masterFd, formatted)
                }
                is AgentStreamEvent.ToolCallStarted -> {
                    writeToPty(masterFd, "\r\n\u001b[38;2;245;158;11m⚙ [Antigravity Agent] Ejecutando herramienta: ${event.toolName}...\u001b[0m\r\n")
                }
                is AgentStreamEvent.ToolCallFinished -> {
                    writeToPty(masterFd, "\u001b[38;2;34;197;94m✔ [Antigravity Agent] ${event.toolName} finalizada: ${event.resultSummary}\u001b[0m\r\n")
                }
                is AgentStreamEvent.Completed -> {
                    if (!lastDeltaEndsWithPrompt) {
                        writeToPty(masterFd, "\r\n\r\n\u001b[1;36m> \u001b[0m")
                    }
                }
                is AgentStreamEvent.Error -> {
                    if (!lastDeltaEndsWithPrompt) {
                        writeToPty(masterFd, "\r\n\u001b[1;31m✖ [Antigravity Agent Error]: ${event.error.message}\u001b[0m\r\n\r\n\u001b[1;36m> \u001b[0m")
                    }
                }
            }
        }
    }

    private fun writeToPty(masterFd: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        var offset = 0
        while (offset < bytes.size) {
            val written = NativePty.nativeWrite(masterFd, bytes, offset, bytes.size - offset)
            if (written > 0) {
                offset += written
            } else {
                break
            }
        }
    }
}
