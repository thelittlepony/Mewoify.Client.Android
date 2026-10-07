package com.tlpteam.basilservice.util

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.tlpteam.basilservice.MainActivity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationHelper {
    const val CHANNEL_ID = "mewoify_channel"
    const val FOREGROUND_SERVICE_NOTIFICATION_ID = 1001
    const val MESSAGE_NOTIFICATION_ID = 1002

    fun createNotificationChannel(context: Context) {
        val name = "Mewoify Gateway"
        val descriptionText = "Keeps WebSocket connection alive and notifies new messages"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun parseFileContentUrl(serverUrl: String, fileContent: JsonElement?): String? {
        val urls = parseFileContentUrls(serverUrl, fileContent)
        return urls.firstOrNull()
    }

    fun parseFileContentUrl(serverUrl: String, fileContent: String?): String? {
        val urls = parseFileContentUrls(serverUrl, fileContent)
        return urls.firstOrNull()
    }

    fun parseFileContentUrls(serverUrl: String, fileContent: String?): List<String> {
        if (fileContent.isNullOrBlank()) return emptyList()
        val raw = fileContent.trim()
        val paths = mutableListOf<String>()
        try {
            if (raw.startsWith("[")) {
                val list = Json.decodeFromString<List<String>>(raw)
                for (p in list) {
                    addPath(serverUrl, p, paths)
                }
            } else {
                addPath(serverUrl, raw, paths)
            }
        } catch (e: Exception) {
            addPath(serverUrl, raw, paths)
        }
        return paths
    }

    fun parseFileContentUrls(serverUrl: String, fileContent: JsonElement?): List<String> {
        if (fileContent == null || fileContent is JsonNull) return emptyList()
        val paths = mutableListOf<String>()
        try {
            when (fileContent) {
                is JsonArray -> {
                    for (element in fileContent) {
                        addPath(serverUrl, element.toString(), paths)
                    }
                }
                is JsonPrimitive -> {
                    val raw = fileContent.content.trim()
                    if (raw.startsWith("[")) {
                        val list = Json.decodeFromString<List<String>>(raw)
                        for (p in list) {
                            addPath(serverUrl, p, paths)
                        }
                    } else {
                        addPath(serverUrl, raw, paths)
                    }
                }
                else -> {}
            }
        } catch (e: Exception) {
            addPath(serverUrl, fileContent.toString(), paths)
        }
        return paths
    }

    private fun addPath(serverUrl: String, path: String, paths: MutableList<String>) {
        var clean = path.replace("\"", "").replace("'", "").trim()
        if (clean.startsWith("./")) clean = clean.substring(2)
        if (clean.isNotBlank()) {
            val url = if (clean.startsWith("http")) clean else "${serverUrl.trimEnd('/')}/${clean.trimStart('/')}"
            paths.add(url)
        }
    }

    fun showMessageNotification(
        context: Context,
        title: String,
        message: String,
        imageUrl: String? = null,
        channelId: String? = null
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (channelId != null) {
                putExtra("channel_id", channelId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (!imageUrl.isNullOrBlank()) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                if (bitmap != null) {
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null as Bitmap?)
                    )
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(MESSAGE_NOTIFICATION_ID + System.currentTimeMillis().toInt(), builder.build())
    }

    fun createForegroundServiceNotification(context: Context): Notification {
        createNotificationChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle("Mewoify")
            .setContentText("Connected to gateway and listening...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun formatServerTimestamp(createdAt: String): String {
        if (createdAt.isBlank()) return ""
        return try {
            val parseStr = if (!createdAt.endsWith("Z") && !createdAt.contains("+")) {
                "${createdAt}Z"
            } else {
                createdAt
            }
            val instant = try {
                Instant.parse(parseStr)
            } catch (e: Exception) {
                val ldt = LocalDateTime.parse(createdAt.take(19))
                ldt.atZone(ZoneId.of("UTC")).toInstant()
            }
            val zdt = instant.atZone(ZoneId.systemDefault())
            zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } catch (e: Exception) {
            createdAt.take(16).replace("T", " ")
        }
    }
}
