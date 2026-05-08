package com.openchat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openchat.app.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message)

    @Update
    suspend fun update(message: Message)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySessionId(sessionId: String): Flow<List<Message>>

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :n")
    suspend fun getLastN(sessionId: String, n: Int): List<Message>

    @Query("SELECT * FROM messages WHERE isStreaming = 1")
    suspend fun getAllInterrupted(): List<Message>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
