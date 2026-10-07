package com.tlpteam.basilservice.data.api

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.tlpteam.basilservice.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

class BasilApiClient(baseUrl: String) {
    private val TAG = "BasilApiClient"
    var baseUrl: String = formatBaseUrl(baseUrl)
        set(value) {
            field = formatBaseUrl(value)
        }

    private fun formatBaseUrl(url: String): String {
        val trimmed = url.trim()
        val withScheme = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) "http://$trimmed" else trimmed
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .proxy(Proxy.NO_PROXY)
        .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS))
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private inline fun <reified T> parseResponse(response: Response): T {
        val bodyStr = response.body?.string() ?: throw IOException("Empty response body")
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: $bodyStr")
        }
        return json.decodeFromString<T>(bodyStr)
    }

    private fun parseEmptyResponse(response: Response) {
        if (!response.isSuccessful) {
            val bodyStr = response.body?.string() ?: ""
            throw IOException("HTTP ${response.code}: $bodyStr")
        }
    }

    suspend fun register(request: RegisterRequest): AuthResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}auth/register"
        val body = json.encodeToString(request).toRequestBody(mediaType)
        val req = Request.Builder().url(url).post(body).build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun login(request: LoginRequest): AuthResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}auth/login"
        val body = json.encodeToString(request).toRequestBody(mediaType)
        val req = Request.Builder().url(url).post(body).build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun getMe(authHeader: String): UserResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/users/me"
        val req = Request.Builder().url(url).header("Authorization", authHeader).get().build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun updateMe(authHeader: String, request: UserUpdateRequest): UserResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/users/me"
        val body = json.encodeToString(request).toRequestBody(mediaType)
        val req = Request.Builder().url(url).header("Authorization", authHeader).patch(body).build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun getUserById(authHeader: String, userId: String): PublicUserResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/users/$userId"
        val req = Request.Builder().url(url).header("Authorization", authHeader).get().build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun createGuild(authHeader: String, request: GuildCreateRequest): GuildResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/create"
        val body = json.encodeToString(request).toRequestBody(mediaType)
        val req = Request.Builder().url(url).header("Authorization", authHeader).post(body).build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun getGuild(authHeader: String, guildId: String): GuildResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId"
        val req = Request.Builder().url(url).header("Authorization", authHeader).get().build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun updateGuild(authHeader: String, guildId: String, request: GuildUpdateRequest): GuildResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId"
        val body = json.encodeToString(request).toRequestBody(mediaType)
        val req = Request.Builder().url(url).header("Authorization", authHeader).patch(body).build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun deleteGuild(authHeader: String, guildId: String) = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId"
        val req = Request.Builder().url(url).header("Authorization", authHeader).delete().build()
        client.newCall(req).execute().use { parseEmptyResponse(it) }
    }

    suspend fun leaveGuild(authHeader: String, guildId: String) = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId/leave"
        val req = Request.Builder().url(url).header("Authorization", authHeader).delete().build()
        client.newCall(req).execute().use { parseEmptyResponse(it) }
    }

    suspend fun createChannel(authHeader: String, guildId: String, request: ChannelCreateRequest): ChannelResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId/channel-create"
        val body = json.encodeToString(request).toRequestBody(mediaType)
        val req = Request.Builder().url(url).header("Authorization", authHeader).post(body).build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun getChannels(authHeader: String, guildId: String): List<ChannelResponse> = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId/channels"
        val req = Request.Builder().url(url).header("Authorization", authHeader).get().build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun updateChannel(authHeader: String, guildId: String, channelId: String, request: ChannelUpdateRequest): ChannelResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId/channel/$channelId"
        val body = json.encodeToString(request).toRequestBody(mediaType)
        val req = Request.Builder().url(url).header("Authorization", authHeader).patch(body).build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun getGuildMembers(authHeader: String, guildId: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId/members"
        val req = Request.Builder().url(url).header("Authorization", authHeader).get().build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun deleteChannel(authHeader: String, guildId: String, channelId: String) = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId/channel/$channelId"
        val req = Request.Builder().url(url).header("Authorization", authHeader).delete().build()
        client.newCall(req).execute().use { parseEmptyResponse(it) }
    }

    suspend fun createInvite(authHeader: String, guildId: String, request: InviteCreateRequest? = null): InviteResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId/invite"
        val body = if (request != null) json.encodeToString(request).toRequestBody(mediaType) else "".toRequestBody(mediaType)
        val req = Request.Builder().url(url).header("Authorization", authHeader).post(body).build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun listInvites(authHeader: String, guildId: String): List<InviteResponse> = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId/invites"
        val req = Request.Builder().url(url).header("Authorization", authHeader).get().build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun deleteInvite(authHeader: String, guildId: String, inviteCode: String) = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/$guildId/invite/$inviteCode"
        val req = Request.Builder().url(url).header("Authorization", authHeader).delete().build()
        client.newCall(req).execute().use { parseEmptyResponse(it) }
    }

    suspend fun joinGuildByInvite(authHeader: String, inviteCode: String) = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/guild/join-invite/$inviteCode"
        val req = Request.Builder().url(url).header("Authorization", authHeader).post("".toRequestBody(mediaType)).build()
        client.newCall(req).execute().use { parseEmptyResponse(it) }
    }

    suspend fun createDm(authHeader: String, userId: String): DmResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/users/$userId/dm/create"
        val req = Request.Builder().url(url).header("Authorization", authHeader).post("".toRequestBody(mediaType)).build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun getDmList(authHeader: String): List<DmResponse> = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/users/me/dm-list"
        val req = Request.Builder().url(url).header("Authorization", authHeader).get().build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun sendMessage(authHeader: String, channelId: String, request: MessageCreateRequest): MessageResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/channel/$channelId/message-create"
        val body = json.encodeToString(request).toRequestBody(mediaType)
        val req = Request.Builder().url(url).header("Authorization", authHeader).post(body).build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun getMessages(authHeader: String, channelId: String, limit: Int = 50, offset: Int = 0): List<MessageResponse> = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/channel/$channelId/messages?limit=$limit&offset=$offset"
        val req = Request.Builder().url(url).header("Authorization", authHeader).get().build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun updateMessage(authHeader: String, messageId: String, request: MessageUpdateRequest): MessageResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/message/$messageId"
        val body = json.encodeToString(request).toRequestBody(mediaType)
        val req = Request.Builder().url(url).header("Authorization", authHeader).patch(body).build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun deleteMessage(authHeader: String, messageId: String) = withContext(Dispatchers.IO) {
        val url = "${baseUrl}api/message/$messageId"
        val req = Request.Builder().url(url).header("Authorization", authHeader).delete().build()
        client.newCall(req).execute().use { parseEmptyResponse(it) }
    }

    suspend fun attachImages(authHeader: String, context: Context, messageId: String, uri: Uri) = withContext(Dispatchers.IO) {
        attachFiles(authHeader, context, messageId, listOf(uri))
    }

    suspend fun attachFiles(authHeader: String, context: Context, messageId: String, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

        for (uri in uris) {
            var fileName = "attachment"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex) ?: "attachment"
                }
            }
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

            val inputStream = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open URI: $uri")
            val bytes = inputStream.readBytes()
            inputStream.close()

            val requestBody = bytes.toRequestBody(mimeType.toMediaType())
            multipartBuilder.addFormDataPart("files", fileName, requestBody)
        }

        val multipartBody = multipartBuilder.build()
        val url = "${baseUrl}api/message/$messageId/attachments"
        val req = Request.Builder().url(url).header("Authorization", authHeader).post(multipartBody).build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                throw IOException("Attach failed HTTP ${response.code}: $bodyStr")
            }
        }
    }

    suspend fun getAboutBackend(): AboutBackendResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl}public-api/about-backend"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { parseResponse(it) }
    }

    suspend fun uploadProfilePicture(authHeader: String, context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open URI")
        val bytes = inputStream.readBytes()
        inputStream.close()

        val requestBody = bytes.toRequestBody("image/*".toMediaType())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "avatar.avif", requestBody)
            .build()

        val url = "${baseUrl}api/users/me/profile-picture"
        val req = Request.Builder().url(url).header("Authorization", authHeader).post(multipartBody).build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                throw IOException("Upload avatar failed HTTP ${response.code}: $bodyStr")
            }
        }
    }

    suspend fun uploadGuildPicture(authHeader: String, context: Context, guildId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open URI")
        val bytes = inputStream.readBytes()
        inputStream.close()

        val requestBody = bytes.toRequestBody("image/*".toMediaType())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "guild.avif", requestBody)
            .build()

        val url = "${baseUrl}api/guild/$guildId/guild-picture"
        val req = Request.Builder().url(url).header("Authorization", authHeader).post(multipartBody).build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                throw IOException("Upload guild picture failed HTTP ${response.code}: $bodyStr")
            }
        }
    }
}
