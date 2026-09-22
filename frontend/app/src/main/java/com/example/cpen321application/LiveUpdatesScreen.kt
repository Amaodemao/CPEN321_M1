package com.example.cpen321application

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONException
import org.json.JSONObject
import android.graphics.Color as AndroidColor

private val pixelHttpClient = OkHttpClient()

@Composable
internal fun LiveUpdatesScreen(apiBaseUrl: String) {
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Pixel Art")
        PixelArtGrid(pixels = pixels)
        Text(text = pixelMessage)
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
