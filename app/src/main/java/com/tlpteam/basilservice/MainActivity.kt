package com.tlpteam.basilservice

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.tlpteam.basilservice.ui.screens.LoginScreen
import com.tlpteam.basilservice.ui.screens.MainScreen
import com.tlpteam.basilservice.ui.theme.BasilServiceTheme
import com.tlpteam.basilservice.ui.viewmodel.AuthState
import com.tlpteam.basilservice.ui.viewmodel.MessengerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MessengerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.handleNotificationIntent(intent)
        setContent {
            BasilServiceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by viewModel.authState.collectAsState()
                    when (authState) {
                        is AuthState.LoggedOut -> LoginScreen(viewModel)
                        is AuthState.LoggedIn -> MainScreen(viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleNotificationIntent(intent)
    }
}
