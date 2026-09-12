package com.antigravity.studio.core.auth

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

interface GoogleOAuthManager {
    val authState: StateFlow<AuthState>
    suspend fun createAuthorizationUrl(): String
    suspend fun handleAuthorizationCallback(uri: Uri): Result<Unit>
    suspend fun getValidAccessToken(): Result<String>
    suspend fun signOut(): Result<Unit>
}

/**
 * Singleton implementation delegating to [com.antigravity.studio.auth.GoogleOAuthManager].
 */
object GoogleOAuthBridge : GoogleOAuthManager {
    override val authState: StateFlow<AuthState>
        get() = com.antigravity.studio.auth.GoogleOAuthManager.authState

    override suspend fun createAuthorizationUrl(): String {
        return com.antigravity.studio.auth.GoogleOAuthManager.createAuthorizationUrl()
    }

    override suspend fun handleAuthorizationCallback(uri: Uri): Result<Unit> {
        return com.antigravity.studio.auth.GoogleOAuthManager.handleAuthCallback(uri).map { }
    }

    override suspend fun getValidAccessToken(): Result<String> {
        val token = com.antigravity.studio.auth.GoogleOAuthManager.getValidAccessToken()
        return if (token != null) {
            Result.success(token)
        } else {
            Result.failure(IllegalStateException("No valid Google OAuth access token found"))
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return com.antigravity.studio.auth.GoogleOAuthManager.signOut()
    }
}
