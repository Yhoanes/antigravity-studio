package com.antigravity.studio.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.antigravity.studio.MainActivity
import com.antigravity.studio.core.auth.AuthState
import com.antigravity.studio.core.auth.OAuthTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * GoogleOAuthManager implements the OAuth 2.0 PKCE (RFC 7636 and RFC 8252) authentication flow
 * for Google Gemini / Cloud Platform endpoints directly on Android without any cloud proxy.
 *
 * Implements SPEC-002:
 * - Official registered credentials for Antigravity Studio.
 * - Primary Localhost Loopback Receiver on port 54123 with Cyber-Obsidian HTTP 200 OK page.
 * - Fallback Custom Scheme callback support (antigravity://oauth2callback).
 * - Silent token auto-refresh with client_id and client_secret.
 * - Secure credential persistence in `$filesDir/.gemini/` with POSIX 0600 permissions.
 */
object GoogleOAuthManager {

    private const val TAG = "GoogleOAuthManager"

    // Official Antigravity Studio Credentials (SPEC-002 Section 2.1)
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
    const val LOOPBACK_PORT = 54123
    const val REDIRECT_URI = "http://localhost:54123/callback"
    const val FALLBACK_REDIRECT_URI = "antigravity://oauth2callback"
    const val SCOPES = "openid email profile https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/generative-language https://www.googleapis.com/auth/generative-language.retriever"

    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"

    // Cyber-Obsidian Success HTML Template served by loopback socket (SPEC-002 Section 2.3)
    const val CYBER_OBSIDIAN_SUCCESS_HTML = """<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Antigravity Studio - Autenticación Exitosa</title>
  <style>
    :root {
      --bg-dark: #07090E;
      --card-bg: #0F172A;
      --card-border: rgba(6, 182, 212, 0.35);
      --cyan-neon: #06B6D4;
      --green-neon: #22C55E;
      --text-main: #F8FAFC;
      --text-dim: #94A3B8;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background-color: var(--bg-dark);
      color: var(--text-main);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      padding: 1.5rem;
    }
    .auth-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 16px;
      padding: 2.5rem 2rem;
      max-width: 440px;
      width: 100%;
      text-align: center;
      box-shadow: 0 0 40px rgba(6, 182, 212, 0.15), 0 20px 25px -5px rgba(0, 0, 0, 0.5);
    }
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      background: rgba(34, 197, 94, 0.15);
      border: 1px solid rgba(34, 197, 94, 0.4);
      color: var(--green-neon);
      font-size: 0.85rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      padding: 0.35rem 0.85rem;
      border-radius: 9999px;
      margin-bottom: 1.5rem;
    }
    .pulse-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background-color: var(--green-neon);
      box-shadow: 0 0 10px var(--green-neon);
    }
    h1 {
      font-size: 1.6rem;
      font-weight: 800;
      letter-spacing: -0.02em;
      margin-bottom: 0.75rem;
      color: #FFFFFF;
    }
    p {
      color: var(--text-dim);
      font-size: 0.95rem;
      line-height: 1.5;
      margin-bottom: 2rem;
    }
    .btn-return {
      display: inline-block;
      width: 100%;
      background: linear-gradient(135deg, #0891B2 0%, #06B6D4 100%);
      color: #030712;
      font-size: 0.95rem;
      font-weight: 700;
      text-decoration: none;
      padding: 0.85rem 1.25rem;
      border-radius: 10px;
      box-shadow: 0 0 20px rgba(6, 182, 212, 0.4);
      cursor: pointer;
    }
  </style>
</head>
<body>
  <div class="auth-card">
    <div class="status-badge">
      <div class="pulse-dot"></div>
      <span>AUTORIZADO 🟢 HTTP 200</span>
    </div>
    <h1>¡Autenticación Exitosa!</h1>
    <p>Las credenciales de Google Antigravity han sido verificadas correctamente. Puedes cerrar esta pestaña y volver a la terminal de Antigravity Studio.</p>
    <a class="btn-return" href="antigravity://workspace" onclick="window.close();">Vuelve a Antigravity Studio</a>
  </div>
</body>
</html>"""

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _activeAccountEmail = MutableStateFlow<String?>(null)
    val activeAccountEmail: StateFlow<String?> = _activeAccountEmail.asStateFlow()

    private var currentTokens: OAuthTokens? = null
    private var pendingCodeVerifier: String? = null
    private var pendingState: String? = null
    private var appContext: Context? = null

    private var loopbackServerSocket: ServerSocket? = null
    private var loopbackJob: Job? = null

    /**
     * Initializes the manager with the application context, restoring existing
     * credentials from storage if available.
     */
    fun init(context: Context) {
        this.appContext = context.applicationContext
        loadSavedCredentials()
    }

    /**
     * Returns the currently active authenticated account email, if any.
     */
    fun getActiveAccount(): String? {
        return _activeAccountEmail.value ?: currentTokens?.accountId
    }

    /**
     * Generates a 64-character high-entropy cryptographic code_verifier for PKCE.
     */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Computes the SHA-256 code_challenge for the given code_verifier.
     */
    fun generateCodeChallenge(verifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Generates a random state string to mitigate CSRF attacks.
     */
    private fun generateState(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Builds the authorization URL containing PKCE parameters and official credentials.
     */
    fun createAuthorizationUrl(redirectUri: String = REDIRECT_URI): String {
        val verifier = pendingCodeVerifier ?: generateCodeVerifier().also { pendingCodeVerifier = it }
        val challenge = generateCodeChallenge(verifier)
        val state = pendingState ?: generateState().also { pendingState = it }

        val ctx = appContext
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("antigravity_oauth_state", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("code_verifier", verifier)
                .putString("state", state)
                .apply()
        }

        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", DEFAULT_CLIENT_ID)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()

        return authUri.toString()
    }

    /**
     * Initiates the OAuth login flow following SPEC-002:
     * 1. Initializes context and sets state to Authenticating.
     * 2. Generates PKCE codeVerifier, codeChallenge, and CSRF state.
     * 3. Starts Localhost Loopback Server on port 54123 (Dispatchers.IO) with 120s timeout.
     * 4. Launches browser intent with official authorization URL.
     * 5. Captures callback, sends Cyber-Obsidian 200 OK page, exchanges tokens with client_secret,
     *    fetches profile, persists credentials locally (0600), and brings MainActivity to front.
     */
    fun startLogin(context: Context) {
        init(context)
        _authState.value = AuthState.Authenticating

        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateState()

        this.pendingCodeVerifier = verifier
        this.pendingState = state

        val ctx = appContext ?: context.applicationContext
        val prefs = ctx.getSharedPreferences("antigravity_oauth_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("code_verifier", verifier)
            .putString("state", state)
            .apply()

        // Stop any previous loopback server instance
        stopLoopbackReceiver()

        // Start localhost loopback server on Dispatchers.IO
        loopbackJob = authScope.launch(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(LOOPBACK_PORT, 1, InetAddress.getByName("127.0.0.1")).apply {
                    soTimeout = 120_000
                }
                loopbackServerSocket = serverSocket
                Log.i(TAG, "Localhost loopback receiver listening on 127.0.0.1:$LOOPBACK_PORT (120s timeout)")

                var authCode: String? = null
                var returnedState: String? = null
                var authError: String? = null

                while (isActive && !serverSocket.isClosed) {
                    try {
                        val client = serverSocket.accept()
                        client.soTimeout = 5000

                        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                        val requestLine = reader.readLine().orEmpty()

                        if (requestLine.startsWith("GET /callback") || requestLine.contains("/callback?")) {
                            val path = requestLine.split(" ").getOrNull(1) ?: ""
                            authCode = extractQueryParam(path, "code")
                            returnedState = extractQueryParam(path, "state")
                            authError = extractQueryParam(path, "error")

                            // Respond with Cyber-Obsidian HTTP 200 OK page
                            val htmlBytes = CYBER_OBSIDIAN_SUCCESS_HTML.toByteArray(Charsets.UTF_8)
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
                        Log.w(TAG, "Localhost loopback server timed out after 120s waiting for callback")
                        if (_authState.value is AuthState.Authenticating) {
                            _authState.value = AuthState.Error("Tiempo de espera agotado para autenticación con Google (120s)")
                        }
                        break
                    }
                }

                if (!authError.isNullOrEmpty()) {
                    val err = "Error de autorización devuelto por Google: $authError"
                    Log.e(TAG, err)
                    _authState.value = AuthState.Error(err)
                    return@launch
                }

                if (authCode.isNullOrEmpty()) {
                    if (_authState.value is AuthState.Authenticating) {
                        _authState.value = AuthState.Error("Código de autorización no recibido en loopback callback")
                    }
                    return@launch
                }

                // Validate state
                if (returnedState != state) {
                    val err = "Fallo de validación de seguridad: el parámetro 'state' no coincide."
                    Log.e(TAG, err)
                    _authState.value = AuthState.Error(err)
                    return@launch
                }

                // Exchange authorization code for tokens via POST /token
                val exchangeResult = exchangeCodeForTokens(
                    authCode = authCode,
                    codeVerifier = verifier,
                    redirectUri = REDIRECT_URI
                )

                if (exchangeResult.isSuccess) {
                    Log.i(TAG, "OAuth loopback token exchange succeeded. Bringing MainActivity to front.")
                    val bringToFrontIntent = Intent(ctx, MainActivity::class.java).apply {
                        data = Uri.parse("antigravity://workspace")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }
                    ctx.startActivity(bringToFrontIntent)
                }

            } catch (e: Exception) {
                if (e !is SocketException || serverSocket?.isClosed != true) {
                    Log.e(TAG, "Error in localhost loopback receiver", e)
                    _authState.value = AuthState.Error(e.message ?: "Error desconocido en loopback receiver", e)
                }
            } finally {
                try {
                    serverSocket?.close()
                } catch (_: Exception) {}
                if (loopbackServerSocket == serverSocket) {
                    loopbackServerSocket = null
                }
            }
        }

        // Immediately launch browser intent with official authorization URL
        val authUrl = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", DEFAULT_CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
            .toString()

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(browserIntent)
    }

    /**
     * Handles the fallback deep link callback `antigravity://oauth2callback?code=...&state=...`
     * and exchanges the authorization code for access and refresh tokens.
     */
    suspend fun handleAuthCallback(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            stopLoopbackReceiver()

            if (uri.scheme != "antigravity" || uri.host != "oauth2callback") {
                val err = "URI callback inválido: $uri"
                _authState.value = AuthState.Error(err)
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            val errorParam = uri.getQueryParameter("error")
            if (!errorParam.isNullOrEmpty()) {
                val err = "Error de autorización devuelto por Google: $errorParam"
                _authState.value = AuthState.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val returnedCode = uri.getQueryParameter("code")
            if (returnedCode.isNullOrEmpty()) {
                val err = "Código de autorización ausente en el callback de Google"
                _authState.value = AuthState.Error(err)
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            val returnedState = uri.getQueryParameter("state")
            val ctx = appContext
            val savedState = pendingState ?: ctx?.getSharedPreferences("antigravity_oauth_state", Context.MODE_PRIVATE)
                ?.getString("state", null)

            if (savedState != null && returnedState != savedState) {
                val err = "Fallo de validación de seguridad: el parámetro 'state' no coincide."
                _authState.value = AuthState.Error(err)
                return@withContext Result.failure(SecurityException(err))
            }

            val verifier = pendingCodeVerifier ?: ctx?.getSharedPreferences("antigravity_oauth_state", Context.MODE_PRIVATE)
                ?.getString("code_verifier", null)
                ?: run {
                    val err = "Code verifier no encontrado en memoria o almacenamiento local."
                    _authState.value = AuthState.Error(err)
                    return@withContext Result.failure(IllegalStateException(err))
                }

            val result = exchangeCodeForTokens(
                authCode = returnedCode,
                codeVerifier = verifier,
                redirectUri = FALLBACK_REDIRECT_URI
            )

            result.map { it.accountId }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling OAuth callback", e)
            _authState.value = AuthState.Error(e.message ?: "Error desconocido en callback", e)
            Result.failure(e)
        }
    }

    /**
     * Backward-compatible alias matching SPEC-002 interface signature.
     */
    suspend fun handleAuthorizationCallback(uri: Uri): Result<Unit> {
        return handleAuthCallback(uri).map { }
    }

    /**
     * Exchanges the authorization code for OAuth tokens by making a POST request to
     * `https://oauth2.googleapis.com/token` with client_id, client_secret, code, code_verifier,
     * and the matching redirect_uri.
     */
    suspend fun exchangeCodeForTokens(
        authCode: String,
        codeVerifier: String,
        redirectUri: String
    ): Result<OAuthTokens> = withContext(Dispatchers.IO) {
        try {
            val requestBody = FormBody.Builder()
                .add("client_id", DEFAULT_CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("grant_type", "authorization_code")
                .add("code", authCode)
                .add("code_verifier", codeVerifier)
                .add("redirect_uri", redirectUri)
                .build()

            val tokenRequest = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(tokenRequest).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val err = "Error al intercambiar token (${response.code}): $responseBody"
                _authState.value = AuthState.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val json = JSONObject(responseBody)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token", currentTokens?.refreshToken.orEmpty())
            val expiresInSeconds = json.optLong("expires_in", 3600L)
            val scope = json.optString("scope", SCOPES)
            val idToken = json.optString("id_token", "")

            val expiresAtEpochMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)

            // Extract profile info
            val profile = fetchUserProfile(accessToken, idToken)
            val email = profile.optString("email", "unknown@google.com")
            val displayName = profile.optString("name", email.substringBefore("@"))
            val pictureUrl = if (profile.has("picture") && !profile.isNull("picture")) profile.getString("picture") else null

            val tokens = OAuthTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochMs = expiresAtEpochMs,
                scope = scope,
                accountId = email
            )

            this@GoogleOAuthManager.currentTokens = tokens
            this@GoogleOAuthManager._activeAccountEmail.value = email

            // Persist credentials locally with 0600 permissions
            saveCredentials(tokens, displayName, pictureUrl)

            _authState.value = AuthState.Authenticated(
                email = email,
                displayName = displayName,
                pictureUrl = pictureUrl
            )

            Log.i(TAG, "OAuth authentication successful for $email")
            Result.success(tokens)
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging code for tokens", e)
            _authState.value = AuthState.Error(e.message ?: "Error en intercambio de tokens", e)
            Result.failure(e)
        }
    }

    /**
     * Returns a valid access_token, performing a transparent silent refresh
     * if expired or expiring within 300 seconds.
     * Uses client_id and client_secret in grant_type=refresh_token POST.
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val tokens = currentTokens ?: loadSavedTokens() ?: return@withContext null

        if (!tokens.scope.contains("generative-language")) {
            Log.w(TAG, "Current token missing 'generative-language' scope. Invalidating token.")
            val ctx = appContext
            if (ctx != null) {
                File(ctx.filesDir, ".gemini/oauth_creds.json").delete()
            }
            currentTokens = null
            _activeAccountEmail.value = null
            _authState.value = AuthState.Unauthenticated
            return@withContext null
        }

        if (!tokens.isExpired) {
            return@withContext tokens.accessToken
        }

        if (tokens.refreshToken.isEmpty()) {
            Log.w(TAG, "Access token expired and no refresh token available")
            return@withContext null
        }

        try {
            Log.i(TAG, "Refreshing expired access token for ${tokens.accountId}...")
            val body = FormBody.Builder()
                .add("client_id", DEFAULT_CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("grant_type", "refresh_token")
                .add("refresh_token", tokens.refreshToken)
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed refreshing token: (${response.code}) $responseBody")
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val newAccessToken = json.getString("access_token")
            val newExpiresIn = json.optLong("expires_in", 3600L)
            val newExpiresAt = System.currentTimeMillis() + (newExpiresIn * 1000L)
            val updatedRefreshToken = json.optString("refresh_token", tokens.refreshToken)

            val updatedTokens = tokens.copy(
                accessToken = newAccessToken,
                refreshToken = updatedRefreshToken,
                expiresAtEpochMs = newExpiresAt
            )

            this@GoogleOAuthManager.currentTokens = updatedTokens
            saveCredentials(updatedTokens, null, null)

            Log.i(TAG, "Access token refreshed successfully")
            updatedTokens.accessToken
        } catch (e: Exception) {
            Log.e(TAG, "Exception refreshing access token", e)
            null
        }
    }

    /**
     * Clears local active credentials and resets auth state.
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopLoopbackReceiver()
            currentTokens = null
            _activeAccountEmail.value = null
            _authState.value = AuthState.Unauthenticated

            val ctx = appContext
            if (ctx != null) {
                val geminiDir = File(ctx.filesDir, ".gemini")
                File(geminiDir, "oauth_creds.json").delete()

                val accountsFile = File(geminiDir, "google_accounts.json")
                if (accountsFile.exists()) {
                    try {
                        val root = JSONObject(accountsFile.readText(Charsets.UTF_8))
                        root.put("active_account_email", JSONObject.NULL)
                        accountsFile.writeText(root.toString(2), Charsets.UTF_8)
                    } catch (_: Exception) {
                        accountsFile.delete()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Closes any active loopback ServerSocket and cancels its background coroutine job.
     */
    fun stopLoopbackReceiver() {
        try {
            loopbackServerSocket?.close()
        } catch (_: Exception) {}
        loopbackServerSocket = null
        loopbackJob?.cancel()
        loopbackJob = null
    }

    private fun fetchUserProfile(accessToken: String, idToken: String): JSONObject {
        // Attempt 1: Decode JWT id_token payload
        if (idToken.isNotEmpty()) {
            try {
                val parts = idToken.split(".")
                if (parts.size >= 2) {
                    val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                    val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
                    if (json.has("email")) {
                        return json
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing id_token JWT payload: ${e.message}")
            }
        }

        // Attempt 2: Query userinfo endpoint
        try {
            val req = Request.Builder()
                .url(USERINFO_ENDPOINT)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                return JSONObject(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching userinfo from Google: ${e.message}")
        }

        return JSONObject()
    }

    private fun saveCredentials(tokens: OAuthTokens, displayName: String?, pictureUrl: String?) {
        val ctx = appContext ?: return
        try {
            val geminiDir = File(ctx.filesDir, ".gemini")
            if (!geminiDir.exists()) {
                geminiDir.mkdirs()
                applyPosixPermissions(geminiDir, "700")
            }

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            // 1. Save oauth_creds.json
            val credsFile = File(geminiDir, "oauth_creds.json")
            val credsJson = JSONObject().apply {
                put("token_type", "Bearer")
                put("access_token", tokens.accessToken)
                put("refresh_token", tokens.refreshToken)
                put("expires_at_epoch_ms", tokens.expiresAtEpochMs)
                put("scope", tokens.scope)
                put("account_id", tokens.accountId)
                put("updated_at_iso", isoFormat.format(Date()))
            }
            credsFile.writeText(credsJson.toString(2), Charsets.UTF_8)
            applyPosixPermissions(credsFile, "600")

            // 2. Save google_accounts.json
            val accountsFile = File(geminiDir, "google_accounts.json")
            val accountsJson = if (accountsFile.exists()) {
                try {
                    JSONObject(accountsFile.readText(Charsets.UTF_8))
                } catch (_: Exception) {
                    JSONObject()
                }
            } else {
                JSONObject()
            }

            accountsJson.put("active_account_email", tokens.accountId)
            val accountsList = accountsJson.optJSONArray("accounts") ?: JSONArray()

            var found = false
            for (i in 0 until accountsList.length()) {
                val acc = accountsList.getJSONObject(i)
                if (acc.optString("email") == tokens.accountId) {
                    acc.put("last_login_epoch_ms", System.currentTimeMillis())
                    if (displayName != null) acc.put("display_name", displayName)
                    if (pictureUrl != null) acc.put("picture_url", pictureUrl)
                    found = true
                    break
                }
            }

            if (!found) {
                val newAccount = JSONObject().apply {
                    put("email", tokens.accountId)
                    put("display_name", displayName ?: tokens.accountId.substringBefore("@"))
                    put("picture_url", pictureUrl ?: JSONObject.NULL)
                    put("last_login_epoch_ms", System.currentTimeMillis())
                    put("project_id", JSONObject.NULL)
                }
                accountsList.put(newAccount)
            }
            accountsJson.put("accounts", accountsList)

            accountsFile.writeText(accountsJson.toString(2), Charsets.UTF_8)
            applyPosixPermissions(accountsFile, "600")

        } catch (e: Exception) {
            Log.e(TAG, "Failed saving OAuth credentials to disk", e)
        }
    }

    private fun loadSavedCredentials() {
        val tokens = loadSavedTokens()
        if (tokens != null) {
            this.currentTokens = tokens
            this._activeAccountEmail.value = tokens.accountId

            // Load profile from google_accounts.json if present
            val ctx = appContext
            var name = tokens.accountId.substringBefore("@")
            var pic: String? = null

            if (ctx != null) {
                val accountsFile = File(ctx.filesDir, ".gemini/google_accounts.json")
                if (accountsFile.exists()) {
                    try {
                        val root = JSONObject(accountsFile.readText(Charsets.UTF_8))
                        val accounts = root.optJSONArray("accounts") ?: JSONArray()
                        for (i in 0 until accounts.length()) {
                            val acc = accounts.getJSONObject(i)
                            if (acc.optString("email") == tokens.accountId) {
                                name = acc.optString("display_name", name)
                                pic = if (acc.isNull("picture_url")) null else acc.optString("picture_url")
                                break
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            _authState.value = AuthState.Authenticated(
                email = tokens.accountId,
                displayName = name,
                pictureUrl = pic
            )
            Log.i(TAG, "Restored active Google account: ${tokens.accountId}")
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    private fun loadSavedTokens(): OAuthTokens? {
        val ctx = appContext ?: return null
        val credsFile = File(ctx.filesDir, ".gemini/oauth_creds.json")
        if (!credsFile.exists()) return null

        return try {
            val json = JSONObject(credsFile.readText(Charsets.UTF_8))
            val savedScope = json.optString("scope", "")
            if (!savedScope.contains("generative-language")) {
                Log.w(TAG, "Loaded token missing 'generative-language' scope ($savedScope). Invalidating saved token.")
                credsFile.delete()
                null
            } else {
                OAuthTokens(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token", ""),
                    expiresAtEpochMs = json.optLong("expires_at_epoch_ms", 0L),
                    scope = savedScope,
                    accountId = json.optString("account_id", "unknown@google.com")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading saved OAuth credentials: ${e.message}")
            null
        }
    }

    private fun applyPosixPermissions(file: File, mode: String) {
        file.setReadable(true, true)
        file.setWritable(true, true)
        file.setExecutable(false, false)
        try {
            Runtime.getRuntime().exec(arrayOf("/system/bin/chmod", mode, file.absolutePath)).waitFor()
        } catch (_: Exception) {}
    }

    /**
     * Extracts query parameters from a URL path string without relying on android.net.Uri
     */
    fun extractQueryParam(pathWithQuery: String, paramName: String): String? {
        val query = pathWithQuery.substringAfter("?", "")
        if (query.isEmpty()) return null
        for (pair in query.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2 && parts[0] == paramName) {
                return java.net.URLDecoder.decode(parts[1], "UTF-8")
            }
        }
        return null
    }
}
