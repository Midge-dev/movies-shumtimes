package com.moviesshumtimes.tv.data.settings

import com.russhwolf.settings.ObservableSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class HostedRoom(val relayUrl: String, val roomId: String, val reconnectToken: String)

data class RelayIdentity(
    val peerId: String,
    val reconnectToken: String? = null,
    val hostedRooms: List<HostedRoom> = emptyList(),
)

private const val PEER_ID_KEY = "relay_peer_id"
private const val RECONNECT_TOKEN_KEY = "relay_reconnect_token"
private const val HOSTED_ROOMS_KEY = "relay_hosted_rooms"

@OptIn(ExperimentalUuidApi::class)
class RelayIdentityStore(private val settings: ObservableSettings) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): RelayIdentity {
        val peerId = settings.getStringOrNull(PEER_ID_KEY) ?: Uuid.random().toString().also { fresh ->
            settings.putString(PEER_ID_KEY, fresh)
        }
        val hostedRooms = settings.getStringOrNull(HOSTED_ROOMS_KEY)
            ?.let { raw -> runCatching { json.decodeFromString<List<HostedRoom>>(raw) }.getOrNull() }
            ?: emptyList()
        return RelayIdentity(peerId, settings.getStringOrNull(RECONNECT_TOKEN_KEY), hostedRooms)
    }

    fun saveReconnectToken(token: String) {
        settings.putString(RECONNECT_TOKEN_KEY, token)
    }

    fun addHostedRoom(relayUrl: String, roomId: String, reconnectToken: String) {
        val updated = load().hostedRooms.filterNot { it.roomId == roomId } + HostedRoom(relayUrl, roomId, reconnectToken)
        settings.putString(HOSTED_ROOMS_KEY, json.encodeToString(updated))
    }

    fun removeHostedRoom(roomId: String) {
        val updated = load().hostedRooms.filterNot { it.roomId == roomId }
        if (updated.isEmpty()) settings.remove(HOSTED_ROOMS_KEY) else settings.putString(HOSTED_ROOMS_KEY, json.encodeToString(updated))
    }
}
