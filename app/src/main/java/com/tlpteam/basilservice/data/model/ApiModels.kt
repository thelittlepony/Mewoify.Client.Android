package com.tlpteam.basilservice.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val login: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String
)

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val email: String,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("created_at") val createdAt: String,
    val guilds: List<GuildSummaryResponse> = emptyList()
)

@Serializable
data class PublicUserResponse(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen_at") val lastSeenAt: String? = null
)

@Serializable
data class UserUpdateRequest(
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null
)

@Serializable
data class GuildCreateRequest(
    val name: String,
    val description: String? = null
)

@Serializable
data class GuildResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class GuildSummaryResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class GuildUpdateRequest(
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class ChannelCreateRequest(
    val name: String,
    @SerialName("channel_type") val channelType: String = "text",
    @SerialName("category_id") val categoryId: String? = null,
    val nsfw: Boolean = false
)

@Serializable
data class ChannelUpdateRequest(
    val name: String? = null,
    @SerialName("channel_type") val channelType: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val nsfw: Boolean? = null
)

@Serializable
data class ChannelResponse(
    val id: String,
    @SerialName("guild_id") val guildId: String,
    val name: String,
    @SerialName("channel_type") val channelType: String,
    @SerialName("category_id") val categoryId: String? = null,
    val position: Int,
    @SerialName("created_at") val createdAt: String,
    val nsfw: Boolean = false
)

@Serializable
data class InviteCreateRequest(
    val usage: Int? = null,
    val date: String? = null
)

@Serializable
data class InviteResponse(
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("guild_id") val guildId: String,
    val usage: Int? = null,
    val uses: Int = 0,
    val date: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class DmResponse(
    val id: String,
    @SerialName("user_id_1") val userId1: String,
    @SerialName("user_id_2") val userId2: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class MessageCreateRequest(
    @SerialName("text_content") val textContent: String
)

@Serializable
data class MessageUpdateRequest(
    @SerialName("text_content") val textContent: String
)

@Serializable
data class MessageResponse(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("channel_type") val channelType: String,
    @SerialName("channel_id") val channelId: String,
    @SerialName("text_content") val textContent: String,
    @SerialName("file_content") val fileContent: JsonElement? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class AboutBackendResponse(
    val name: String,
    @SerialName("backend_version") val backendVersion: String,
    @SerialName("thirdparty_version") val thirdpartyVersion: Map<String, String>? = null,
    val copyright: String? = null,
    @SerialName("max_file_size") val maxFileSize: Long? = null
)
