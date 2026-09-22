package com.example.cpen321application

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal suspend fun fetchBackendJson(
    apiBaseUrl: String,
    path: String
): JSONObject = withContext(Dispatchers.IO) {
    val url = URL(
        "${apiBaseUrl.trimEnd('/')}/${path.trimStart('/')}"
    )

    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 10_000
        instanceFollowRedirects = false
    }

    try {
        val code = connection.responseCode

        if (code != HttpURLConnection.HTTP_OK) {
            error("GET $path failed (HTTP $code)")
        }

        val body = connection.inputStream
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        JSONObject(body)
    } finally {
        connection.disconnect()
    }
}

internal suspend fun fetchHealthStatus(apiBaseUrl: String): String = withContext(Dispatchers.IO) {
    val healthUrl = "${apiBaseUrl.trimEnd('/')}/health"
    try {
        val connection = (URL(healthUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
        }

        when (val code = connection.responseCode) {
            HttpURLConnection.HTTP_OK -> {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                "Backend healthy ($healthUrl): $body"
            }
            else -> {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                "Backend error ($healthUrl): HTTP $code${errorBody?.let { " — $it" } ?: ""}"
            }
        }
    } catch (e: Exception) {
        "Backend unreachable ($healthUrl): ${e.message ?: e.javaClass.simpleName}"
    }
}
