package com.openchat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openchat.app.data.model.AiModel
import com.openchat.app.ui.theme.PrimaryTeal
import com.openchat.app.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModelsBottomSheet(
    viewModel: SettingsViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val models by viewModel.models.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val customModels = models.filter { !it.isBuiltIn }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Custom Models", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item {
                    AddCustomModelCard(
                        providers = providers,
                        onAdd = { mId, name, pId, mode ->
                            viewModel.addCustomModel(mId, name, pId, mode,
                                onSuccess = {
                                    scope.launch { snackbarHostState.showSnackbar("Model added ✓") }
                                },
                                onError = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                    )
                    Spacer(Modifier.height(24.dp))
                }

                items(customModels) { model ->
                    CustomModelCard(
                        model = model,
                        providers = providers,
                        onSave = { mId, name, pId, mode ->
                            viewModel.updateCustomModel(model, mId, name, pId, mode,
                                onSuccess = {
                                    scope.launch { snackbarHostState.showSnackbar("Model updated ✓") }
                                },
                                onError = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        },
                        onDelete = { viewModel.deleteCustomModel(model.id) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomModelCard(
    providers: List<com.openchat.app.data.model.ApiProvider>,
    onAdd: (String, String, String, String) -> Unit
) {
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var selectedProviderId by remember { mutableStateOf(providers.firstOrNull()?.id ?: "") }
    var selectedMode by remember { mutableStateOf("default") }

    var pExpanded by remember { mutableStateOf(false) }
    var mExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ADD CUSTOM MODEL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text("Model ID (e.g. google/gemini-2.5-flash)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            
            ExposedDropdownMenuBox(
                expanded = pExpanded,
                onExpandedChange = { pExpanded = !pExpanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = providers.find { it.id == selectedProviderId }?.name ?: "Select Provider",
                    onValueChange = { },
                    label = { Text("Select Provider API") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = pExpanded,
                    onDismissRequest = { pExpanded = false }
                ) {
                    providers.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                selectedProviderId = p.id
                                pExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            
            ExposedDropdownMenuBox(
                expanded = mExpanded,
                onExpandedChange = { mExpanded = !mExpanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedMode.replaceFirstChar { it.uppercase() },
                    onValueChange = { },
                    label = { Text("Assign to Censored Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = mExpanded,
                    onDismissRequest = { mExpanded = false }
                ) {
                    listOf("default", "uncensored", "safe").forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                selectedMode = mode
                                mExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { 
                    onAdd(modelId, displayName, selectedProviderId, selectedMode)
                    if (modelId.isNotBlank() && displayName.isNotBlank() && selectedProviderId.isNotBlank()) {
                        modelId = ""
                        displayName = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Text("Add Model", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModelCard(
    model: AiModel,
    providers: List<com.openchat.app.data.model.ApiProvider>,
    onSave: (String, String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!isEditing) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.modelId, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text(model.displayName, style = MaterialTheme.typography.bodyMedium, color = PrimaryTeal)
                    }
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                var editModelId by remember { mutableStateOf(model.modelId) }
                var editDisplayName by remember { mutableStateOf(model.displayName) }
                var editProviderId by remember { mutableStateOf(model.providerId) }
                var editMode by remember { mutableStateOf(model.censorMode) }

                var pExpanded by remember { mutableStateOf(false) }
                var mExpanded by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = editModelId,
                    onValueChange = { editModelId = it },
                    label = { Text("Model ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editDisplayName,
                    onValueChange = { editDisplayName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                
                ExposedDropdownMenuBox(
                    expanded = pExpanded,
                    onExpandedChange = { pExpanded = !pExpanded }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = providers.find { it.id == editProviderId }?.name ?: "Select Provider",
                        onValueChange = { },
                        label = { Text("Provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = pExpanded,
                        onDismissRequest = { pExpanded = false }
                    ) {
                        providers.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = {
                                    editProviderId = p.id
                                    pExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                
                ExposedDropdownMenuBox(
                    expanded = mExpanded,
                    onExpandedChange = { mExpanded = !mExpanded }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = editMode.replaceFirstChar { it.uppercase() },
                        onValueChange = { },
                        label = { Text("Censored Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = mExpanded,
                        onDismissRequest = { mExpanded = false }
                    ) {
                        listOf("default", "uncensored", "safe").forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    editMode = mode
                                    mExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { isEditing = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(editModelId, editDisplayName, editProviderId, editMode)
                            isEditing = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
