package com.openchat.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.openchat.app.data.model.WorkspaceFile
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(
    file: WorkspaceFile,
    onClose: () -> Unit,
    onSave: (String) -> Unit,
    onUndo: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    // JS Interface to receive content back from WebView
    val jsInterface = remember {
        object {
            @JavascriptInterface
            fun receiveContent(content: String) {
                onSave(content)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Saved ✓", duration = SnackbarDuration.Short)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.fileName, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onUndo) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = {
                        webViewRef?.evaluateJavascript("editor.getValue()", { result ->
                            // result comes back wrapped in quotes, easier to use JS interface
                            webViewRef?.evaluateJavascript("Android.receiveContent(editor.getValue());", null)
                        })
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Initialize editor with content
                            // Escape content properly for JS
                            val escapedContent = file.content
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "")
                            
                            val js = "initEditor(\"$escapedContent\", \"${file.fileType}\");"
                            evaluateJavascript(js, null)
                        }
                    }
                    addJavascriptInterface(jsInterface, "Android")
                    loadUrl("file:///android_asset/editor.html")
                    webViewRef = this
                }
            },
            update = { view ->
                // Do not reload content on every recomposition to prevent losing unsaved changes.
                // Recompose logic should only happen if file ID changes, handled by upper layers.
            }
        )
    }
}
