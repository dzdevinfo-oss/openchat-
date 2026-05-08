package com.openchat.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openchat.app.ui.theme.PrimaryTeal
import com.openchat.app.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // Appearance
    val darkTheme by viewModel.darkTheme.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val messageBubbleStyle by viewModel.messageBubbleStyle.collectAsState()
    val showTimestamps by viewModel.showTimestamps.collectAsState()

    // AI Behavior
    val defaultSystemPrompt by viewModel.defaultSystemPrompt.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val maxTokens by viewModel.maxTokens.collectAsState()
    val streaming by viewModel.streaming.collectAsState()
    val extendedThinking by viewModel.extendedThinking.collectAsState()
    val thinkingBudget by viewModel.thinkingBudget.collectAsState()

    // Memory
    val isMemoryEnabled by viewModel.isMemoryEnabled.collectAsState()

    // Voice
    val enableVoiceInput by viewModel.enableVoiceInput.collectAsState()
    val autoSendVoice by viewModel.autoSendVoice.collectAsState()
    val enableTts by viewModel.enableTts.collectAsState()
    val ttsSpeed by viewModel.ttsSpeed.collectAsState()
    val ttsPitch by viewModel.ttsPitch.collectAsState()

    // Workspace
    val autoInjectContext by viewModel.autoInjectContext.collectAsState()
    val maxContextFiles by viewModel.maxContextFiles.collectAsState()
    val autoSaveArtifacts by viewModel.autoSaveArtifacts.collectAsState()

    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importData(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, 
                            contentDescription = "Go back to chat"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SettingsHeader("APPEARANCE") }
            item {
                SettingsSwitch("Dark Theme", darkTheme) { viewModel.setDarkTheme(it) }
                SettingsSlider("Font Size", fontSize, 12f..20f, steps = 7) { viewModel.setFontSize(it) }
                SettingsDropdown("Message Bubble Style", messageBubbleStyle, listOf("Modern", "Minimal", "Classic")) { viewModel.setMessageBubbleStyle(it) }
                SettingsSwitch("Show Timestamps", showTimestamps) { viewModel.setShowTimestamps(it) }
            }

            item { SettingsHeader("AI BEHAVIOR") }
            item {
                SettingsTextField("Default System Prompt", defaultSystemPrompt, multiLine = true) { viewModel.setDefaultSystemPrompt(it) }
                SettingsSlider("Temperature", temperature, 0.0f..2.0f, steps = 19) { viewModel.setTemperature(it) }
                SettingsNumberInput("Max Tokens", maxTokens) { viewModel.setMaxTokens(it) }
                SettingsSwitch("Streaming", streaming) { viewModel.setStreaming(it) }
                SettingsSwitch("Extended Thinking", extendedThinking) { viewModel.setExtendedThinking(it) }
                if (extendedThinking) {
                    SettingsSlider("Thinking Budget (Tokens)", thinkingBudget.toFloat(), 1000f..50000f, steps = 49) { viewModel.setThinkingBudget(it.toInt()) }
                }
            }

            item { SettingsHeader("MEMORY") }
            item {
                SettingsSwitch("Enable Memory", isMemoryEnabled) { viewModel.setMemoryEnabled(it) }
                // In a real app we'd navigate to MemoryListScreen. For now we use the viewmodel function
                SettingsButton("Clear All Memories", true) { viewModel.clearAllMemories() }
            }

            item { SettingsHeader("VOICE") }
            item {
                SettingsSwitch("Enable Voice Input", enableVoiceInput) { viewModel.setEnableVoiceInput(it) }
                SettingsSwitch("Auto-Send after Voice", autoSendVoice) { viewModel.setAutoSendVoice(it) }
                SettingsSwitch("Enable TTS (read responses)", enableTts) { viewModel.setEnableTts(it) }
                SettingsSlider("TTS Speed", ttsSpeed, 0.5f..2.0f, steps = 15) { viewModel.setTtsSpeed(it) }
                SettingsSlider("TTS Pitch", ttsPitch, 0.5f..2.0f, steps = 15) { viewModel.setTtsPitch(it) }
            }

            item { SettingsHeader("WORKSPACE") }
            item {
                SettingsSwitch("Auto-Inject Workspace Context", autoInjectContext) { viewModel.setAutoInjectContext(it) }
                SettingsNumberInput("Max Context Files", maxContextFiles) { viewModel.setMaxContextFiles(it) }
                SettingsSwitch("Auto-Save Artifacts", autoSaveArtifacts) { viewModel.setAutoSaveArtifacts(it) }
            }

            item { SettingsHeader("STORAGE") }
            item {
                val dbSize = remember { viewModel.getDatabaseSize() }
                val workspaceSize = remember { viewModel.getWorkspaceSize() }
                
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Storage Used", style = MaterialTheme.typography.titleSmall)
                    Text("$dbSize by chats, $workspaceSize by workspace files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                SettingsButton("Export All Data") {
                    val uri = viewModel.exportAllData()
                    if (uri != null) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Data"))
                    }
                }
                SettingsButton("Import Data") { importLauncher.launch("application/zip") }
                SettingsButton("Clear All Chat History", isDestructive = true) { showClearHistoryConfirm = true }
            }

            item { SettingsHeader("ABOUT") }
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App Version: 1.0.0", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("OpenChat by User", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "GitHub Link", 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = PrimaryTeal,
                        modifier = Modifier.clickable { uriHandler.openUri("https://github.com/") }
                    )
                }
            }
        }
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear All Chat History?") },
            text = { Text("This action cannot be undone. All chats and workspace files will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearHistoryConfirm = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryTeal, checkedTrackColor = PrimaryTeal.copy(alpha = 0.5f))
        )
    }
}

@Composable
fun SettingsSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(String.format(java.util.Locale.US, "%.1f", value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(title: String, selectedValue: String, options: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.width(150.dp)
        ) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTextField(title: String, value: String, multiLine: Boolean = false, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = !multiLine,
            maxLines = if (multiLine) 5 else 1
        )
    }
}

@Composable
fun SettingsNumberInput(title: String, value: Int, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(4.dp))
        var textValue by remember(value) { mutableStateOf(value.toString()) }
        OutlinedTextField(
            value = textValue,
            onValueChange = { 
                textValue = it
                it.toIntOrNull()?.let { num -> onValueChange(num) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsButton(title: String, isDestructive: Boolean = false, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Text(title, color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontSize = 16.sp)
        }
    }
}
