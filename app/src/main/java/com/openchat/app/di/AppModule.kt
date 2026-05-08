package com.openchat.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.openchat.app.data.db.AiModelDao
import com.openchat.app.data.db.ApiProviderDao
import com.openchat.app.data.db.MemoryDao
import com.openchat.app.data.db.MessageDao
import com.openchat.app.data.db.OpenChatDatabase
import com.openchat.app.data.db.SessionDao
import com.openchat.app.data.db.WorkspaceFileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE // Disable logging in production for performance
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            // Long timeouts for SSE / AI streams
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/") // Default base URL
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): OpenChatDatabase {
        return Room.databaseBuilder(
            context,
            OpenChatDatabase::class.java,
            "openchat_db"
        )
        // Enable WAL for performance
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onCreate(db)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val pOpenRouterId = java.util.UUID.randomUUID().toString()
                    val pGroqId = java.util.UUID.randomUUID().toString()
                    val pGoogleId = java.util.UUID.randomUUID().toString()

                    val initialProviders = listOf(
                        com.openchat.app.data.model.ApiProvider(pOpenRouterId, "OpenRouter", "https://openrouter.ai/api/v1/", "", true),
                        com.openchat.app.data.model.ApiProvider(pGroqId, "Groq", "https://api.groq.com/openai/v1/", "", true),
                        com.openchat.app.data.model.ApiProvider(pGoogleId, "Google AI Studio", "https://generativelanguage.googleapis.com/v1beta/openai/", "", true)
                    )

                    val initialModels = listOf(
                        com.openchat.app.data.model.AiModel(java.util.UUID.randomUUID().toString(), pOpenRouterId, "anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", true, "default"),
                        com.openchat.app.data.model.AiModel(java.util.UUID.randomUUID().toString(), pOpenRouterId, "openai/gpt-4o", "GPT-4o", true, "default"),
                        com.openchat.app.data.model.AiModel(java.util.UUID.randomUUID().toString(), pGroqId, "llama3-70b-8192", "Llama 3 70B", true, "default"),
                        com.openchat.app.data.model.AiModel(java.util.UUID.randomUUID().toString(), pGoogleId, "gemini-1.5-pro", "Gemini 1.5 Pro", true, "default")
                    )

                    initialProviders.forEach { p ->
                        db.execSQL("INSERT INTO api_providers (id, name, baseUrl, encryptedApiKey, isActive, createdAt) VALUES ('${p.id}', '${p.name}', '${p.baseUrl}', '${p.encryptedApiKey}', 1, ${System.currentTimeMillis()})")
                    }
                    initialModels.forEach { m ->
                        db.execSQL("INSERT INTO ai_models (id, providerId, modelId, displayName, isBuiltIn, censorMode, createdAt) VALUES ('${m.id}', '${m.providerId}', '${m.modelId}', '${m.displayName}', 1, '${m.censorMode}', ${System.currentTimeMillis()})")
                    }
                }
            }
        })
        .build()
    }

    @Provides
    fun provideSessionDao(database: OpenChatDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideMessageDao(database: OpenChatDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideApiProviderDao(database: OpenChatDatabase): ApiProviderDao = database.apiProviderDao()

    @Provides
    fun provideAiModelDao(database: OpenChatDatabase): AiModelDao = database.aiModelDao()

    @Provides
    fun provideWorkspaceFileDao(database: OpenChatDatabase): WorkspaceFileDao = database.workspaceFileDao()

    @Provides
    fun provideMemoryDao(database: OpenChatDatabase): MemoryDao = database.memoryDao()
}
