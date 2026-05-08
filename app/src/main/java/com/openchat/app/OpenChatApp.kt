package com.openchat.app

import android.app.Application
import android.util.Log
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
}
