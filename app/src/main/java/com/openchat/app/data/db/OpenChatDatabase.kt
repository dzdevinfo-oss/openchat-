package com.openchat.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.openchat.app.data.model.AiModel
import com.openchat.app.data.model.ApiProvider
import com.openchat.app.data.model.Memory
import com.openchat.app.data.model.Message
import com.openchat.app.data.model.Session
import com.openchat.app.data.model.WorkspaceFile

@Database(
    entities = [
        Session::class,
        Message::class,
        ApiProvider::class,
        AiModel::class,
        WorkspaceFile::class,
        Memory::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OpenChatDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun apiProviderDao(): ApiProviderDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun workspaceFileDao(): WorkspaceFileDao
    abstract fun memoryDao(): MemoryDao
}
