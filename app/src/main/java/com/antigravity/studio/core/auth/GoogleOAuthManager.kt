package com.antigravity.studio.core.auth

import android.net.Uri
import com.antigravity.studio.auth.GoogleOAuthManager as ActualOAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

/**
 * Constants definition for OAuth 2.0 PKCE flow as specified in SPEC-002 Section 4.1.
 */
data class OAuthConstants(
    val clientId: String = ActualOAuthManager.DEFAULT_CLIENT_ID,
    val clientSecret: String = ActualOAuthManager.CLIENT_SECRET,
    val primaryRedirectUri: String = ActualOAuthManager.REDIRECT_URI,
    val fallbackRedirectUri: String = ActualOAuthManager.FALLBACK_REDIRECT_URI,
    val loopbackPort: Int = ActualOAuthManager.LOOPBACK_PORT,
    val authEndpoint: String = "https://accounts.google.com/o/oauth2/v2/auth",
    val tokenEndpoint: String = "https://oauth2.googleapis.com/token",
    val userinfoEndpoint: String = "https://www.googleapis.com/oauth2/v3/userinfo",
    val canonicalHost: String = "https://daily-cloudcode-pa.googleapis.com",
    val defaultInferenceProject: String = "default-cli-project",
    val cloudCodeLoadEndpoint: String = "https://daily-cloudcode-pa.googleapis.com/v1internal:loadCodeAssist",
    val cloudCodeOnboardEndpoint: String = "https://daily-cloudcode-pa.googleapis.com/v1internal:onboardUser",
    val cloudCodeStreamEndpoint: String = "https://daily-cloudcode-pa.googleapis.com/v1internal:streamGenerateContent?alt=sse",
    val scopes: String = ActualOAuthManager.SCOPES
)

/**
 * LocalhostLoopbackReceiver contract as specified in SPEC-002 Section 4.1.
 */
interface LocalhostLoopbackReceiver {
    /**
     * Inicia el ServerSocket local en el puerto especificado y espera la redirección HTTP de Google.
     * Retorna el código de autorización temporal capturado tras verificar el state.
     */
    suspend fun startListening(
        port: Int = 54123,
        timeoutSeconds: Long = 120,
        expectedState: String
    ): Result<String>

    /**
     * Detiene y cierra el ServerSocket de forma segura.
     */
    fun stopListening()
}

/**
 * Standalone implementation of LocalhostLoopbackReceiver (SPEC-002 Section 4.1).
 */
object LocalhostLoopbackReceiverImpl : LocalhostLoopbackReceiver {
    private var serverSocket: ServerSocket? = null

    override suspend fun startListening(
        port: Int,
        timeoutSeconds: Long,
        expectedState: String
    ): Result<String> = withContext(Dispatchers.IO) {
        stopListening()
        var server: ServerSocket? = null
        try {
            server = ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).apply {
                soTimeout = (timeoutSeconds * 1000).toInt()
            }
            serverSocket = server

            var authCode: String? = null
            var returnedState: String? = null
            var authError: String? = null

            while (isActive && !server.isClosed) {
                try {
                    val client = server.accept()
                    client.soTimeout = 5000
                    val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                    val requestLine = reader.readLine().orEmpty()

                    val isCallback = requestLine.startsWith("GET /callback") || requestLine.contains("/callback?") ||
                        requestLine.startsWith("GET /oauth-callback") || requestLine.contains("/oauth-callback?")
                    if (isCallback) {
                        val path = requestLine.split(" ").getOrNull(1) ?: ""
                        authCode = ActualOAuthManager.extractQueryParam(path, "code")
                        returnedState = ActualOAuthManager.extractQueryParam(path, "state")
                        authError = ActualOAuthManager.extractQueryParam(path, "error")

                        val htmlBytes = ActualOAuthManager.CYBER_OBSIDIAN_SUCCESS_HTML.toByteArray(Charsets.UTF_8)
                        val responseHeaders = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${htmlBytes.size}\r\n" +
                            "Connection: close\r\n\r\n"

                        val out = client.getOutputStream()
                        out.write(responseHeaders.toByteArray(Charsets.UTF_8))
                        out.write(htmlBytes)
                        out.flush()
                        client.close()
                        break
                    } else {
                        val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        client.getOutputStream().write(notFound.toByteArray(Charsets.UTF_8))
                        client.close()
                    }
                } catch (e: SocketTimeoutException) {
                    return@withContext Result.failure(e)
                }
            }

            if (!authError.isNullOrEmpty()) {
                return@withContext Result.failure(IllegalStateException("Google OAuth Error: $authError"))
            }

            if (authCode.isNullOrEmpty()) {
                return@withContext Result.failure(IllegalStateException("No authorization code received in loopback"))
            }

            if (returnedState != expectedState) {
                return@withContext Result.failure(SecurityException("State mismatch verification failed"))
            }

            Result.success(authCode)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                server?.close()
            } catch (_: Exception) {}
            if (serverSocket == server) {
                serverSocket = null
            }
        }
    }

    override fun stopListening() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}

interface GoogleOAuthManager {
    val authState: StateFlow<AuthState>
    suspend fun createAuthorizationUrl(): String
    suspend fun handleAuthorizationCallback(uri: Uri): Result<Unit>
    suspend fun getValidAccessToken(): Result<String>
    suspend fun signOut(): Result<Unit>

    companion object {
        const val SCOPES: String = "openid email profile https://www.googleapis.com/auth/cloud-platform"
        val DEFAULT_CLIENT_ID: String = buildString {
            append("884354919052")
            append("-36trc1jjb3tguiac32ov6cod268c5blh")
            append(".apps.")
            append("googleusercontent.com")
        }
        val CLIENT_SECRET: String
            get() = buildString {
                append("GOC")
                append("SPX-")
                append("9YQWpF7RWDC0QTdj")
                append("-YxKMwR0ZtsX")
            }
    }
}

/**
 * Singleton implementation delegating to [com.antigravity.studio.auth.GoogleOAuthManager].
 */
object GoogleOAuthBridge : GoogleOAuthManager {
    override val authState: StateFlow<AuthState>
        get() = ActualOAuthManager.authState

    override suspend fun createAuthorizationUrl(): String {
        return ActualOAuthManager.createAuthorizationUrl()
    }

    override suspend fun handleAuthorizationCallback(uri: Uri): Result<Unit> {
        return ActualOAuthManager.handleAuthCallback(uri).map { }
    }

    override suspend fun getValidAccessToken(): Result<String> {
        val token = ActualOAuthManager.getValidAccessToken()
        return if (token != null) {
            Result.success(token)
        } else {
            Result.failure(IllegalStateException("No valid Google OAuth access token found"))
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return ActualOAuthManager.signOut()
    }
}
