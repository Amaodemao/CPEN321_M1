package com.example.cpen321application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.cpen321application.ui.theme.CPEN321ApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CPEN321ApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        apiBaseUrl = BuildConfig.API_BASE_URL,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(apiBaseUrl: String, modifier: Modifier = Modifier) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // These states outlive individual pages, so navigation cannot stop their work.
    val loginState = remember { LoginState() }
    val timerState = rememberTimerState()

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BackendHealthStatus(apiBaseUrl)

        when (currentScreen) {
            Screen.HOME -> HomeScreen(
                isSigningIn = loginState.isSigningIn,
                onNavigate = { destination ->
                    currentScreen = destination
                    if (destination == Screen.LOGIN) {
                        scope.launch { loginState.signIn(context, apiBaseUrl) }
                    }
                }
            )
            Screen.LOGIN -> LoginScreen(
                message = loginState.message,
                accountName = loginState.accountName
            )
            Screen.LIVE_UPDATES -> LiveUpdatesScreen(apiBaseUrl)
            Screen.TIMER -> TimerScreen(timerState)
        }

        if (currentScreen != Screen.HOME) {
            Button(onClick = { currentScreen = Screen.HOME }) {
                Text("Return to main menu")
            }
        }
    }

    if (timerState.showTriviaDialog) {
        TriviaSurpriseDialog(onDismiss = timerState::dismissTrivia)
    }
}

@Composable
private fun BackendHealthStatus(apiBaseUrl: String) {
    var statusText by remember(apiBaseUrl) {
        mutableStateOf("Checking backend at $apiBaseUrl/health...")
    }
    LaunchedEffect(apiBaseUrl) {
        statusText = fetchHealthStatus(apiBaseUrl)
    }
    Text(statusText)
}
