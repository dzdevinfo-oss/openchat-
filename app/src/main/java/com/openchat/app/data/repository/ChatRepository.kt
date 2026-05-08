package com.openchat.app.data.repository

import com.openchat.app.data.db.MessageDao
import com.openchat.app.data.db.SessionDao
import com.openchat.app.data.model.Message
import com.openchat.app.data.model.Session
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) {
    fun getAllSessions(): Flow<List<Session>> = sessionDao.getAll()
    fun searchSessions(query: String): Flow<List<Session>> = sessionDao.search(query)
    fun getPinnedSessions(): Flow<List<Session>> = sessionDao.getPinned()
    suspend fun getSessionById(id: String): Session? = sessionDao.getById(id)
    
    suspend fun createSession(session: Session) {
        sessionDao.insert(session)
    }

    suspend fun updateSession(session: Session) {
        sessionDao.update(session.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSession(id: String) {
        sessionDao.delete(id)
    }

    fun getMessagesBySessionId(sessionId: String): Flow<List<Message>> = messageDao.getBySessionId(sessionId)
    suspend fun getLastNMessages(sessionId: String, n: Int): List<Message> = messageDao.getLastN(sessionId, n)
    suspend fun getAllInterruptedMessages(): List<Message> = messageDao.getAllInterrupted()
    
    suspend fun insertMessage(message: Message) {
        messageDao.insert(message)
        sessionDao.getById(message.sessionId)?.let {
            sessionDao.update(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun updateMessage(message: Message) {
        messageDao.update(message)
        sessionDao.getById(message.sessionId)?.let {
            sessionDao.update(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteMessagesBySessionId(sessionId: String) {
        messageDao.deleteBySessionId(sessionId)
    }

    suspend fun deleteMessageById(id: String) {
        messageDao.deleteById(id)
    }

    suspend fun updateStreamingMessage(messageId: String, sessionId: String, contentChunk: String, thinkingChunk: String? = null, isComplete: Boolean = false) {
        // Find existing message to append to
        val messages = messageDao.getLastN(sessionId, 50).filter { it.id == messageId }
        val message = messages.firstOrNull()
        if (message != null) {
            val newContent = message.content + contentChunk
            val newThinking = if (thinkingChunk != null) (message.thinkingContent ?: "") + thinkingChunk else message.thinkingContent
            val updatedMessage = message.copy(
                content = newContent,
                thinkingContent = newThinking,
                isStreaming = !isComplete
            )
            messageDao.update(updatedMessage)
            sessionDao.getById(sessionId)?.let {
                sessionDao.update(it.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }
}
