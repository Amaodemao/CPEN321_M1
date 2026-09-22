package com.example.cpen321application

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun LoginScreen(message: String, accountName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Login + Server")
        Text(message)
        accountName?.let { Text("Google account: $it") }
    }
}

internal class LoginState {
    var isSigningIn by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    private var credential by mutableStateOf<GoogleIdTokenCredential?>(null)

    val accountName: String?
        get() = credential?.let { it.displayName ?: it.id }

    suspend fun signIn(context: Context, apiBaseUrl: String) {
        if (isSigningIn) return
        isSigningIn = true
        message = "Opening Google sign-in..."
        credential = null

        try {
            val receivedCredential = requestGoogleSignIn(context)
            credential = receivedCredential
            message = "Verifying with backend..."

            val user = verifyGoogleWithBackend(
                apiBaseUrl = apiBaseUrl,
                idToken = receivedCredential.idToken
            )

            val firstName =
                if (user.isNull("firstName")) "Not provided"
                else user.getString("firstName")

            val lastName =
                if (user.isNull("lastName")) "Not provided"
                else user.getString("lastName")

            message = "Backend verified. Loading developer info..."

            val developer = fetchBackendJson(
                apiBaseUrl = apiBaseUrl,
                path = "/developer/name"
            )

            val serverIpResponse = fetchBackendJson(
                apiBaseUrl = apiBaseUrl,
                path = "/server/ip"
            )

            val serverIp = serverIpResponse.getString("ip")

            val serverTimeResponse = fetchBackendJson(
                apiBaseUrl = apiBaseUrl,
                path = "/server/time"
            )

            val developerFirstName = developer.getString("firstName")
            val developerLastName = developer.getString("lastName")
            val serverTime = serverTimeResponse.getString("time")
            val clientTime = getClientLocalTime()
            val clientIp = getClientIpAddress(context) ?: "Unavailable"

            message =
                "Backend verified.\n" +
                        "Server public IP: $serverIp\n" +
                        "Client IP: $clientIp\n" +
                        "Server local time: $serverTime\n" +
                        "Client local time: $clientTime\n" +
                        "Developer: $developerFirstName $developerLastName\n" +
                        "Signed-in user: $firstName $lastName\n"
        } catch (e: GetCredentialCancellationException) {
            message = "Sign-in cancelled."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message =
                "Sign-in failed: ${e.message ?: "Unknown error"}"
        } finally {
            isSigningIn = false
        }
    }
}

private fun getClientIpAddress(context: Context): String? {
    val manager = context.getSystemService(
        ConnectivityManager::class.java
    ) ?: return null

    val network = manager.activeNetwork ?: return null
    val properties = manager.getLinkProperties(network) ?: return null

    val addresses = properties.linkAddresses
        .map { it.address }
        .filter {
            !it.isLoopbackAddress &&
                    !it.isLinkLocalAddress &&
                    !it.isAnyLocalAddress
        }

    val selectedAddress =
        addresses.firstOrNull { it is Inet4Address }
            ?: addresses.firstOrNull()

    return selectedAddress?.hostAddress
}

private fun getClientLocalTime(): String {
    val formatter = DateTimeFormatter.ofPattern(
        "HH:mm:ss 'GMT'xxx",
        Locale.ROOT
    )

    return ZonedDateTime.now().format(formatter)
}

private suspend fun verifyGoogleWithBackend(
    apiBaseUrl: String,
    idToken: String
): JSONObject = withContext(Dispatchers.IO) {
    val url = URL("${apiBaseUrl.trimEnd('/')}/auth/google")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 10_000
        doOutput = true
        instanceFollowRedirects = false
        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
    }

    try {
        val requestBody = JSONObject()
            .put("idToken", idToken)
            .toString()

        connection.outputStream
            .bufferedWriter(Charsets.UTF_8)
            .use { writer ->
                writer.write(requestBody)
            }

        val code = connection.responseCode

        if (code != HttpURLConnection.HTTP_OK) {
            error("Backend verification failed (HTTP $code)")
        }

        val responseBody = connection.inputStream
            .bufferedReader(Charsets.UTF_8)
            .use { reader ->
                reader.readText()
            }

        JSONObject(responseBody).getJSONObject("user")
    } finally {
        connection.disconnect()
    }
}

private suspend fun requestGoogleSignIn (
    context: Context
): GoogleIdTokenCredential {
    val googleOption = GetSignInWithGoogleOption.Builder(
        serverClientId = BuildConfig.GOOGLE_CLIENT_ID
    ).build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleOption)
        .build()

    val credentialManager = CredentialManager.create(context)

    val result = credentialManager.getCredential(
        context = context,
        request = request
    )

    val credential = result.credential

    if (
        credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return GoogleIdTokenCredential.createFrom(credential.data)
    }

    error("Unexpected credential type")
}
