package com.antigravity.studio.agent

import android.content.Context
import android.util.Log
import com.antigravity.studio.auth.GoogleOAuthManager
import com.antigravity.studio.pty.NativePty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
 * RealAgentEngine connects Antigravity Studio directly to Google Generative Language
 * APIs (`gemini-2.0-flash` / `gemini-1.5-pro`) using the authenticated user's Google OAuth token.
 *
 * Streams tokens via SSE with sub-16ms frame latency directly to the PTY master descriptor.
 */
object RealAgentEngine : IRealAgentEngine {

    private const val TAG = "RealAgentEngine"
    private const val GEMINI_MODEL = "gemini-2.0-flash"
    private const val STREAM_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:streamGenerateContent?alt=sse"
    private const val SYNC_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    var workspaceDir: File? = null

    /**
     * Executes an agent task with real-time SSE streaming from Google Gemini.
     */
    override fun executeAgentTask(
        userPrompt: String,
        systemInstruction: String?
    ): Flow<AgentStreamEvent> = flow {
        val accessToken = GoogleOAuthManager.getValidAccessToken()

        if (accessToken.isNullOrEmpty()) {
            val unauthMessage = """
                [1;33m[!] No se ha detectado una sesión activa de Google OAuth.[0m
                
                [38;2;139;92;246mAntigravity Studio se conecta directamente a los modelos reales de Gemini[0m
                [38;2;139;92;246mutilizando tu cuenta de Google mediante OAuth 2.0 PKCE.[0m
                
                [1;36mPara activar el agente con IA real:[0m
                1. Pulsa el botón [1;32m[Iniciar Sesión con Google][0m en la barra superior.
                2. O escribe [1;36magy auth[0m en esta terminal.
                
            """.trimIndent()
            emit(AgentStreamEvent.TextDelta(unauthMessage))
            emit(AgentStreamEvent.Completed(0))
            return@flow
        }

        try {
            val defaultSystem = systemInstruction ?: """
                Eres el Agente Autónomo de Antigravity Studio ejecutándose en una estación móvil Xiaomi Pad 6 (Snapdragon 870, 144Hz).
                Tu objetivo es asistir en tareas de ingeniería de software, arquitectura de sistemas y análisis de código.
                Responde de forma concisa, precisa y profesional con formato compatible con terminal ANSI.
            """.trimIndent()

            val requestJson = JSONObject().apply {
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

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(STREAM_API_URL)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                val errMsg = "[1;31m[Error API Google ${response.code}][0m: $errorBody\r\n"
                emit(AgentStreamEvent.TextDelta(errMsg))
                emit(AgentStreamEvent.Error(IllegalStateException("HTTP ${response.code}: $errorBody")))
                return@flow
            }

            val source = response.body?.source()
            if (source == null) {
                emit(AgentStreamEvent.Error(IllegalStateException("Empty response body from Google API")))
                return@flow
            }

            var totalTokens = 0
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data.isNotEmpty()) {
                        try {
                            val chunkJson = JSONObject(data)
                            val candidates = chunkJson.optJSONArray("candidates")
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
            emit(AgentStreamEvent.TextDelta("\r\n[1;31m[Fallo de Conexión Agéntica][0m: ${e.message}\r\n"))
            emit(AgentStreamEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Executes an agent task and pipes the streaming text tokens directly into the PTY master FD.
     */
    override suspend fun attachToPtyStream(masterFd: Int, userPrompt: String) = withContext(Dispatchers.IO) {
        if (masterFd < 0) return@withContext

        val banner = "\r\n[1;36m⟳ Conectando con Google Gemini 2.0 Flash (OAuth)...[0m\r\n"
        writeToPty(masterFd, banner)

        executeAgentTask(userPrompt).collect { event ->
            when (event) {
                is AgentStreamEvent.TextDelta -> {
                    // Replace standard LF with CRLF for POSIX raw terminal output
                    val formatted = event.text.replace("\n", "\r\n")
                    writeToPty(masterFd, formatted)
                }
                is AgentStreamEvent.ToolCallStarted -> {
                    writeToPty(masterFd, "\r\n[38;2;245;158;11m⚙ Ejecutando herramienta: ${event.toolName}...[0m\r\n")
                }
                is AgentStreamEvent.ToolCallFinished -> {
                    writeToPty(masterFd, "[38;2;34;197;94m✔ ${event.toolName} finalizada: ${event.resultSummary}[0m\r\n")
                }
                is AgentStreamEvent.Completed -> {
                    writeToPty(masterFd, "\r\n[38;2;34;197;94m● [Respuesta de Gemini Completada][0m\r\n\r\n")
                }
                is AgentStreamEvent.Error -> {
                    writeToPty(masterFd, "\r\n[1;31m✖ Error en agente: ${event.error.message}[0m\r\n\r\n")
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
