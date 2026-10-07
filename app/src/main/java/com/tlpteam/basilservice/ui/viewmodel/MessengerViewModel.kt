package com.tlpteam.basilservice.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tlpteam.basilservice.data.api.BasilApiClient
import com.tlpteam.basilservice.data.model.*
import com.tlpteam.basilservice.data.preferences.SavedAccount
import com.tlpteam.basilservice.data.preferences.UserPreferences
import com.tlpteam.basilservice.data.ws.GatewayWebSocketClient
import com.tlpteam.basilservice.service.MessengerForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

sealed interface AuthState {
    object LoggedOut : AuthState
    data class LoggedIn(val token: String) : AuthState
}

class MessengerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _serverUrl = MutableStateFlow(UserPreferences.DEFAULT_SERVER_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _savedAccounts = MutableStateFlow<List<SavedAccount>>(emptyList())
    val savedAccounts: StateFlow<List<SavedAccount>> = _savedAccounts.asStateFlow()

    private val _currentUser = MutableStateFlow<UserResponse?>(null)
    val currentUser: StateFlow<UserResponse?> = _currentUser.asStateFlow()

    private val _guilds = MutableStateFlow<List<GuildSummaryResponse>>(emptyList())
    val guilds: StateFlow<List<GuildSummaryResponse>> = _guilds.asStateFlow()

    private val _dmList = MutableStateFlow<List<DmResponse>>(emptyList())
    val dmList: StateFlow<List<DmResponse>> = _dmList.asStateFlow()

    private val _selectedGuildId = MutableStateFlow<String?>(null)
    val selectedGuildId: StateFlow<String?> = _selectedGuildId.asStateFlow()

    private val _channels = MutableStateFlow<List<ChannelResponse>>(emptyList())
    val channels: StateFlow<List<ChannelResponse>> = _channels.asStateFlow()

    private val _guildMembers = MutableStateFlow<List<String>>(emptyList())
    val guildMembers: StateFlow<List<String>> = _guildMembers.asStateFlow()

    private val _selectedChannelId = MutableStateFlow<String?>(null)
    val selectedChannelId: StateFlow<String?> = _selectedChannelId.asStateFlow()

    private val _selectedDmId = MutableStateFlow<String?>(null)
    val selectedDmId: StateFlow<String?> = _selectedDmId.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageResponse>>(emptyList())
    val messages: StateFlow<List<MessageResponse>> = _messages.asStateFlow()

    private val _userCache = MutableStateFlow<Map<String, PublicUserResponse>>(emptyMap())
    val userCache: StateFlow<Map<String, PublicUserResponse>> = _userCache.asStateFlow()

    private val _presenceMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val presenceMap: StateFlow<Map<String, Boolean>> = _presenceMap.asStateFlow()

    private val _selectedUserProfile = MutableStateFlow<PublicUserResponse?>(null)
    val selectedUserProfile: StateFlow<PublicUserResponse?> = _selectedUserProfile.asStateFlow()

    val inviteCodeResult = MutableStateFlow<InviteResponse?>(null)

    private val _guildPictureVersions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val guildPictureVersions: StateFlow<Map<String, Long>> = _guildPictureVersions.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var maxFileSize: Long? = null
        private set

    private var api: BasilApiClient = BasilApiClient(UserPreferences.DEFAULT_SERVER_URL)
    private var webSocketClient: GatewayWebSocketClient? = null

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return UserPreferences.DEFAULT_SERVER_URL
        return trimmed
    }

    init {
        viewModelScope.launch {
            prefs.serverUrlFlow.collect { url ->
                val norm = normalizeUrl(url)
                _serverUrl.value = norm
                api = BasilApiClient(norm)
                fetchAboutBackend()
            }
        }
        viewModelScope.launch {
            prefs.savedAccountsFlow.collect { accounts ->
                _savedAccounts.value = accounts
            }
        }
        viewModelScope.launch {
            prefs.authTokenFlow.collect { token ->
                _authToken.value = token
                if (token != null) {
                    _authState.value = AuthState.LoggedIn(token)
                    fetchUserData()
                    connectWebSocket(token)
                    startForegroundService(token)
                } else {
                    _authState.value = AuthState.LoggedOut
                    disconnectWebSocket()
                    stopForegroundService()
                    _currentUser.value = null
                    _guilds.value = emptyList()
                    _dmList.value = emptyList()
                }
            }
        }
    }

    private fun startForegroundService(token: String) {
        val context = getApplication<Application>()
        val intent = Intent(context, MessengerForegroundService::class.java).apply {
            putExtra("server_url", normalizeUrl(_serverUrl.value))
            putExtra("auth_token", token)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopForegroundService() {
        val context = getApplication<Application>()
        val intent = Intent(context, MessengerForegroundService::class.java)
        context.stopService(intent)
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            prefs.saveServerUrl(url.trim())
        }
    }

    fun login(loginStr: String, passStr: String) {
        viewModelScope.launch {
            try {
                val normalizedUrl = normalizeUrl(_serverUrl.value)
                val tempApi = BasilApiClient(normalizedUrl)
                val resp = tempApi.login(LoginRequest(loginStr, passStr))
                prefs.saveAuthToken(resp.token)
                val user = tempApi.getMe("Bearer ${resp.token}")
                prefs.addOrUpdateAccount(SavedAccount(user.username, resp.token, normalizedUrl))
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Login failed: ${e.localizedMessage}"
            }
        }
    }

    fun register(username: String, email: String, passStr: String) {
        viewModelScope.launch {
            try {
                val normalizedUrl = normalizeUrl(_serverUrl.value)
                val resp = api.register(RegisterRequest(username, email, passStr))
                prefs.saveAuthToken(resp.token)
                prefs.addOrUpdateAccount(SavedAccount(username, resp.token, normalizedUrl))
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Registration failed: ${e.localizedMessage}"
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            disconnectWebSocket()
            stopForegroundService()
            prefs.saveAuthToken(null)
        }
    }

    fun switchAccount(account: SavedAccount) {
        viewModelScope.launch {
            setServerUrl(account.serverUrl)
            prefs.saveAuthToken(account.token)
        }
    }

    fun removeAccount(account: SavedAccount) {
        viewModelScope.launch {
            prefs.removeAccount(account.username, account.serverUrl)
        }
    }

    fun uploadProfilePicture(context: Context, uri: Uri) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.uploadProfilePicture("Bearer $token", context, uri)
                fetchUserData()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to upload profile picture: ${e.localizedMessage}"
            }
        }
    }

    fun uploadGuildPicture(guildId: String, context: Context, uri: Uri) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.uploadGuildPicture("Bearer $token", context, guildId, uri)
                _guildPictureVersions.value = _guildPictureVersions.value + (guildId to System.currentTimeMillis())
                fetchUserData()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to upload guild picture: ${e.localizedMessage}"
            }
        }
    }

    fun updateProfile(displayName: String?, bio: String?) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                val updatedUser = api.updateMe("Bearer $token", UserUpdateRequest(displayName = displayName, bio = bio))
                _currentUser.value = updatedUser
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update profile: ${e.localizedMessage}"
            }
        }
    }

    fun fetchUserData() {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                val user = api.getMe("Bearer $token")
                _currentUser.value = user
                _guilds.value = user.guilds
                user.guilds.forEach { guild ->
                    ensureUserCached(guild.ownerId)
                }
                val dms = api.getDmList("Bearer $token")
                _dmList.value = dms
                _userCache.value = _userCache.value + (user.id to PublicUserResponse(user.id, user.username, user.displayName, user.bio, user.createdAt))
                dms.forEach { dm ->
                    val peerId = if (dm.userId1 == user.id) dm.userId2 else dm.userId1
                    ensureUserCached(peerId)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch user data: ${e.localizedMessage}"
            }
        }
    }

    private fun connectWebSocket(token: String) {
        disconnectWebSocket()
        webSocketClient = GatewayWebSocketClient(
            serverUrl = normalizeUrl(_serverUrl.value),
            authToken = token,
            onEventReceived = { eventType, jsonObject ->
                handleWsEvent(eventType, jsonObject)
            },
            onAuthRejected = {
                viewModelScope.launch {
                    _errorMessage.value = "Auth token rejected by gateway!"
                    logout()
                }
            },
            onConnectionStateChanged = { connected ->
                _isConnected.value = connected
            }
        )
        webSocketClient?.connect()
    }

    private fun disconnectWebSocket() {
        webSocketClient?.disconnect()
        webSocketClient = null
        _isConnected.value = false
    }

    private fun handleWsEvent(eventType: String, data: JsonObject) {
        viewModelScope.launch {
            val token = _authToken.value ?: return@launch
            when (eventType) {
                "message.create", "message.update", "message.delete", "message.attachment.create" -> {
                    try {
                        if (eventType != "message.attachment.create") {
                            val msg = json.decodeFromJsonElement<MessageResponse>(data)
                            ensureUserCached(msg.userId)
                        }
                        fetchMessagesForActiveChannel()
                    } catch (e: Exception) {
                        Log.e("VM", "Error parsing message event", e)
                    }
                }
                "guild.create", "guild.update", "guild.delete" -> {
                    fetchUserData()
                }
                "guild_channel.create", "guild_channel.update", "guild_channel.delete", "guild.member.join", "guild.member.leave" -> {
                    val guildId = _selectedGuildId.value
                    if (guildId != null) {
                        selectGuild(guildId)
                    }
                }
                "dm.create", "dm.update", "dm.delete" -> {
                    try {
                        val dms = api.getDmList("Bearer $token")
                        _dmList.value = dms
                        val currentUserId = _currentUser.value?.id
                        dms.forEach { dm ->
                            if (currentUserId != null) {
                                val peerId = if (dm.userId1 == currentUserId) dm.userId2 else dm.userId1
                                ensureUserCached(peerId)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VM", "Error refreshing DMs", e)
                    }
                }
                "presence.update" -> {
                    try {
                        val userId = data["user_id"]?.toString()?.replace("\"", "")
                        val isOnline = data["is_online"]?.toString()?.toBoolean() ?: false
                        if (userId != null) {
                            _presenceMap.value = _presenceMap.value + (userId to isOnline)
                        }
                    } catch (e: Exception) {
                        Log.e("VM", "Error parsing presence.update", e)
                    }
                }
            }
        }
    }

    fun selectGuild(guildId: String) {
        _selectedGuildId.value = guildId
        _selectedDmId.value = null
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                val list = api.getChannels("Bearer $token", guildId)
                _channels.value = list
                if (list.isNotEmpty() && _selectedChannelId.value !in list.map { it.id }) {
                    selectChannel(list.first().id)
                }

                val members = api.getGuildMembers("Bearer $token", guildId)
                val memberIds = members.mapNotNull { it["user_id"] }
                _guildMembers.value = memberIds
                memberIds.forEach { ensureUserCached(it) }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load guild data: ${e.localizedMessage}"
            }
        }
    }

    fun selectDm(dmId: String) {
        _selectedDmId.value = dmId
        _selectedGuildId.value = null
        _selectedChannelId.value = dmId
        _channels.value = emptyList()
        fetchMessagesForActiveChannel()
    }

    fun selectChannel(channelId: String) {
        _selectedChannelId.value = channelId
        fetchMessagesForActiveChannel()
    }

    private var hasMoreMessages = true
    private var isLoadingMore = false

    private fun fetchMessagesForActiveChannel() {
        val token = _authToken.value ?: return
        val channelId = _selectedChannelId.value ?: return
        hasMoreMessages = true
        viewModelScope.launch {
            try {
                val msgs = api.getMessages("Bearer $token", channelId, limit = 50, offset = 0)
                _messages.value = msgs
                msgs.forEach { ensureUserCached(it.userId) }
                hasMoreMessages = msgs.size >= 50
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load messages: ${e.localizedMessage}"
            }
        }
    }

    fun loadMoreMessages() {
        if (isLoadingMore || !hasMoreMessages) return
        val token = _authToken.value ?: return
        val channelId = _selectedChannelId.value ?: return
        val currentSize = _messages.value.size
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val olderMsgs = api.getMessages("Bearer $token", channelId, limit = 50, offset = currentSize)
                if (olderMsgs.isEmpty()) {
                    hasMoreMessages = false
                } else {
                    olderMsgs.forEach { ensureUserCached(it.userId) }
                    _messages.value = olderMsgs + _messages.value
                    if (olderMsgs.size < 50) {
                        hasMoreMessages = false
                    }
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun ensureUserCached(userId: String) {
        if (_userCache.value.containsKey(userId)) return
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                val profile = api.getUserById("Bearer $token", userId)
                _userCache.value = _userCache.value + (userId to profile)
                _presenceMap.value = _presenceMap.value + (userId to profile.isOnline)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun loadUserProfile(userId: String) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                val profile = api.getUserById("Bearer $token", userId)
                _selectedUserProfile.value = profile
                _userCache.value = _userCache.value + (userId to profile)
                _presenceMap.value = _presenceMap.value + (userId to profile.isOnline)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load user profile: ${e.localizedMessage}"
            }
        }
    }

    fun clearSelectedUserProfile() {
        _selectedUserProfile.value = null
    }

    fun sendMessage(text: String) {
        val token = _authToken.value ?: return
        val channelId = _selectedChannelId.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                api.sendMessage("Bearer $token", channelId, MessageCreateRequest(text))
                fetchMessagesForActiveChannel()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send message: ${e.localizedMessage}"
            }
        }
    }

    fun sendMessageWithImages(context: Context, imageUris: List<Uri>, text: String) {
        sendMessageWithFiles(context, imageUris, text)
    }

    fun fetchAboutBackend() {
        viewModelScope.launch {
            try {
                val info = api.getAboutBackend()
                maxFileSize = info.maxFileSize
            } catch (e: Exception) {
                Log.e("VM", "Failed to fetch about-backend", e)
            }
        }
    }

    fun sendMessageWithFiles(context: Context, uris: List<Uri>, text: String) {
        val token = _authToken.value ?: return
        val channelId = _selectedChannelId.value ?: return
        viewModelScope.launch {
            try {
                if (maxFileSize != null) {
                    for (uri in uris) {
                        var size = -1L
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (cursor.moveToFirst() && sizeIndex != -1) {
                                size = cursor.getLong(sizeIndex)
                            }
                        }
                        if (size > maxFileSize!!) {
                            val maxMb = maxFileSize!! / (1024 * 1024)
                            _errorMessage.value = "File is too large (max allowed: ${if (maxMb > 0) "$maxMb MB" else "${maxFileSize} bytes"})"
                            return@launch
                        }
                    }
                }

                val msgText = text.ifBlank { if (uris.isNotEmpty()) "Attachments" else "" }
                val msg = api.sendMessage("Bearer $token", channelId, MessageCreateRequest(msgText))
                if (uris.isNotEmpty()) {
                    try {
                        api.attachFiles("Bearer $token", context, msg.id, uris)
                    } catch (e: Exception) {
                        Log.e("VM", "Failed to attach files", e)
                        _errorMessage.value = "Failed to attach files: ${e.localizedMessage}"
                    }
                }
                fetchMessagesForActiveChannel()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send message: ${e.localizedMessage}"
            }
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.updateMessage("Bearer $token", messageId, MessageUpdateRequest(newText))
                fetchMessagesForActiveChannel()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to edit message: ${e.localizedMessage}"
            }
        }
    }

    fun deleteMessage(messageId: String) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.deleteMessage("Bearer $token", messageId)
                fetchMessagesForActiveChannel()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete message: ${e.localizedMessage}"
            }
        }
    }

    fun createGuild(name: String, description: String?) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.createGuild("Bearer $token", GuildCreateRequest(name, description))
                fetchUserData()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create guild: ${e.localizedMessage}"
            }
        }
    }

    fun updateGuild(guildId: String, name: String, description: String?) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.updateGuild("Bearer $token", guildId, GuildUpdateRequest(name, description))
                fetchUserData()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update guild: ${e.localizedMessage}"
            }
        }
    }

    fun deleteGuild(guildId: String) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.deleteGuild("Bearer $token", guildId)
                _selectedGuildId.value = null
                _channels.value = emptyList()
                fetchUserData()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete guild: ${e.localizedMessage}"
            }
        }
    }

    fun leaveGuild(guildId: String) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.leaveGuild("Bearer $token", guildId)
                _selectedGuildId.value = null
                _channels.value = emptyList()
                fetchUserData()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to leave guild: ${e.localizedMessage}"
            }
        }
    }

    fun createInvite(guildId: String, usage: Int? = null, date: String? = null) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                val resp = api.createInvite("Bearer $token", guildId, InviteCreateRequest(usage, date))
                inviteCodeResult.value = resp
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create invite: ${e.localizedMessage}"
            }
        }
    }

    fun clearInviteCode() {
        inviteCodeResult.value = null
    }

    fun listInvites(guildId: String, onResult: (List<InviteResponse>) -> Unit) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                val invites = api.listInvites("Bearer $token", guildId)
                onResult(invites)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to list invites: ${e.localizedMessage}"
                onResult(emptyList())
            }
        }
    }

    fun deleteInvite(guildId: String, inviteCode: String, onComplete: () -> Unit = {}) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.deleteInvite("Bearer $token", guildId, inviteCode)
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete invite: ${e.localizedMessage}"
            }
        }
    }

    fun joinGuildByInvite(inviteCode: String) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.joinGuildByInvite("Bearer $token", inviteCode)
                fetchUserData()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to join guild: ${e.localizedMessage}"
            }
        }
    }

    fun createChannel(guildId: String, name: String, type: String = "text", categoryId: String? = null, nsfw: Boolean = false) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.createChannel("Bearer $token", guildId, ChannelCreateRequest(name, type, categoryId, nsfw))
                selectGuild(guildId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create channel: ${e.localizedMessage}"
            }
        }
    }

    fun updateChannel(guildId: String, channelId: String, name: String? = null, type: String? = null, categoryId: String? = null, nsfw: Boolean? = null) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.updateChannel("Bearer $token", guildId, channelId, ChannelUpdateRequest(name, type, categoryId, nsfw))
                selectGuild(guildId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update channel: ${e.localizedMessage}"
            }
        }
    }

    fun deleteChannel(guildId: String, channelId: String) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                api.deleteChannel("Bearer $token", guildId, channelId)
                selectGuild(guildId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete channel: ${e.localizedMessage}"
            }
        }
    }

    fun openDm(userId: String) {
        val token = _authToken.value ?: return
        viewModelScope.launch {
            try {
                val dm = api.createDm("Bearer $token", userId)
                val dms = api.getDmList("Bearer $token")
                _dmList.value = dms
                selectDm(dm.id)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to open DM: ${e.localizedMessage}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun handleNotificationIntent(intent: Intent?) {
        val channelId = intent?.getStringExtra("channel_id") ?: return
        viewModelScope.launch {
            delay(1200)
            val isDm = _dmList.value.any { it.id == channelId }
            if (isDm) {
                selectDm(channelId)
            } else {
                selectChannel(channelId)
            }
        }
    }
}
