package com.openchat.app

import android.app.Application
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.openchat.app.util.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class OpenChatApp : Application() {

    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        setupGlobalExceptionHandler()
        
        // Seed built-in providers and models
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            databaseSeeder.seedDatabase()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun setupGlobalExceptionHandler() {
        val handler = CoroutineExceptionHandler { _, exception ->
            Log.e("OpenChat", "Global coroutine exception caught: ${exception.message}", exception)
        }
        
        GlobalScope.launch(handler) {
            // Keep handler attached to an active scope to prevent silent crashes
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Agent Status"
            val descriptionText = "Notifications about autonomous agent progress"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("agent_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
