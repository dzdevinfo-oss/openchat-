package com.openchat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openchat.app.data.model.Memory
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: Memory)

    @Update
    suspend fun update(memory: Memory)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Memory>>

    @Query("SELECT * FROM memories WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun getBySession(sessionId: String): Flow<List<Memory>>

    @Query("SELECT * FROM memories WHERE sessionId IS NULL ORDER BY createdAt DESC")
    fun getGlobal(): Flow<List<Memory>>

    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()
}
