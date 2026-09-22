package com.example.cpen321application

import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun TriviaSurpriseDialog(
    onDismiss: () -> Unit
) {
    var trivia by remember {
        mutableStateOf<TriviaQuestion?>(null)
    }
    var selectedAnswer by remember {
        mutableStateOf<String?>(null)
    }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        isLoading = true
        errorMessage = null

        try {
            // 重试前等待，减少触发 API 限流的机会。
            if (attempt > 0) {
                delay(5000L)
            }

            trivia = fetchTriviaQuestion()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unable to load question."
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Time's up! Computer challenge")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(
                    rememberScrollState()
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val question = trivia
                val error = errorMessage

                when {
                    isLoading -> {
                        Text(
                            if (attempt == 0) "Loading your question..."
                            else "Waiting briefly, then retrying..."
                        )
                    }

                    error != null -> {
                        Text(error)

                        TextButton(
                            onClick = { attempt += 1 }
                        ) {
                            Text("Retry")
                        }
                    }

                    question != null -> {
                        Text(question.question)

                        question.options.forEach { option ->
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedAnswer == null,
                                onClick = {
                                    if (selectedAnswer == null) {
                                        selectedAnswer = option
                                    }
                                }
                            ) {
                                Text(option)
                            }
                        }

                        selectedAnswer?.let { answer ->
                            Text("Your answer: $answer")

                            if (answer == question.correctAnswer) {
                                Text(
                                    "Correct! Great job — " +
                                            "keep learning and stay curious!"
                                )
                            } else {
                                Text(
                                    "Not quite! The correct answer is:\n" +
                                            question.correctAnswer
                                )
                            }
                        }

                        Text("Questions: Open Trivia DB · CC BY-SA 4.0")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private suspend fun fetchTriviaQuestion(): TriviaQuestion {
    val data = fetchBackendJson(
        apiBaseUrl = "https://opentdb.com",
        path = "/api.php?amount=1&category=18&type=multiple&encode=base64"
    )

    val responseCode = data.getInt("response_code")

    if (responseCode == 5) {
        error("Too many requests. Please wait at least 5 seconds.")
    }

    if (responseCode != 0) {
        error("Unable to load a question (code $responseCode).")
    }

    val results = data.getJSONArray("results")

    if (results.length() == 0) {
        error("No question was returned.")
    }

    val item = results.getJSONObject(0)

    fun decode(value: String): String {
        return String(
            Base64.decode(value, Base64.DEFAULT),
            Charsets.UTF_8
        )
    }

    val question = decode(item.getString("question"))
    val correctAnswer = decode(item.getString("correct_answer"))
    val incorrectAnswers = item.getJSONArray("incorrect_answers")

    val options = mutableListOf(correctAnswer)

    for (index in 0 until incorrectAnswers.length()) {
        options.add(decode(incorrectAnswers.getString(index)))
    }

    return TriviaQuestion(
        question = question,
        options = options.shuffled(),
        correctAnswer = correctAnswer
    )
}

private data class TriviaQuestion(
    val question: String,
    val options: List<String>,
    val correctAnswer: String
)
