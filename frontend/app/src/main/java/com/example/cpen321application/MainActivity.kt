package com.example.cpen321application

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.cpen321application.ui.theme.CPEN321ApplicationTheme
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

// Button 2
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.toMutableStateList
import android.graphics.Color as AndroidColor
import org.json.JSONException
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

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

private val pixelHttpClient = OkHttpClient()

@Composable
fun Greeting(apiBaseUrl: String, modifier: Modifier = Modifier) {
    var statusText by remember { mutableStateOf("Checking backend at $apiBaseUrl/health...") }

    LaunchedEffect(apiBaseUrl) {
        statusText = fetchHealthStatus(apiBaseUrl)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isSigningIn by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf("") }

    var googleCredential by remember { mutableStateOf<GoogleIdTokenCredential?>(null) }
    // Timer helpers
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var remainingSeconds by remember { mutableStateOf(0) }
    var minutesInput by remember { mutableStateOf("0") }
    var secondsInput by remember { mutableStateOf("5") }
    var isTimerRunning by remember { mutableStateOf(false) }
    var timerError by remember { mutableStateOf("") }
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            while (remainingSeconds > 0){
                delay(1000L.milliseconds)
                remainingSeconds -= 1
            }

            isTimerRunning = false
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = statusText)

        when (currentScreen) {
            Screen.HOME -> {
                listOf(
                    "Login + Server" to Screen.LOGIN,
                    "Live Updates" to Screen.LIVE_UPDATES,
                    "Timer" to Screen.TIMER
                ).forEach { (buttonName, targetScreen) ->
                    Button(
                        enabled = targetScreen != Screen.LOGIN || !isSigningIn,
                        onClick = {
                            currentScreen = targetScreen

                            if (targetScreen == Screen.LOGIN && !isSigningIn) {
                                isSigningIn = true
                                loginMessage = "Opening Google sign-in..."
                                googleCredential = null

                                scope.launch {
                                    try {
                                        val credential = requestGoogleSignIn(context)
                                        googleCredential = credential
                                        loginMessage = "Verifying with backend..."

                                        val user = verifyGoogleWithBackend(
                                            apiBaseUrl = apiBaseUrl,
                                            idToken = credential.idToken
                                        )

                                        val firstName =
                                            if (user.isNull("firstName")) "Not provided"
                                            else user.getString("firstName")

                                        val lastName =
                                            if (user.isNull("lastName")) "Not provided"
                                            else user.getString("lastName")

//                                        loginMessage =
//                                            "Backend verified.\nFirst name: $firstName\nLast name: $lastName"
                                        loginMessage = "Backend verified. Loading developer info..."

                                        val developer = fetchBackendJson(
                                            apiBaseUrl = apiBaseUrl,
                                            path = "/developer/name"
                                        )

                                        val serverTimeResponse = fetchBackendJson(
                                            apiBaseUrl = apiBaseUrl,
                                            path = "/server/time"
                                        )

                                        val developerFirstName = developer.getString("firstName")
                                        val developerLastName = developer.getString("lastName")
                                        val serverTime = serverTimeResponse.getString("time")
                                        val clientTime = getClientLocalTime()
                                        val clientIp = getClientIpAddress(context) ?: "Unavailable"

                                        loginMessage =
                                            "Backend verified.\n" +
                                                    "Client IP: $clientIp\n" +
                                                    "Server local time: $serverTime\n" +
                                                    "Client local time: $clientTime\n" +
                                                    "Developer: $developerFirstName $developerLastName\n" +
                                                    "Signed-in user: $firstName $lastName\n"
                                    } catch (e: GetCredentialCancellationException) {
                                        loginMessage = "Sign-in cancelled."
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        loginMessage =
                                            "Sign-in failed: ${e.message ?: "Unknown error"}"
                                    } finally {
                                        isSigningIn = false
                                    }
                                }
                            }
                        }
                    ) {
                        Text(text = buttonName)
                    }
                }
            }

            Screen.LOGIN -> {
                Text(text = "Login + Server")
                Text(text = loginMessage)

                googleCredential?.let { credential ->
                    Text(
                        text = "Google account: " +
                                (credential.displayName ?: credential.id)
                    )
                }
            }

            Screen.LIVE_UPDATES -> {
                val pixels = remember {
                    List(16 * 16) { Color.White }.toMutableStateList()
                }

                var pixelMessage by remember {
                    mutableStateOf("Connecting...")
                }

                val socketUrl = apiBaseUrl
                    .trimEnd('/')
                    .replaceFirst("https://", "wss://")
                    .replaceFirst("http://", "ws://") + "/pixels"

                DisposableEffect(socketUrl) {
                    val updateScope = CoroutineScope(
                        SupervisorJob() + Dispatchers.Main.immediate
                    )

                    pixelMessage = "Connecting..."

                    val request = Request.Builder()
                        .url(socketUrl)
                        .build()

                    val listener = object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response
                        ) {
                            updateScope.launch {
                                pixelMessage =
                                    "Backend connected. Waiting for pixels..."
                            }
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String
                        ) {
                            updateScope.launch {
                                val updated = applyPixelUpdate(text, pixels)

                                pixelMessage =
                                    if (updated) "Receiving pixel updates"
                                    else "Invalid pixel message"
                            }
                        }

                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String
                        ) {
                            webSocket.close(code, reason)
                        }

                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String
                        ) {
                            updateScope.launch {
                                pixelMessage = "Disconnected ($code): $reason"
                            }
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?
                        ) {
                            updateScope.launch {
                                pixelMessage =
                                    "Connection failed: ${t.message ?: "Unknown error"}"
                            }
                        }
                    }

                    val webSocket = pixelHttpClient.newWebSocket(
                        request,
                        listener
                    )

                    onDispose {
                        updateScope.cancel()
                        webSocket.close(1000, "Leaving pixel screen")
                    }
                }

                Text(text = "Pixel Art")
                PixelArtGrid(pixels = pixels)
                Text(text = pixelMessage)
            }

            Screen.TIMER -> {
                Text(text = "Timer page")
                OutlinedTextField(
                    value = minutesInput,
                    onValueChange = { newText ->
                        minutesInput = newText
                    },
                    label = { Text("Minutes") },
                    singleLine = true,
                    enabled = !isTimerRunning
                )

                OutlinedTextField(
                    value = secondsInput,
                    onValueChange = { newText ->
                        secondsInput = newText
                    },
                    label = { Text("Seconds") },
                    singleLine = true,
                    enabled = !isTimerRunning
                )

                Text(text = "Remaining time: $remainingSeconds seconds")

                Button(
                    enabled = !isTimerRunning,
                    onClick = {
                        val minutes = minutesInput.toIntOrNull()
                        val seconds = secondsInput.toIntOrNull()

                        if (
                            minutes == null || seconds == null ||
                            minutes < 0 || seconds !in 0..59
                        ) {
                            timerError = "Please enter a non-negative integer for the minutes and an integer between 0 and 59 for the seconds."
                        } else {
                            val totalSeconds = minutes.toLong() * 60 + seconds

                            if (totalSeconds == 0L) {
                                timerError = "The duration must be greater than 0."
                            } else if (totalSeconds > Int.MAX_VALUE.toLong()) {
                                timerError = "The duration is too long. Please input smaller values."
                            } else {
                                timerError = ""
                                remainingSeconds = totalSeconds.toInt()
                                isTimerRunning = true
                            }
                        }
                    }
                ) {
                    Text(text = "Start timer")
                }
            }
        }
        if (currentScreen != Screen.HOME) {
            Button(
                onClick = {
                    currentScreen = Screen.HOME
                }
            ) {
                Text(text = "Return to main menu")
            }
        }
    }
}

