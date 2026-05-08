package com.openchat.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeBlockView(
    language: String,
    code: String,
    onSaveToWorkspace: (String, String) -> Unit
) {
    var output by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val webView = remember(context) { WebView(context) }
    var errorOutput by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        webView.settings.javaScriptEnabled = true
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null && isRunning) {
                    val msg = consoleMessage.message()
                    output = (output ?: "") + msg + "\n"
                    if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        errorOutput = true
                    }
                }
                return true
            }
        }
        onDispose { webView.destroy() }
    }

    var isEditing by remember { mutableStateOf(false) }
    var editableCode by remember { mutableStateOf(code) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = language.ifBlank { "code" },
                color = Color.LightGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    onClick = { isEditing = !isEditing },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(if (isEditing) Icons.Default.Check else Icons.Default.Edit, contentDescription = "Edit", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(editableCode)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }

                if (language.lowercase() == "javascript" || language.lowercase() == "js") {
                    IconButton(
                        onClick = {
                            if (isRunning) {
                                isRunning = false
                                output = output?.plus("\n[Stopped]")
                            } else {
                                isRunning = true
                                output = ""
                                errorOutput = false
                                val script = """
                                    try {
                                        let result = eval('$editableCode');
                                        if (result !== undefined) {
                                            console.log(result);
                                        }
                                    } catch(e) {
                                        console.error(e.toString());
                                    }
                                """.trimIndent().replace("'", "\\'").replace("\n", " ") // A bit naive, but it's a sandbox
                                webView.evaluateJavascript("javascript:(function() { $script })()") { res ->
                                    if (res != null && res != "null") {
                                        output = (output ?: "") + res.removePrefix("\"").removeSuffix("\"") + "\n"
                                    }
                                    isRunning = false
                                }
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "Run", tint = if (isRunning) Color.Red else Color.Green, modifier = Modifier.size(16.dp))
                    }
                }

                IconButton(
                    onClick = { onSaveToWorkspace("snippet_${System.currentTimeMillis()}.${language.ifBlank { "txt" }}", editableCode) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save to Workspace", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (isEditing) {
                androidx.compose.foundation.text.BasicTextField(
                    value = editableCode,
                    onValueChange = { editableCode = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFFD4D4D4),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = editableCode,
                    color = Color(0xFFD4D4D4),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (output != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Output",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = { output = null },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Output", tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = output ?: "",
                    color = if (errorOutput) Color.Red else Color.Green,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
