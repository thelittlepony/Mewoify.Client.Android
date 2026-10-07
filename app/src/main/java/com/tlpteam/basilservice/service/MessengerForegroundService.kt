package com.tlpteam.basilservice.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.tlpteam.basilservice.data.api.BasilApiClient
import com.tlpteam.basilservice.data.model.MessageResponse
import com.tlpteam.basilservice.data.ws.GatewayWebSocketClient
import com.tlpteam.basilservice.util.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class MessengerForegroundService : Service() {
    private val TAG = "MessengerFGS"
    private var webSocketClient: GatewayWebSocketClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var currentUserId: String? = null
    private var authToken: String? = null
    private var serverUrl: String = "https://mewoify.tlpdev.ru"
    private lateinit var api: BasilApiClient
    private val userCache = mutableMapOf<String, String>() // userId -> name
    private val channelNameCache = mutableMapOf<String, String>() // channelId -> channelName

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serverUrl = intent?.getStringExtra("server_url") ?: "https://mewoify.tlpdev.ru"
        authToken = intent?.getStringExtra("auth_token")

        if (authToken != null) {
            api = BasilApiClient(serverUrl)
            startForeground(
                NotificationHelper.FOREGROUND_SERVICE_NOTIFICATION_ID,
                NotificationHelper.createForegroundServiceNotification(this)
            )
            cacheUserDataAndChannels(authToken!!)
            connectWs(serverUrl, authToken!!)
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    private fun cacheUserDataAndChannels(token: String) {
        scope.launch {
            try {
                val user = api.getMe("Bearer $token")
                currentUserId = user.id
                userCache[user.id] = user.displayName ?: user.username

                user.guilds.forEach { guild ->
                    try {
                        val channels = api.getChannels("Bearer $token", guild.id)
                        channels.forEach { ch ->
                            channelNameCache[ch.id] = ch.name
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache user data and channels in FGS", e)
            }
        }
    }

    private fun resolveAuthorName(token: String, userId: String, onResolved: (String) -> Unit) {
        if (userCache.containsKey(userId)) {
            onResolved(userCache[userId]!!)
            return
        }
        scope.launch {
            try {
                val user = api.getUserById("Bearer $token", userId)
                val name = user.displayName ?: user.username
                userCache[userId] = name
                onResolved(name)
            } catch (e: Exception) {
                onResolved(userId.take(6))
            }
        }
    }

    private fun connectWs(serverUrl: String, token: String) {
        webSocketClient?.disconnect()
        webSocketClient = GatewayWebSocketClient(
            serverUrl = serverUrl,
            authToken = token,
            onEventReceived = { eventType, jsonObject ->
                if (eventType == "message.create") {
                    try {
                        val msg = json.decodeFromJsonElement<MessageResponse>(jsonObject)
                        // Skip notification if message is sent by current user
                        if (currentUserId != null && msg.userId == currentUserId) {
                            return@GatewayWebSocketClient
                        }

                        resolveAuthorName(token, msg.userId) { authorName ->
                            val channelName = channelNameCache[msg.channelId]
                            val notificationTitle = if (channelName != null) {
                                "#$channelName > $authorName"
                            } else {
                                authorName
                            }
                            val imageUrl = NotificationHelper.parseFileContentUrl(serverUrl, msg.fileContent)

                            NotificationHelper.showMessageNotification(
                                this,
                                notificationTitle,
                                msg.textContent,
                                imageUrl,
                                msg.channelId
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse message for notification", e)
                    }
                } else if (eventType == "message.attachment.create") {
                    try {
                        val attachmentPath = jsonObject["attachment"]?.toString()?.replace("\"", "")
                        val userId = jsonObject["user_id"]?.toString()?.replace("\"", "")
                        val channelId = jsonObject["channel_id"]?.toString()?.replace("\"", "")

                        val imageUrl = NotificationHelper.parseFileContentUrl(serverUrl, attachmentPath)

                        if (userId != null) {
                            if (currentUserId != null && userId == currentUserId) {
                                return@GatewayWebSocketClient
                            }
                            resolveAuthorName(token, userId) { authorName ->
                                val channelName = if (channelId != null) channelNameCache[channelId] else null
                                val notificationTitle = if (channelName != null) {
                                    "#$channelName > $authorName"
                                } else {
                                    authorName
                                }
                                NotificationHelper.showMessageNotification(
                                    this,
                                    notificationTitle,
                                    "Sent an image attachment",
                                    imageUrl,
                                    channelId
                                )
                            }
                        } else {
                            NotificationHelper.showMessageNotification(
                                this,
                                "New Image Attachment",
                                "An image was attached to a message",
                                imageUrl,
                                channelId
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse attachment notification", e)
                    }
                }
            },
            onAuthRejected = {
                stopSelf()
            },
            onConnectionStateChanged = { connected ->
                Log.d(TAG, "Foreground WS connected: $connected")
            }
        )
        webSocketClient?.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient?.disconnect()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
