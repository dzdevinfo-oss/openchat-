package com.openchat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openchat.app.data.model.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: Session)

    @Update
    suspend fun update(session: Session)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): Session?

    @Query("SELECT * FROM sessions WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE isPinned = 1 ORDER BY updatedAt DESC")
    fun getPinned(): Flow<List<Session>>
}
