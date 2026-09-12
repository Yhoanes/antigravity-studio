package com.antigravity.studio.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.antigravity.studio.model.TerminalSession
import com.antigravity.studio.theme.CyberObsidian
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "TerminalSurface"

/**
 * JavaScript interface injected into WebView to receive input, resize, and diagnostics
 * from xterm.js WebGL/Canvas renderer.
 */
class PtyBridgeJsInterface(
    private val onDataReceived: (ByteArray) -> Unit,
    private val onResizeRequested: (cols: Int, rows: Int) -> Unit,
    private val onRendererReadyCallback: () -> Unit
) {
    @JavascriptInterface
    fun sendData(dataBase64: String) {
        try {
            val decoded = Base64.decode(dataBase64, Base64.NO_WRAP)
            onDataReceived(decoded)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding sendData Base64: ${e.message}", e)
        }
    }

    @JavascriptInterface
    fun onResize(cols: Int, rows: Int) {
        if (cols > 0 && rows > 0) {
            onResizeRequested(cols, rows)
        }
    }

    @JavascriptInterface
    fun onRendererReady() {
        Log.i(TAG, "xterm.js WebGL/Canvas renderer is ready")
        onRendererReadyCallback()
    }

    @JavascriptInterface
    fun logDebug(message: String) {
        Log.d("xterm.js", message)
    }
}

/**
 * High-performance TerminalSurface Composable hosting the accelerated WebView.
 */
@Composable
fun TerminalSurface(
    session: TerminalSession,
    modifier: Modifier = Modifier,
    onRendererReady: () -> Unit = {}
) {
    TerminalViewBridge(
        modifier = modifier,
        outputStream = session.outputFlow,
        onUserInput = { bytes -> session.writeInput(bytes) },
        onTerminalResized = { cols, rows -> session.resize(cols, rows) },
        onRendererReady = onRendererReady
    )
}

/**
 * Direct TerminalViewBridge implementation adhering strictly to SPEC-001 Section 4.3.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalViewBridge(
    modifier: Modifier = Modifier,
    outputStream: SharedFlow<ByteArray>,
    onUserInput: (ByteArray) -> Unit,
    onTerminalResized: (cols: Int, rows: Int) -> Unit,
    onRendererReady: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isRendererReady by remember { mutableStateOf(false) }

    // Stream PTY output directly into the WebView terminal buffer
    LaunchedEffect(webViewInstance, isRendererReady) {
        val wv = webViewInstance ?: return@LaunchedEffect
        if (!isRendererReady) return@LaunchedEffect

        outputStream.collect { chunk ->
            if (chunk.isNotEmpty()) {
                val base64Chunk = Base64.encodeToString(chunk, Base64.NO_WRAP)
                val js = "window.TerminalBridge && window.TerminalBridge.writeBinary('$base64Chunk');"
                wv.evaluateJavascript(js, null)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberObsidian)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                createConfiguredTerminalWebView(
                    context = context,
                    onUserInput = onUserInput,
                    onTerminalResized = onTerminalResized,
                    onReady = {
                        isRendererReady = true
                        onRendererReady()
                    }
                ).also { webViewInstance = it }
            },
            update = { wv ->
                webViewInstance = wv
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }
}

/**
 * Creates and configures the WebView for maximum GPU acceleration and minimum latency (< 16ms).
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredTerminalWebView(
    context: Context,
    onUserInput: (ByteArray) -> Unit,
    onTerminalResized: (cols: Int, rows: Int) -> Unit,
    onReady: () -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 1. Hardware Acceleration for Snapdragon 870 Adreno GPU (144Hz)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setBackgroundColor(Color.parseColor("#0B0F19"))
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false

        // 2. WebSettings optimization
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT

            // Touch gesture optimizations
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        // 3. Inject Bidirectional JavaScript Bridge
        val bridge = PtyBridgeJsInterface(
            onDataReceived = onUserInput,
            onResizeRequested = onTerminalResized,
            onRendererReadyCallback = onReady
        )
        addJavascriptInterface(bridge, "PtyBridgeNative")

        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Fit on page load complete
                view?.evaluateJavascript(
                    "window.TerminalBridge && window.TerminalBridge.fitTerminal();",
                    null
                )
            }
        }

        // Load standalone terminal bundle
        loadUrl("file:///android_asset/terminal/terminal.html")
    }
}
