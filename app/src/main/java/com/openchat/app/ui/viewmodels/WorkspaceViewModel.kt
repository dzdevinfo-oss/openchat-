package com.openchat.app.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openchat.app.data.model.WorkspaceFile
import com.openchat.app.data.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _currentSessionId = MutableStateFlow<String?>(null)

    val files: StateFlow<List<WorkspaceFile>> = _currentSessionId
        .filterNotNull()
        .flatMapLatest { workspaceRepository.getFilesBySessionId(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val deletedFiles: StateFlow<List<WorkspaceFile>> = _currentSessionId
        .filterNotNull()
        .flatMapLatest { workspaceRepository.getDeletedFilesBySessionId(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentlyOpenFile = MutableStateFlow<WorkspaceFile?>(null)
    val currentlyOpenFile: StateFlow<WorkspaceFile?> = _currentlyOpenFile.asStateFlow()

    fun loadWorkspace(sessionId: String) {
        _currentSessionId.value = sessionId
    }

    fun openFile(file: WorkspaceFile) {
        _currentlyOpenFile.value = file
    }

    fun closeFile() {
        _currentlyOpenFile.value = null
    }

    fun createFile(name: String, content: String, type: String) {
        val sessionId = _currentSessionId.value ?: return
        val workspaceId = sessionId // For now, 1:1 mapping
        val filePath = File(context.filesDir, "workspaces/$sessionId/$name").absolutePath
        
        val newFile = WorkspaceFile(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            sessionId = sessionId,
            fileName = name,
            filePath = filePath,
            fileType = type,
            content = content
        )
        viewModelScope.launch {
            workspaceRepository.createFile(newFile)
            _currentlyOpenFile.value = newFile
        }
    }

    fun updateFile(id: String, newContent: String) {
        viewModelScope.launch {
            workspaceRepository.updateFileContent(id, newContent)
            if (_currentlyOpenFile.value?.id == id) {
                _currentlyOpenFile.value = workspaceRepository.getFileById(id)
            }
        }
    }

    fun deleteFile(id: String) {
        viewModelScope.launch {
            workspaceRepository.softDeleteFile(id)
            if (_currentlyOpenFile.value?.id == id) {
                _currentlyOpenFile.value = null
            }
        }
    }

    fun recoverFile(id: String) {
        viewModelScope.launch {
            workspaceRepository.recoverDeletedFile(id)
        }
    }

    fun permanentlyDelete(id: String) {
        viewModelScope.launch {
            workspaceRepository.permanentDeleteFile(id)
        }
    }

    fun undoLastEdit(id: String) {
        viewModelScope.launch {
            workspaceRepository.undoLastEdit(id)
            if (_currentlyOpenFile.value?.id == id) {
                _currentlyOpenFile.value = workspaceRepository.getFileById(id)
            }
        }
    }

    fun renameFile(id: String, newName: String) {
        viewModelScope.launch {
            val file = workspaceRepository.getFileById(id) ?: return@launch
            val extension = MimeTypeMap.getFileExtensionFromUrl(newName) ?: ""
            val newFilePath = File(context.filesDir, "workspaces/${file.sessionId}/$newName").absolutePath
            
            // Move file on disk
            try {
                val oldFile = File(file.filePath)
                val newDiskFile = File(newFilePath)
                if (oldFile.exists() && !newDiskFile.exists()) {
                    oldFile.renameTo(newDiskFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            workspaceRepository.updateFileMetadata(
                file.copy(fileName = newName, filePath = newFilePath, fileType = extension)
            )
            if (_currentlyOpenFile.value?.id == id) {
                _currentlyOpenFile.value = workspaceRepository.getFileById(id)
            }
        }
    }

    fun exportWorkspaceAsZip(onExported: (Uri) -> Unit) {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            val currentFiles = files.value
            if (currentFiles.isEmpty()) return@launch

            val zipFile = File(context.cacheDir, "workspace_$sessionId.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                currentFiles.forEach { wf ->
                    val file = File(wf.filePath)
                    if (file.exists()) {
                        zos.putNextEntry(ZipEntry(wf.fileName))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            // Real app would use FileProvider.getUriForFile
            onExported(Uri.fromFile(zipFile))
        }
    }

    fun importFileFromDevice(uri: Uri) {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                var name = "imported_file_${System.currentTimeMillis()}"
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex("_display_name")
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                    cursor.close()
                }
                
                val extension = MimeTypeMap.getFileExtensionFromUrl(name) ?: ""
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                
                createFile(name, content, extension)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
