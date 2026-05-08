package com.openchat.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sessionId: String? = null,
    val isActive: Boolean = true
)
