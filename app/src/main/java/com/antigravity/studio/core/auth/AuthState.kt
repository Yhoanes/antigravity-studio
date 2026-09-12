package com.antigravity.studio.core.auth

/**
 * State representing Google OAuth authentication lifecycle as defined in SPEC-002 Section 4.1.
 */
sealed interface AuthState {
    data object Unauthenticated : AuthState
    data object Authenticating : AuthState
    data class Authenticated(
        val email: String,
        val displayName: String = "",
        val pictureUrl: String? = null
    ) : AuthState
    data class Error(val message: String, val cause: Throwable? = null) : AuthState
}

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val scope: String,
    val accountId: String = ""
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= (expiresAtEpochMs - 300_000)
}
