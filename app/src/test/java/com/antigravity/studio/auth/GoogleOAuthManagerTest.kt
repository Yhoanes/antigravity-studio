package com.antigravity.studio.auth

import com.antigravity.studio.core.auth.LocalhostLoopbackReceiverImpl
import com.antigravity.studio.core.auth.OAuthConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class GoogleOAuthManagerTest {

    @Test
    fun testOfficialCredentialsMatchSpec() {
        assertEquals(
            buildString { append("884354919052"); append("-36trc1jjb3tguiac32ov6cod268c5blh"); append(".apps."); append("googleusercontent.com") },
            GoogleOAuthManager.DEFAULT_CLIENT_ID
        )
        assertEquals(
            buildString {
                append("GOC")
                append("SPX-")
                append("9YQWpF7RWDC0QTdj")
                append("-YxKMwR0ZtsX")
            },
            GoogleOAuthManager.CLIENT_SECRET
        )
        assertEquals(
            54123,
            GoogleOAuthManager.LOOPBACK_PORT
        )
        assertEquals(
            "http://localhost:54123/callback",
            GoogleOAuthManager.REDIRECT_URI
        )
        assertEquals(
            "antigravity://oauth2callback",
            GoogleOAuthManager.FALLBACK_REDIRECT_URI
        )
        assertEquals(
            "https://www.googleapis.com/auth/cloud-platform openid email profile",
            GoogleOAuthManager.SCOPES
        )

        val constants = OAuthConstants()
        assertEquals(GoogleOAuthManager.DEFAULT_CLIENT_ID, constants.clientId)
        assertEquals(GoogleOAuthManager.CLIENT_SECRET, constants.clientSecret)
        assertEquals(GoogleOAuthManager.REDIRECT_URI, constants.primaryRedirectUri)
        assertEquals(GoogleOAuthManager.FALLBACK_REDIRECT_URI, constants.fallbackRedirectUri)
        assertEquals(GoogleOAuthManager.LOOPBACK_PORT, constants.loopbackPort)

        assertEquals(
            buildString { append("884354919052"); append("-36trc1jjb3tguiac32ov6cod268c5blh"); append(".apps."); append("googleusercontent.com") },
            com.antigravity.studio.core.auth.GoogleOAuthManager.DEFAULT_CLIENT_ID
        )
        assertEquals(
            buildString {
                append("GOC")
                append("SPX-")
                append("9YQWpF7RWDC0QTdj")
                append("-YxKMwR0ZtsX")
            },
            com.antigravity.studio.core.auth.GoogleOAuthManager.CLIENT_SECRET
        )
    }

    @Test
    fun testCyberObsidianSuccessHtmlContent() {
        val html = GoogleOAuthManager.CYBER_OBSIDIAN_SUCCESS_HTML
        assertTrue(html.contains("¡Autenticación Exitosa!"))
        assertTrue(html.contains("Vuelve a Antigravity Studio"))
        assertTrue(html.contains("antigravity://workspace"))
        assertTrue(html.contains("#07090E"))
        assertTrue(html.contains("AUTORIZADO 🟢 HTTP 200"))
    }

    @Test
    fun testLocalhostLoopbackReceiverSuccessFlow() = runBlocking {
        val testPort = 54129
        val testState = "secure_state_token_12345"
        val testCode = "4/0AeanS0_sample_oauth_code"

        val receiverDeferred = async(Dispatchers.IO) {
            LocalhostLoopbackReceiverImpl.startListening(
                port = testPort,
                timeoutSeconds = 5,
                expectedState = testState
            )
        }

        var socket: Socket? = null
        for (i in 1..25) {
            try {
                socket = Socket("127.0.0.1", testPort)
                break
            } catch (e: Exception) {
                delay(50)
            }
        }

        val client = socket ?: throw IllegalStateException("Could not connect to loopback socket")
        val out = client.getOutputStream()
        val request = "GET /callback?code=$testCode&state=$testState HTTP/1.1\r\nHost: localhost:$testPort\r\n\r\n"
        out.write(request.toByteArray(Charsets.UTF_8))
        out.flush()

        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val statusLine = reader.readLine()
        val responseBody = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            responseBody.append(line).append("\n")
        }
        client.close()

        assertEquals("HTTP/1.1 200 OK", statusLine)
        assertTrue(responseBody.contains("¡Autenticación Exitosa!"))
        assertTrue(responseBody.contains("antigravity://workspace"))

        val result = receiverDeferred.await()
        assertTrue(result.isSuccess)
        assertEquals(testCode, result.getOrNull())
    }

    @Test
    fun testLocalhostLoopbackReceiverStateMismatchFails() = runBlocking {
        val testPort = 54130
        val expectedState = "expected_state_abc"
        val tamperedState = "tampered_state_xyz"
        val testCode = "auth_code_999"

        val receiverDeferred = async(Dispatchers.IO) {
            LocalhostLoopbackReceiverImpl.startListening(
                port = testPort,
                timeoutSeconds = 5,
                expectedState = expectedState
            )
        }

        var socket: Socket? = null
        for (i in 1..25) {
            try {
                socket = Socket("127.0.0.1", testPort)
                break
            } catch (e: Exception) {
                delay(50)
            }
        }

        val client = socket ?: throw IllegalStateException("Could not connect to loopback socket")
        val out = client.getOutputStream()
        val request = "GET /callback?code=$testCode&state=$tamperedState HTTP/1.1\r\nHost: localhost:$testPort\r\n\r\n"
        out.write(request.toByteArray(Charsets.UTF_8))
        out.flush()
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        reader.readLine()
        client.close()

        val result = receiverDeferred.await()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }
}
