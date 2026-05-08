package com.openchat.app.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitBuilder {
    fun build(baseUrl: String, apiKey: String): ApiService {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .build()
            
            var response = chain.proceed(request)
            var tryCount = 0
            val maxRetries = 3
            
            while (!response.isSuccessful && tryCount < maxRetries && isRetryable(response.code)) {
                tryCount++
                response.close()
                try {
                    Thread.sleep((1000 * Math.pow(2.0, tryCount.toDouble())).toLong())
                } catch (e: InterruptedException) {
                    // Ignore
                }
                response = chain.proceed(request)
            }
            response
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
            
        val standardBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(standardBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun isRetryable(code: Int): Boolean {
        return code == 408 || code == 429 || code >= 500
    }
}
