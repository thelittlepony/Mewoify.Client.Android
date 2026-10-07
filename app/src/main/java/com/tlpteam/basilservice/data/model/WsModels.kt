package com.tlpteam.basilservice.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class WsPacket(
    val t: String,
    val d: JsonElement = JsonObject(emptyMap()),
    val s: Int? = null
)

@Serializable
data class HiClientData(
    val auth_token: String,
    val client_event_capabilities: List<String>
)
