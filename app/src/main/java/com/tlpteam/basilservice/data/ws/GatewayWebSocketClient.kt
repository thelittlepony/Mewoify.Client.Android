package com.tlpteam.basilservice.data.ws

import android.util.Log
import com.tlpteam.basilservice.data.model.HiClientData
import com.tlpteam.basilservice.data.model.WsPacket
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.*
import java.util.concurrent.atomic.AtomicInteger

class GatewayWebSocketClient(
    private val serverUrl: String,
    private val authToken: String,
    private val onEventReceived: (String, JsonObject) -> Unit,
    private val onAuthRejected: () -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit
) {
    private val TAG = "GatewayWS"
    private val client = OkHttpClient.Builder().build()
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var shouldReconnect = true
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val sequenceNumber = AtomicInteger(0)

    val eventCapabilities = listOf(
        "message.create",
        "message.update",
        "message.delete",
        "message.attachment.create",
        "guild.create",
        "guild.update",
        "guild.delete",
        "guild_channel.create",
        "guild_channel.update",
        "guild_channel.delete",
        "guild.member.join",
        "guild.member.leave",
        "dm.create",
        "dm.update",
        "dm.delete",
        "presence.update"
    )

    fun connect() {
        shouldReconnect = true
        doConnect()
    }

    private fun doConnect() {
        if (!shouldReconnect) return
        var cleanUrl = serverUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://") && !cleanUrl.startsWith("ws://") && !cleanUrl.startsWith("wss://")) {
            cleanUrl = "http://$cleanUrl"
        }
        val wsBase = cleanUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val wsUrl = if (wsBase.endsWith("/")) "${wsBase}gateway" else "$wsBase/gateway"

        val request = Request.Builder().url(wsUrl).build()
        Log.d(TAG, "Connecting to WebSocket at $wsUrl")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                onConnectionStateChanged(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received WS message: $text")
                try {
                    val packet = json.decodeFromString<WsPacket>(text)
                    if (packet.s != null) {
                        sequenceNumber.set(packet.s)
                    }
                    handleIncomingPacket(webSocket, packet)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse WS packet", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                cleanup()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                cleanup()
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000L)
            Log.d(TAG, "Attempting WebSocket reconnection...")
            doConnect()
        }
    }

    private fun handleIncomingPacket(ws: WebSocket, packet: WsPacket) {
        val dataObj = packet.d as? JsonObject ?: JsonObject(emptyMap())
        when (packet.t) {
            "hi.server" -> {
                Log.d(TAG, "Received hi.server, sending hi.client")
                val hiClientData = HiClientData(
                    auth_token = authToken,
                    client_event_capabilities = eventCapabilities
                )
                val hiPacket = WsPacket(
                    t = "hi.client",
                    d = json.encodeToJsonElement(HiClientData.serializer(), hiClientData)
                )
                ws.send(json.encodeToString(hiPacket))
            }
            "ready.server" -> {
                Log.d(TAG, "Received ready.server, sending ready.client")
                val readyPacket = WsPacket(t = "ready.client")
                ws.send(json.encodeToString(readyPacket))
                startHeartbeat(ws)
            }
            "rejected.client.auth_wrong_token" -> {
                Log.e(TAG, "Auth token rejected by server!")
                onAuthRejected()
                disconnect()
            }
            "heartbeat.server" -> {
                // Acknowledge or respond if needed
            }
            else -> {
                onEventReceived(packet.t, dataObj)
            }
        }
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(10000L)
                val hbPacket = WsPacket(
                    t = "heartbeat.client",
                    s = sequenceNumber.get()
                )
                val sent = ws.send(json.encodeToString(hbPacket))
                Log.d(TAG, "Sent heartbeat.client: $sent")
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        cleanup()
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }

    private fun cleanup() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        scope.coroutineContext[Job]?.cancelChildren()
        onConnectionStateChanged(false)
    }
}
