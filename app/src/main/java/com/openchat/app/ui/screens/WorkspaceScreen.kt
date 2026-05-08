package com.openchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openchat.app.data.model.WorkspaceFile
import com.openchat.app.ui.theme.PrimaryTeal
import com.openchat.app.ui.viewmodels.WorkspaceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    sessionId: String,
    viewModel: WorkspaceViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    LaunchedEffect(sessionId) {
        viewModel.loadWorkspace(sessionId)
    }
    val files by viewModel.files.collectAsState()
    val currentlyOpenFile by viewModel.currentlyOpenFile.collectAsState()

    var showRecycleBin by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Files, 1: Terminal

    if (currentlyOpenFile != null) {
        FileEditorScreen(
            file = currentlyOpenFile!!,
            onClose = { viewModel.closeFile() },
            onSave = { content -> viewModel.updateFile(currentlyOpenFile!!.id, content) },
            onUndo = { viewModel.undoLastEdit(currentlyOpenFile!!.id) }
        )
    } else if (showRecycleBin) {
        RecycleBinScreen(
            viewModel = viewModel,
            onClose = { showRecycleBin = false }
        )
    } else {
        Scaffold(
            topBar = {
                var menuExpanded by remember { mutableStateOf(false) }
                TopAppBar(
                    title = { Text("Workspace") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import File") },
                                onClick = { 
                                    menuExpanded = false 
                                    // TODO Pick file
                                },
                                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export ZIP") },
                                onClick = { 
                                    menuExpanded = false
                                    viewModel.exportWorkspaceAsZip { uri -> }
                                },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Recycle Bin") },
                                onClick = { 
                                    menuExpanded = false
                                    showRecycleBin = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showNewFileDialog = true },
                    containerColor = PrimaryTeal
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New File")
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("FILES") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("TERMINAL") }
                    )
                }
                
                if (selectedTab == 0) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (files.isEmpty()) {
                            item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Workspace is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    // Group files by top-level directory if they have slashes, or put in root
                    val rootFiles = files.filter { !it.fileName.contains("/") }
                    val folderGroups = files.filter { it.fileName.contains("/") }
                        .groupBy { it.fileName.substringBefore("/") }

                    folderGroups.forEach { (folderName, folderFiles) ->
                        item {
                            FolderRow(folderName = folderName)
                        }
                        items(folderFiles) { file ->
                            FileRow(
                                file = file,
                                displayName = file.fileName.substringAfter("/"),
                                indentLevel = 1,
                                onClick = { viewModel.openFile(file) },
                                onDelete = { viewModel.deleteFile(file.id) },
                                onRename = { newName -> viewModel.renameFile(file.id, "$folderName/$newName") }
                            )
                        }
                    }

                    items(rootFiles) { file ->
                        FileRow(
                            file = file,
                            displayName = file.fileName,
                            indentLevel = 0,
                            onClick = { viewModel.openFile(file) },
                            onDelete = { viewModel.deleteFile(file.id) },
                                onRename = { newName -> viewModel.renameFile(file.id, newName) }
                            )
                        }
                    }
                }
                } else {
                    // Terminal Tab
                    AgentTerminalView()
                }
            }
        }
    }

    if (showNewFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    placeholder = { Text("e.g. script.js") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            val ext = newFileName.substringAfterLast('.', "txt")
                            viewModel.createFile(newFileName, "", ext)
                        }
                        showNewFileDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FolderRow(folderName: String) {
    var expanded by remember { mutableStateOf(true) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder, contentDescription = null, tint = PrimaryTeal)
        Spacer(Modifier.width(16.dp))
        Text(folderName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FileRow(
    file: WorkspaceFile,
    displayName: String,
    indentLevel: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = 16.dp + (indentLevel * 24).dp,
                end = 16.dp, 
                top = 12.dp, 
                bottom = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (file.fileType.lowercase()) {
            "kt", "java" -> Icons.Default.Code
            "py" -> Icons.Default.IntegrationInstructions
            "js", "ts", "json" -> Icons.Default.Javascript
            "md", "txt" -> Icons.Default.Description
            else -> Icons.Default.InsertDriveFile
        }
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("Modified: ${dateFormat.format(Date(file.updatedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // ... rest stays same
        
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        expanded = false
                        showRenameDialog = true
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(file.fileName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    onRename(newName)
                    showRenameDialog = false 
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    viewModel: WorkspaceViewModel,
    onClose: () -> Unit
) {
    val deletedFiles by viewModel.deletedFiles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (deletedFiles.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No deleted files", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(deletedFiles) { file ->
                    var optionsExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.fileName, style = MaterialTheme.typography.bodyLarge)
                        }
                        Box {
                            IconButton(onClick = { optionsExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = optionsExpanded,
                                onDismissRequest = { optionsExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Recover") },
                                    onClick = { 
                                        optionsExpanded = false
                                        viewModel.recoverFile(file.id) 
                                    },
                                    leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Permanently Delete") },
                                    onClick = { 
                                        optionsExpanded = false
                                        viewModel.permanentlyDelete(file.id) 
                                    },
                                    leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentTerminalView() {
    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).padding(8.dp)) {
        Text("AI Agent logs & terminal output will stream here.", color = androidx.compose.ui.graphics.Color.Green, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
    }
}
