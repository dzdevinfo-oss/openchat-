package com.openchat.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "ai_models",
    foreignKeys = [
        ForeignKey(
            entity = ApiProvider::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["providerId"])]
)
data class AiModel(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val modelId: String,
    val displayName: String,
    val providerId: String,
    val isBuiltIn: Boolean = false,
    val censorMode: String = "default", // "default" | "uncensored" | "safe"
    val contextWindow: Int? = null,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true
)
