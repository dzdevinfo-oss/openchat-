package com.openchat.app.agent

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.openchat.app.data.model.AiModel
import com.openchat.app.data.model.ApiProvider
import com.openchat.app.data.model.Message
import com.openchat.app.data.model.WorkspaceFile
import com.openchat.app.data.repository.AiApiRepository
import com.openchat.app.data.repository.ChatRepository
import com.openchat.app.data.repository.WorkspaceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.first

enum class AgentStatus { RUNNING, PAUSED, DONE, ERROR }

data class AgentJobInfo(
    val sessionId: String,
    val job: Job,
    val status: MutableStateFlow<AgentStatus> = MutableStateFlow(AgentStatus.RUNNING)
)

@Singleton
class AgentManager @Inject constructor(
    private val aiApiRepository: AiApiRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val terminalExecutor: TerminalExecutor,
    private val actionParser: ActionParser,
    private val chatRepository: ChatRepository,
    @ApplicationContext private val context: Context
) {
    private val activeJobs = ConcurrentHashMap<String, AgentJobInfo>()
    
    private val _agentSessions = MutableStateFlow<List<String>>(emptyList())
    val agentSessions: StateFlow<List<String>> = _agentSessions

    private val _logs = ConcurrentHashMap<String, MutableStateFlow<String>>()
    fun getLogs(sessionId: String): StateFlow<String> {
        return _logs.getOrPut(sessionId) { MutableStateFlow("Agent initialized...\n") }.asStateFlow()
    }

    private fun appendLog(sessionId: String, text: String) {
        val flow = _logs.getOrPut(sessionId) { MutableStateFlow("") }
        flow.value += text + "\n"
    }

    fun launchAgent(sessionId: String, task: String, provider: ApiProvider, model: AiModel) {
        if (activeJobs.containsKey(sessionId)) return

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                showNotification(sessionId, "Agent running", task)

                var currentIteration = 0
                var isComplete = false
                
                // Load existing session history
                val sessionMessages = chatRepository.getMessagesBySessionId(sessionId).first().toMutableList()
                
                // If the last message isn't the current task, add it
                if (sessionMessages.none { it.content == task }) {
                    val initialMsg = Message(sessionId = sessionId, role = "user", content = task)
                    chatRepository.insertMessage(initialMsg)
                    sessionMessages.add(initialMsg)
                }

                while (currentIteration < 20 && !isComplete) {
                    currentIteration++
                    appendLog(sessionId, "\n--- TURN $currentIteration ---")
                    
                    var aiResponse = ""
                    var errorMessage: Throwable? = null

                    // Blocking call for stream to complete
                    val chatMsgs = sessionMessages.map { com.openchat.app.data.remote.ChatMessage(it.role, it.content) }
                    aiApiRepository.sendStreamingMessage(
                        provider = provider,
                        model = model,
                        chatMessages = chatMsgs,
                        systemPrompt = """
                            You are an autonomous AI IDE agent with complete access to the user's workspace.
                            Your goal is to accomplish the user's task by managing files, executing terminal commands, and coordinating actions.
                            
                            You MUST follow these rules:
                            1. Be persistent: Files you create in a session stay there unless specifically deleted.
                            2. Use existing files: If a file exists, use 'edit_file' to update it in place instead of creating a new one.
                            3. Manage structure: You can use relative paths (e.g., 'src/main/main.kt') in filenames.
                            4. Verify your work: Use 'list_files' or 'read_file' to see what you've done.
                            5. Think before you act: Keep your non-action thoughts concise.
                            6. Output actions in strictly formatted Markdown blocks.
                            
                            Available Actions:
                            - ```action:create_file\n{"name":"file.ext", "content":"..."}``` - Create a new file.
                            - ```action:edit_file\n{"name":"file.ext", "content":"..."}``` - Update an existing file's full content in-place.
                            - ```action:read_file\n{"name":"file.ext"}``` - Read a file's content.
                            - ```action:list_files\n{"path":"."}``` - List files and directories in a path.
                            - ```action:delete_file\n{"name":"file.ext"}``` - Soft delete a file.
                            - ```action:restore_file\n{"name":"file.ext"}``` - Restore a deleted file.
                            - ```action:undo_edit\n{"name":"file.ext"}``` - Revert the last edit to a file.
                            - ```action:redo_edit\n{"name":"file.ext"}``` - Re-apply the last undone edit.
                            - ```action:create_directory\n{"name":"dir_name"}``` - Create a new directory.
                            - ```action:search_files\n{"query":"text"}``` - Search for text/files in the workspace.
                            - ```action:get_app_info\n{"query":"all"}``` - Get information about the app environment, permissions, and status.
                            - ```action:terminal\n{"command":"..."}``` - Execute a terminal command.
                            - ```action:task_complete\n{"summary":"..."}``` - Mark the task as done.
                            
                            SELF-IMPROVEMENT: You can explore the application's own source code at `/app`. You have permission to read these files to understand your internal logic or suggest architectural improvements.
                            
                            Always prefer direct file updates to maintain a clean codebase.
                        """.trimIndent(),
                        sessionId = sessionId,
                        onToken = { token -> aiResponse += token },
                        onThinking = { },
                        onComplete = { fullText, _ -> aiResponse = fullText },
                        onError = { err -> errorMessage = err }
                    )

                    if (errorMessage != null) {
                        break
                    }

                    // Save AI response
                    val aiMessage = Message(sessionId = sessionId, role = "assistant", content = aiResponse)
                    chatRepository.insertMessage(aiMessage)
                    sessionMessages.add(aiMessage)

                    appendLog(sessionId, "AI Thought: ${aiResponse.substringBefore("```")}")
                    val actions = actionParser.parse(aiResponse)
                    if (actions.isEmpty()) {
                        // AI didn't format an action, prompt it again if it didn't say complete
                        if (!aiResponse.contains("[TASK_COMPLETE]", ignoreCase = true)) {
                            val nudgeMsg = Message(sessionId = sessionId, role = "user", content = "No valid action blocks found. Please use the markdown action format or use task_complete action.")
                            chatRepository.insertMessage(nudgeMsg)
                            sessionMessages.add(nudgeMsg)
                        } else {
                            isComplete = true
                        }
                        continue
                    }

                    val results = mutableListOf<String>()

                    for (action in actions) {
                        when (action) {
                            is Action.CreateFile -> {
                                appendLog(sessionId, "Action: Create File -> ${action.name}")
                                val file = WorkspaceFile(
                                    workspaceId = sessionId,
                                    sessionId = sessionId,
                                    fileName = action.name,
                                    filePath = "${context.filesDir}/workspace/$sessionId/${action.name}",
                                    content = action.content,
                                    fileType = action.name.substringAfterLast('.', "txt")
                                )
                                workspaceRepository.createFile(file)
                                results.add("File ${action.name} created successfully.")
                            }
                            is Action.EditFile -> {
                                appendLog(sessionId, "Action: Edit File ${action.name}")
                                val files = workspaceRepository.getFilesBySessionId(sessionId).first()
                                val file = files.find { it.fileName == action.name }
                                if (file != null) {
                                    workspaceRepository.updateFileContent(file.id, action.content)
                                    results.add("File ${action.name} updated successfully.")
                                } else {
                                    results.add("Error: File ${action.name} does not exist. Use create_file instead.")
                                }
                            }
                            is Action.ReadFile -> {
                                val files = workspaceRepository.getFilesBySessionId(sessionId).first()
                                val repoFile = files.find { it.fileName == action.name }
                                if (repoFile != null) {
                                    results.add("Content of ${action.name}:\n${repoFile.content}")
                                } else {
                                    val diskFile = if (action.name.startsWith("/")) File(action.name)
                                                 else File(context.filesDir, "workspace/$sessionId/${action.name}")
                                    if (diskFile.exists() && diskFile.isFile) {
                                        results.add("Content of ${action.name}:\n${diskFile.readText()}")
                                    } else {
                                        results.add("Error: File ${action.name} not found.")
                                    }
                                }
                            }
                            is Action.ListFiles -> {
                                val dir = if (action.path.startsWith("/")) File(action.path)
                                         else File(context.filesDir, "workspace/$sessionId/${action.path}")
                                
                                if (dir.exists() && dir.isDirectory) {
                                    val list = dir.listFiles()?.joinToString("\n") { 
                                        if (it.isDirectory) "[DIR] ${it.name}" else "- ${it.name}"
                                    } ?: "Empty."
                                    results.add("Files in ${dir.absolutePath}:\n$list")
                                } else {
                                    results.add("Error: Path ${action.path} not found or is not a directory.")
                                }
                            }
                            is Action.DeleteFile -> {
                                val files = workspaceRepository.getFilesBySessionId(sessionId).first()
                                val file = files.find { it.fileName == action.name }
                                if (file != null) {
                                    workspaceRepository.softDeleteFile(file.id)
                                    results.add("File ${action.name} deleted.")
                                } else {
                                    results.add("Error: File ${action.name} not found.")
                                }
                            }
                            is Action.RestoreFile -> {
                                val files = workspaceRepository.getDeletedFilesBySessionId(sessionId).first()
                                val file = files.find { it.fileName == action.name }
                                if (file != null) {
                                    workspaceRepository.recoverDeletedFile(file.id)
                                    results.add("File ${action.name} restored.")
                                } else {
                                    results.add("Error: No deleted file found named ${action.name}.")
                                }
                            }
                            is Action.UndoEdit -> {
                                val files = workspaceRepository.getFilesBySessionId(sessionId).first()
                                val file = files.find { it.fileName == action.name }
                                if (file != null) {
                                    workspaceRepository.undoLastEdit(file.id)
                                    results.add("Last edit to ${action.name} undone.")
                                } else {
                                    results.add("Error: File ${action.name} not found.")
                                }
                            }
                            is Action.RedoEdit -> {
                                val files = workspaceRepository.getFilesBySessionId(sessionId).first()
                                val file = files.find { it.fileName == action.name }
                                if (file != null) {
                                    workspaceRepository.undoLastEdit(file.id) // Same swap logic
                                    results.add("Redo applied to ${action.name}.")
                                } else {
                                    results.add("Error: File ${action.name} not found.")
                                }
                            }
                            is Action.CreateDirectory -> {
                                val dir = File(context.filesDir, "workspace/$sessionId/${action.name}")
                                if (dir.mkdirs() || dir.exists()) {
                                    results.add("Directory ${action.name} created/verified.")
                                } else {
                                    results.add("Error: Could not create directory ${action.name}.")
                                }
                            }
                            is Action.SearchFiles -> {
                                val files = workspaceRepository.getFilesBySessionId(sessionId).first()
                                val matched = files.filter { 
                                    it.fileName.contains(action.query, ignoreCase = true) || 
                                    it.content.contains(action.query, ignoreCase = true)
                                }.joinToString("\n") { "- ${it.fileName}" }
                                results.add(if (matched.isEmpty()) "No matches found for '${action.query}'." else "Search results for '${action.query}':\n$matched")
                            }
                            is Action.GetAppInfo -> {
                                val info = """
                                    App Name: OpenChat
                                    Version: 1.0.0
                                    OS: Android (API ${android.os.Build.VERSION.SDK_INT})
                                    Workspace Path: /data/user/0/com.openchat.app/files/workspace/$sessionId
                                    Storage: Local persistence (Room + DataStore)
                                    UI: Jetpack Compose
                                    AI Access Level: Full (File System, Terminal, Application Memory)
                                """.trimIndent()
                                results.add(info)
                            }
                            is Action.TerminalCommand -> {
                                appendLog(sessionId, "Action: Run Terminal > ${action.command}")
                                val out = terminalExecutor.execute(action.command, File(context.filesDir, "workspace/$sessionId"))
                                results.add("Terminal out:\n$out")
                                appendLog(sessionId, "Terminal Output: $out")
                            }
                            is Action.TaskComplete -> {
                                appendLog(sessionId, "Task Complete: ${action.summary}")
                                results.add("Task marked complete: ${action.summary}")
                                isComplete = true
                            }
                            is Action.InvalidCommand -> {
                                results.add("Action parse error: ${action.reason}")
                            }
                        }
                    }

                    if (!isComplete) {
                        val resultMsg = Message(sessionId = sessionId, role = "system", content = "Action results:\n" + results.joinToString("\n"))
                        chatRepository.insertMessage(resultMsg)
                        sessionMessages.add(resultMsg)
                    }
                }
                
                activeJobs[sessionId]?.status?.value = AgentStatus.DONE
                showNotification(sessionId, "Agent completed", task)

            } catch (e: Exception) {
                activeJobs[sessionId]?.status?.value = AgentStatus.ERROR
            } finally {
                activeJobs.remove(sessionId)
                _agentSessions.value = activeJobs.keys().toList()
            }
        }
        
        activeJobs[sessionId] = AgentJobInfo(sessionId, job)
        _agentSessions.value = activeJobs.keys().toList()
    }

    fun pauseAgent(sessionId: String) {
        val info = activeJobs[sessionId]
        if (info != null && info.status.value == AgentStatus.RUNNING) {
            // Cannot truly pause a coroutine easily unless we check flags inside the loop.
            // For now, suspend execution or just cancel. We'll cancel for simplicity.
            info.job.cancel()
            info.status.value = AgentStatus.PAUSED
        }
    }

    fun cancelAgent(sessionId: String) {
        activeJobs[sessionId]?.job?.cancel()
        activeJobs.remove(sessionId)
        _agentSessions.value = activeJobs.keys().toList()
        NotificationManagerCompat.from(context).cancel(sessionId.hashCode())
    }

    private fun showNotification(sessionId: String, title: String, content: String) {
        // Assume channel is created in MainActivity or App
        try {
            val builder = NotificationCompat.Builder(context, "agent_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(title == "Agent running")

            with(NotificationManagerCompat.from(context)) {
                notify(sessionId.hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            // Missing permissions
        }
    }
}
