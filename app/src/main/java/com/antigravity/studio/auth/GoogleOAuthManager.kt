package com.antigravity.studio.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

import com.antigravity.studio.core.auth.AuthState
import com.antigravity.studio.core.auth.OAuthTokens

/**
 * GoogleOAuthManager implements the OAuth 2.0 PKCE (RFC 7636) authentication flow
 * for Google Gemini / Cloud Platform endpoints directly on Android without any cloud proxy.
 *
 * Persists credentials securely in `$filesDir/.gemini/` with 0600 file permissions.
 */
object GoogleOAuthManager {

    private const val TAG = "GoogleOAuthManager"
    const val DEFAULT_CLIENT_ID = "933725514589-lud4la20l7i5c35g1f77d33j8tffb66a.apps.googleusercontent.com"
    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
    const val REDIRECT_URI = "antigravity://oauth2callback"
    const val SCOPES = "https://www.googleapis.com/auth/cloud-platform openid email profile"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _activeAccountEmail = MutableStateFlow<String?>(null)
    val activeAccountEmail: StateFlow<String?> = _activeAccountEmail.asStateFlow()

    private var currentTokens: OAuthTokens? = null
    private var pendingCodeVerifier: String? = null
    private var pendingState: String? = null
    private var appContext: Context? = null

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
     * Builds the authorization URL containing PKCE parameters.
     */
    fun createAuthorizationUrl(context: Context? = appContext): String {
        val ctx = context ?: appContext
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateState()

        this.pendingCodeVerifier = verifier
        this.pendingState = state

        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("antigravity_oauth_state", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("code_verifier", verifier)
                .putString("state", state)
                .apply()
        }

        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
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

        return authUri.toString()
    }

    /**
     * Initiates the OAuth login flow by opening the Google sign-in consent screen in browser.
     */
    fun startLogin(context: Context) {
        init(context)
        _authState.value = AuthState.Authenticating
        val authUrl = createAuthorizationUrl(context)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Handles the deep link callback `antigravity://oauth2callback?code=...&state=...`
     * and exchanges the authorization code for access and refresh tokens.
     */
    suspend fun handleAuthCallback(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
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

            // Exchange authorization code for tokens via POST /token
            val requestBody = FormBody.Builder()
                .add("client_id", DEFAULT_CLIENT_ID)
                .add("grant_type", "authorization_code")
                .add("code", returnedCode)
                .add("code_verifier", verifier)
                .add("redirect_uri", REDIRECT_URI)
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
            Result.success(email)
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
     * Returns a valid access_token, performing a transparent silent refresh
     * if expired or expiring within 300 seconds.
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val tokens = currentTokens ?: loadSavedTokens() ?: return@withContext null

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
            OAuthTokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token", ""),
                expiresAtEpochMs = json.optLong("expires_at_epoch_ms", 0L),
                scope = json.optString("scope", SCOPES),
                accountId = json.optString("account_id", "unknown@google.com")
            )
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
}
