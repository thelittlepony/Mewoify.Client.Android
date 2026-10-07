package com.tlpteam.basilservice.data.api

import com.tlpteam.basilservice.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface BasilApiService {

    @POST("/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("/api/users/me")
    suspend fun getMe(@Header("Authorization") authHeader: String): UserResponse

    @PATCH("/api/users/me")
    suspend fun updateMe(
        @Header("Authorization") authHeader: String,
        @Body request: UserUpdateRequest
    ): UserResponse

    @GET("/api/users/{user_id}")
    suspend fun getUserById(
        @Header("Authorization") authHeader: String,
        @Path("user_id") userId: String
    ): PublicUserResponse

    @POST("/api/guild/create")
    suspend fun createGuild(
        @Header("Authorization") authHeader: String,
        @Body request: GuildCreateRequest
    ): GuildResponse

    @GET("/api/guild/{guild_id}")
    suspend fun getGuild(
        @Header("Authorization") authHeader: String,
        @Path("guild_id") guildId: String
    ): GuildResponse

    @PATCH("/api/guild/{guild_id}")
    suspend fun updateGuild(
        @Header("Authorization") authHeader: String,
        @Path("guild_id") guildId: String,
        @Body request: GuildUpdateRequest
    ): GuildResponse

    @DELETE("/api/guild/{guild_id}")
    suspend fun deleteGuild(
        @Header("Authorization") authHeader: String,
        @Path("guild_id") guildId: String
    ): Response<Unit>

    @DELETE("/api/guild/{guild_id}/leave")
    suspend fun leaveGuild(
        @Header("Authorization") authHeader: String,
        @Path("guild_id") guildId: String
    ): Response<Unit>

    @POST("/api/guild/{guild_id}/channel-create")
    suspend fun createChannel(
        @Header("Authorization") authHeader: String,
        @Path("guild_id") guildId: String,
        @Body request: ChannelCreateRequest
    ): ChannelResponse

    @GET("/api/guild/{guild_id}/channels")
    suspend fun getChannels(
        @Header("Authorization") authHeader: String,
        @Path("guild_id") guildId: String
    ): List<ChannelResponse>

    @PATCH("/api/guild/{guild_id}/channel/{channel_id}")
    suspend fun updateChannel(
        @Header("Authorization") authHeader: String,
        @Path("guild_id") guildId: String,
        @Path("channel_id") channelId: String,
        @Body request: ChannelUpdateRequest
    ): ChannelResponse

    @DELETE("/api/guild/{guild_id}/channel/{channel_id}")
    suspend fun deleteChannel(
        @Header("Authorization") authHeader: String,
        @Path("guild_id") guildId: String,
        @Path("channel_id") channelId: String
    ): Response<Unit>

    @POST("/api/guild/{guild_id}/invite")
    suspend fun createInvite(
        @Header("Authorization") authHeader: String,
        @Path("guild_id") guildId: String,
        @Body request: InviteCreateRequest? = null
    ): InviteResponse

    @GET("/api/guild/{guild_id}/invites")
    suspend fun listInvites(
        @Header("Authorization") authHeader: String,
        @Path("guild_id") guildId: String
    ): List<InviteResponse>

    @DELETE("/api/guild/{guild_id}/invite/{invite_code}")
    suspend fun deleteInvite(
        @Header("Authorization") authHeader: String,
        @Path("guild_id") guildId: String,
        @Path("invite_code") inviteCode: String
    ): Response<Unit>

    @POST("/api/guild/join-invite/{invite_code}")
    suspend fun joinGuildByInvite(
        @Header("Authorization") authHeader: String,
        @Path("invite_code") inviteCode: String
    ): Response<Unit>

    @POST("/api/users/{user_id}/dm/create")
    suspend fun createDm(
        @Header("Authorization") authHeader: String,
        @Path("user_id") userId: String
    ): DmResponse

    @GET("/api/users/me/dm-list")
    suspend fun getDmList(
        @Header("Authorization") authHeader: String
    ): List<DmResponse>

    @POST("/api/channel/{channel_id}/message-create")
    suspend fun sendMessage(
        @Header("Authorization") authHeader: String,
        @Path("channel_id") channelId: String,
        @Body request: MessageCreateRequest
    ): MessageResponse

    @GET("/api/channel/{channel_id}/messages")
    suspend fun getMessages(
        @Header("Authorization") authHeader: String,
        @Path("channel_id") channelId: String
    ): List<MessageResponse>

    @PATCH("/api/message/{message_id}")
    suspend fun updateMessage(
        @Header("Authorization") authHeader: String,
        @Path("message_id") messageId: String,
        @Body request: MessageUpdateRequest
    ): MessageResponse

    @DELETE("/api/message/{message_id}")
    suspend fun deleteMessage(
        @Header("Authorization") authHeader: String,
        @Path("message_id") messageId: String
    ): Response<Unit>

    @GET("/public-api/about-backend")
    suspend fun getAboutBackend(): AboutBackendResponse
}
