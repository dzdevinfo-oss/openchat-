package com.openchat.app.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openchat.app.data.db.AiModelDao
import com.openchat.app.data.db.ApiProviderDao
import com.openchat.app.data.model.AiModel
import com.openchat.app.data.model.ApiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepository @Inject constructor(
    private val apiProviderDao: ApiProviderDao,
    private val aiModelDao: AiModelDao,
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "api_keys_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getAllProviders(): Flow<List<ApiProvider>> = apiProviderDao.getAll()
    fun getActiveProviders(): Flow<List<ApiProvider>> = apiProviderDao.getActive()
    suspend fun getProviderById(id: String): ApiProvider? = apiProviderDao.getById(id)

    suspend fun createProvider(provider: ApiProvider) {
        apiProviderDao.insert(provider)
    }

    suspend fun updateProvider(provider: ApiProvider) {
        apiProviderDao.update(provider)
    }

    suspend fun deleteProvider(id: String) {
        apiProviderDao.delete(id)
        // Also remove the stored API key
        sharedPreferences.edit().remove("api_key_$id").apply()
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        sharedPreferences.edit().putString("api_key_$providerId", apiKey).apply()
    }

    fun getApiKey(providerId: String): String? {
        return sharedPreferences.getString("api_key_$providerId", null)
    }

    // AI Models
    fun getAllModels(): Flow<List<AiModel>> = aiModelDao.getAll()
    fun getModelsByProvider(providerId: String): Flow<List<AiModel>> = aiModelDao.getByProvider(providerId)
    fun getBuiltInModels(): Flow<List<AiModel>> = aiModelDao.getBuiltIn()
    suspend fun getModelById(modelId: String): AiModel? = aiModelDao.getByModelId(modelId)

    suspend fun createModel(model: AiModel) {
        aiModelDao.insert(model)
    }

    suspend fun updateModel(model: AiModel) {
        aiModelDao.update(model)
    }

    suspend fun deleteModel(id: String) {
        aiModelDao.delete(id)
    }
}
