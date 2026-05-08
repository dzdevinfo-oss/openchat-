package com.openchat.app.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TerminalExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workingDir: File = File(context.filesDir, "workspace").apply {
        if (!exists()) mkdirs()
    }

    suspend fun execute(command: String): String = withContext(Dispatchers.IO) {
        try {
            // Very simple tokenization for the process builder
            val commandParts = if (command.startsWith("bash -c ")) {
                listOf("bash", "-c", command.removePrefix("bash -c "))
            } else if (command.startsWith("sh -c ")) {
                listOf("sh", "-c", command.removePrefix("sh -c "))
            } else {
                listOf("sh", "-c", command)
            }

            val process = ProcessBuilder(commandParts)
                .directory(workingDir)
                .redirectErrorStream(true) // Combine stdout and stderr
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(30, TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                return@withContext "$output\n[Process timed out after 30 seconds]"
            }

            output.ifBlank { "[Command executed successfully with no output]" }
        } catch (e: Exception) {
            "Error executing command: ${e.message}"
        }
    }
}
