package com.openchat.app.util

sealed class MessageChunk {
    data class Text(val content: String) : MessageChunk()
    data class Code(val language: String, val content: String) : MessageChunk()
}

object MessageChunker {
    fun chunk(message: String): List<MessageChunk> {
        val chunks = mutableListOf<MessageChunk>()
        var remaining = message
        val codeRegex = Regex("```(.*?)?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)

        while (remaining.isNotEmpty()) {
            val match = codeRegex.find(remaining)
            if (match != null) {
                if (match.range.first > 0) {
                    chunks.add(MessageChunk.Text(remaining.substring(0, match.range.first)))
                }
                val lang = match.groupValues[1].trim()
                val code = match.groupValues[2]
                chunks.add(MessageChunk.Code(lang, code))
                remaining = remaining.substring(match.range.last + 1)
            } else {
                val incompleteCodeRegex = Regex("```(.*?)?\\n(.*?)$", RegexOption.DOT_MATCHES_ALL)
                val incompleteMatch = incompleteCodeRegex.find(remaining)
                if (incompleteMatch != null) {
                    if (incompleteMatch.range.first > 0) {
                        chunks.add(MessageChunk.Text(remaining.substring(0, incompleteMatch.range.first)))
                    }
                    chunks.add(MessageChunk.Code(incompleteMatch.groupValues[1].trim(), incompleteMatch.groupValues[2]))
                } else {
                    chunks.add(MessageChunk.Text(remaining))
                }
                remaining = ""
            }
        }
        return chunks
    }
}
