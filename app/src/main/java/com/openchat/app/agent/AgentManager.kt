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

    fun launchAgent(sessionId: String, task: String, provider: ApiProvider, model: AiModel) {
        if (activeJobs.containsKey(sessionId)) return

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                showNotification(sessionId, "Agent running", task)

                var currentIteration = 0
                var isComplete = false
                
                // Add task to messages
                val sessionMessages = mutableListOf<Message>()
                val initialMsg = Message(sessionId = sessionId, role = "user", content = task)
                chatRepository.insertMessage(initialMsg)
                sessionMessages.add(initialMsg)

                while (currentIteration < 20 && !isComplete) {
                    currentIteration++
                    
                    var aiResponse = ""
                    var errorMessage: Throwable? = null

                    // Blocking call for stream to complete
                    val chatMsgs = sessionMessages.map { com.openchat.app.data.remote.ChatMessage(it.role, it.content) }
                    aiApiRepository.sendStreamingMessage(
                        provider = provider,
                        model = model,
                        chatMessages = chatMsgs,
                        systemPrompt = "You are an autonomous agent. Accomplish the user's task. Output actions in strictly formatted Markdown blocks like ```action:create_file\\n{\"name\":\"file.ext\",\"content\":\"...\"}```. Other actions: edit_file, terminal, task_complete. Keep non-action thinking concise.",
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

                    val actions = actionParser.parse(aiResponse)
                    if (actions.isEmpty()) {
                        // AI didn't format an action, prompt it again
                        val nudgeMsg = Message(sessionId = sessionId, role = "user", content = "No valid actions found in your response. Please formulate your action using the action markdown blocks, or use [TASK_COMPLETE] if done.")
                        chatRepository.insertMessage(nudgeMsg)
                        sessionMessages.add(nudgeMsg)
                        continue
                    }

                    val results = mutableListOf<String>()

                    for (action in actions) {
                        when (action) {
                            is Action.CreateFile -> {
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
                                // Find id by name
                                val files = kotlinx.coroutines.flow.first(workspaceRepository.getFilesBySessionId(sessionId))
                                val file = files.find { it.fileName == action.name }
                                if (file != null) {
                                    workspaceRepository.updateFileContent(file.id, action.content)
                                    results.add("File ${action.name} edited successfully.")
                                } else {
                                    results.add("File ${action.name} not found.")
                                }
                            }
                            is Action.TerminalCommand -> {
                                val out = terminalExecutor.execute(action.command)
                                results.add("Terminal out: $out")
                            }
                            is Action.TaskComplete -> {
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
