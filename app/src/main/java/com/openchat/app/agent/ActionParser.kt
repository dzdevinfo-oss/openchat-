package com.openchat.app.agent

import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed class Action {
    data class CreateFile(val name: String, val content: String) : Action()
    data class EditFile(val name: String, val content: String) : Action()
    data class ReadFile(val name: String) : Action()
    data class ListFiles(val path: String = ".") : Action()
    data class DeleteFile(val name: String) : Action()
    data class RestoreFile(val name: String) : Action()
    data class UndoEdit(val name: String) : Action()
    data class RedoEdit(val name: String) : Action()
    data class CreateDirectory(val name: String) : Action()
    data class TerminalCommand(val command: String) : Action()
    data class TaskComplete(val summary: String) : Action()
    data class InvalidCommand(val reason: String) : Action()
}

@Singleton
class ActionParser @Inject constructor() {
    fun parse(response: String): List<Action> {
        val actions = mutableListOf<Action>()
        
        // Find blocks like ```action:create_file\n{"name":"...", "content":"..."}```
        val regex = Regex("```action:([a-z_]+)\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(response)
        
        for (match in matches) {
            val command = match.groupValues[1]
            val jsonBody = match.groupValues[2].trim()
            
            try {
                val json = JSONObject(jsonBody)
                val action = when (command) {
                    "create_file" -> {
                        Action.CreateFile(
                            name = json.getString("name"),
                            content = json.getString("content")
                        )
                    }
                    "edit_file" -> {
                        Action.EditFile(
                            name = json.getString("name"),
                            content = json.getString("content")
                        )
                    }
                    "read_file" -> {
                        Action.ReadFile(
                            name = json.getString("name")
                        )
                    }
                    "list_files" -> {
                        Action.ListFiles(
                            path = if (json.has("path")) json.getString("path") else "."
                        )
                    }
                    "delete_file" -> {
                        Action.DeleteFile(
                            name = json.getString("name")
                        )
                    }
                    "restore_file" -> {
                        Action.RestoreFile(
                            name = json.getString("name")
                        )
                    }
                    "undo_edit" -> {
                        Action.UndoEdit(
                            name = json.getString("name")
                        )
                    }
                    "redo_edit" -> {
                        Action.RedoEdit(
                            name = json.getString("name")
                        )
                    }
                    "create_directory" -> {
                        Action.CreateDirectory(
                            name = json.getString("name")
                        )
                    }
                    "terminal" -> {
                        Action.TerminalCommand(
                            command = json.getString("command")
                        )
                    }
                    "task_complete" -> {
                        Action.TaskComplete(
                            summary = json.getString("summary")
                        )
                    }
                    else -> Action.InvalidCommand("Unknown action type: $command")
                }
                actions.add(action)
            } catch (e: Exception) {
                actions.add(Action.InvalidCommand("JSON parsing error: ${e.message}"))
            }
        }
        
        return actions
    }
}
