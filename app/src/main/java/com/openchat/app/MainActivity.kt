package com.openchat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.openchat.app.ui.theme.OpenChatTheme
import com.openchat.app.util.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme by settingsManager.darkTheme.collectAsState(initial = true)
            val fontSize by settingsManager.fontSize.collectAsState(initial = 16f)
            
            OpenChatTheme(
                darkTheme = darkTheme,
                fontSize = fontSize
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.openchat.app.ui.navigation.AppNavigation(settingsManager)
                }
            }
        }
    }
}
