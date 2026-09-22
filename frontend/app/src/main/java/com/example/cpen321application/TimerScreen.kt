package com.example.cpen321application

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun TimerScreen(state: TimerState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Timer page")
        OutlinedTextField(
            value = state.minutesInput,
            onValueChange = { state.minutesInput = it },
            label = { Text("Minutes") },
            singleLine = true,
            enabled = !state.isRunning
        )
        OutlinedTextField(
            value = state.secondsInput,
            onValueChange = { state.secondsInput = it },
            label = { Text("Seconds") },
            singleLine = true,
            enabled = !state.isRunning
        )
        Text("Remaining time: ${state.remainingSeconds} seconds")
        Button(enabled = !state.isRunning, onClick = state::start) {
            Text("Start timer")
        }
        if (state.errorMessage.isNotEmpty()) {
            Text(state.errorMessage)
        }
    }
}

// Called by the navigation host, not by TimerScreen: leaving the page keeps
// the same state and countdown coroutine alive.
@Composable
internal fun rememberTimerState(): TimerState {
    val state = remember { TimerState() }
    LaunchedEffect(state.isRunning) {
        state.runCountdown()
    }
    return state
}

internal class TimerState {
    var minutesInput by mutableStateOf("0")
    var secondsInput by mutableStateOf("5")
    var remainingSeconds by mutableStateOf(0)
        private set
    var isRunning by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf("")
        private set
    var showTriviaDialog by mutableStateOf(false)
        private set

    fun start() {
        if (isRunning) return
        val minutes = minutesInput.toIntOrNull()
        val seconds = secondsInput.toIntOrNull()
        if (minutes == null || seconds == null || minutes < 0 || seconds !in 0..59) {
            errorMessage = "Please enter a non-negative integer for the minutes and an integer between 0 and 59 for the seconds."
            return
        }

        val totalSeconds = minutes.toLong() * 60 + seconds
        if (totalSeconds == 0L) {
            errorMessage = "The duration must be greater than 0."
        } else if (totalSeconds > Int.MAX_VALUE.toLong()) {
            errorMessage = "The duration is too long. Please input smaller values."
        } else {
            errorMessage = ""
            remainingSeconds = totalSeconds.toInt()
            isRunning = true
        }
    }

    suspend fun runCountdown() {
        if (!isRunning) return
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        }
        isRunning = false
        showTriviaDialog = true
    }

    fun dismissTrivia() {
        showTriviaDialog = false
    }
}
