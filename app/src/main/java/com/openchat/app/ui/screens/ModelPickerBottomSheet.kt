package com.openchat.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openchat.app.data.model.AiModel
import com.openchat.app.data.model.ApiProvider
import com.openchat.app.ui.theme.PrimaryTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerBottomSheet(
    models: List<AiModel>,
    providers: List<ApiProvider>,
    selectedModel: AiModel?,
    onModelSelected: (AiModel) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var searchQuery by remember { mutableStateOf("") }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search models...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryTeal
                )
            )
            
            Spacer(Modifier.height(16.dp))
            
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                val filteredModels = models.filter { it.displayName.contains(searchQuery, ignoreCase = true) || it.modelId.contains(searchQuery, ignoreCase = true) }
                
                val builtInModels = filteredModels.filter { it.isBuiltIn }
                val customModels = filteredModels.filter { !it.isBuiltIn }
                
                val groupedBuiltIn = builtInModels.groupBy { it.providerId }

                groupedBuiltIn.forEach { (providerId, providerModels) ->
                    val providerName = providers.find { it.id == providerId }?.name ?: "Unknown Provider"
                    item {
                        Text(providerName.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(providerModels) { model ->
                        ModelRow(model, providerName, selectedModel, onModelSelected)
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                if (customModels.isNotEmpty()) {
                    item {
                        Text("CUSTOM MODELS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(customModels) { model ->
                        val providerName = providers.find { it.id == model.providerId }?.name ?: "Unknown Provider"
                        ModelRow(model, providerName, selectedModel, onModelSelected)
                    }
                }
            }
        }
    }
}

@Composable
fun ModelRow(model: AiModel, providerName: String, selectedModel: AiModel?, onModelSelected: (AiModel) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onModelSelected(model) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryTeal)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(model.displayName, style = MaterialTheme.typography.bodyLarge)
            Text("$providerName • ${model.modelId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(
            selected = model.id == selectedModel?.id,
            onClick = { onModelSelected(model) },
            colors = RadioButtonDefaults.colors(selectedColor = PrimaryTeal)
        )
    }
}