@Composable
fun PixelArtGrid( pixels: List<Color>, modifier: Modifier = Modifier ) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(1.dp, Color.Gray)
    ) {
        drawRect(color = Color.White)

        val cellWidth = size.width / 16
        val cellHeight = size.height / 16
        val lineWidth = 0.5.dp.toPx()

        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val index = y * 16 + x

                drawRect(
                    color = pixels[index],
                    topLeft = Offset(
                        x = x * cellWidth,
                        y = y * cellHeight
                    ),
                    size = Size(cellWidth, cellHeight)
                )
            }
        }

        for (index in 1 until 16) {
            // columns
            val x = index * cellWidth
            drawLine(
                color = Color.LightGray,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = lineWidth
            )

            // rows
            val y = index * cellHeight
            drawLine(
                color = Color.LightGray,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = lineWidth
            )
        }
    }
}

private fun applyPixelUpdate(
    message: String,
    pixels: MutableList<Color>
): Boolean {
    return try {
        val data = JSONObject(message)
        val x = data.get("x")
        val y = data.get("y")

        if (x !is Int || y !is Int) {
            return false
        }

        if (x !in 0 until 16 || y !in 0 until 16) {
            return false
        }

        val colorText = data.getString("color")
        val color = Color(AndroidColor.parseColor(colorText))

        pixels[y * 16 + x] = color
        true
    } catch (_: JSONException) {
        false
    } catch (_: IllegalArgumentException) {
        false
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

private suspend fun fetchBackendJson(
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

enum class Screen {
    HOME,
    LOGIN,
    LIVE_UPDATES,
    TIMER
}

private fun getClickMessage(buttonName: String): String {
    return "You clicked $buttonName!"
}

private suspend fun fetchHealthStatus(apiBaseUrl: String): String = withContext(Dispatchers.IO) {
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