package com.example.cpen321application

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

enum class Screen {
    HOME,
    LOGIN,
    LIVE_UPDATES,
    TIMER
}

@Composable
internal fun HomeScreen(isSigningIn: Boolean, onNavigate: (Screen) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            "Login + Server" to Screen.LOGIN,
            "Live Updates" to Screen.LIVE_UPDATES,
            "Timer" to Screen.TIMER
        ).forEach { (buttonName, destination) ->
            Button(
                enabled = destination != Screen.LOGIN || !isSigningIn,
                onClick = { onNavigate(destination) }
            ) {
                Text(buttonName)
            }
        }
    }
}
