package com.openchat.app.data.repository

import com.openchat.app.data.db.WorkspaceFileDao
import com.openchat.app.data.model.WorkspaceFile
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepository @Inject constructor(
    private val workspaceFileDao: WorkspaceFileDao
) {
    fun getFilesBySessionId(sessionId: String): Flow<List<WorkspaceFile>> = workspaceFileDao.getBySessionId(sessionId)
    fun getDeletedFilesBySessionId(sessionId: String): Flow<List<WorkspaceFile>> = workspaceFileDao.getDeletedBySession(sessionId)
    suspend fun getFileById(id: String): WorkspaceFile? = workspaceFileDao.getById(id)

    suspend fun createFile(file: WorkspaceFile) {
        // In a real app, you would also write content to the file on disk using file.filePath
        try {
            val f = File(file.filePath)
            f.parentFile?.mkdirs()
            if (!f.exists()) {
                f.writeText(file.content)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        workspaceFileDao.insert(file.copy(previousContent = null))
    }

    suspend fun updateFileContent(id: String, newContent: String) {
        val currentFile = workspaceFileDao.getById(id) ?: return
        
        // 1. Read current content (from DB or disk) -> store in previousContent
        val previousContent = currentFile.content
        
        // 2. Write new content to file on disk
        try {
            val f = File(currentFile.filePath)
            if (f.exists() || f.parentFile?.mkdirs() == true) {
                f.writeText(newContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 3. Update the DB record content field + updatedAt + previousContent
        val updatedFile = currentFile.copy(
            content = newContent,
            previousContent = previousContent,
            updatedAt = System.currentTimeMillis()
        )
        workspaceFileDao.update(updatedFile)
    }
    
    suspend fun updateFileMetadata(file: WorkspaceFile) {
        workspaceFileDao.update(file.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun softDeleteFile(id: String) {
        workspaceFileDao.softDelete(id)
    }

    suspend fun undoLastEdit(id: String) {
        val currentFile = workspaceFileDao.getById(id) ?: return
        if (currentFile.previousContent != null) {
            // Restore previous content to disk
            try {
                val f = File(currentFile.filePath)
                f.writeText(currentFile.previousContent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Swap in DB using DAO query
            workspaceFileDao.undoLastEdit(id)
        }
    }
    
    suspend fun recoverDeletedFile(id: String) {
        workspaceFileDao.recoverDeleted(id)
    }

    suspend fun permanentDeleteFile(id: String) {
        val currentFile = workspaceFileDao.getById(id)
        if (currentFile != null) {
            try {
                val f = File(currentFile.filePath)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            workspaceFileDao.permanentDelete(id)
        }
    }
}
