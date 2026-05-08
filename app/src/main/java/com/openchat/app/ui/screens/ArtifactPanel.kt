package com.openchat.app.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.openchat.app.ui.theme.PrimaryTeal
import com.openchat.app.util.Artifact
import java.io.File
import java.io.FileOutputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArtifactPanel(
    artifact: Artifact,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isFullScreen by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val htmlContent = remember(artifact) {
        when (artifact.type) {
            "html" -> artifact.content
            "svg" -> """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; display:flex; justify-content:center; align-items:center; height:100vh;">
                    ${artifact.content}
                </body>
                </html>
            """.trimIndent()
            "react" -> """
                <!DOCTYPE html>
                <html>
                  <head>
                    <!-- Bundle React, ReactDOM, Babel, and Tailwind locally in production -->
                    <script crossorigin src="react.production.min.js"></script>
                    <script crossorigin src="react-dom.production.min.js"></script>
                    <script src="babel.min.js"></script>
                    <script src="tailwindcss.js"></script>
                  </head>
                  <body>
                    <div id="root"></div>
                    <script type="text/babel">
                      ${artifact.content}
                      
                      const root = ReactDOM.createRoot(document.getElementById('root'));
                      if (typeof App !== 'undefined') {
                          root.render(<App />);
                      } else if (typeof Default !== 'undefined') {
                          root.render(<Default />);
                      }
                    </script>
                  </body>
                </html>
            """.trimIndent()
            else -> artifact.content
        }
    }

    val modifier = if (isFullScreen) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxHeight().fillMaxWidth(0.85f)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                Text(
                    text = "Artifact: ${artifact.type.uppercase()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                IconButton(onClick = {
                    val file = File(context.cacheDir, "artifact.html")
                    FileOutputStream(file).use { it.write(htmlContent.toByteArray()) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/html"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Artifact"))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
                IconButton(onClick = { isFullScreen = !isFullScreen }) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Toggle Fullscreen")
                }
            }

            // Content
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccessFromFileURLs = true
                            settings.allowUniversalAccessFromFileURLs = true
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }
                            }
                            
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    return true
                                }
                            }

                            loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), color = PrimaryTeal)
                }
            }
        }
    }
}
