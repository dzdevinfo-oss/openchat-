package com.openchat.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "api_providers")
data class ApiProvider(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val encryptedApiKey: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
