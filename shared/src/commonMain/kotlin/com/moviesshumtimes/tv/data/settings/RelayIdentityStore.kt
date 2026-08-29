package com.moviesshumtimes.tv.data.settings

import com.russhwolf.settings.ObservableSettings
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// This device's identity with the relay: a peerId minted once and kept
// forever (so the relay recognizes "the same device" across app restarts —
// though not across a full uninstall, same as the relay pairing URL),
// plus whatever reconnectToken the relay most recently issued for it.
// Presenting both together on reconnect is what lets a device reclaim its
// own seat instead of racing for a free one.
//
// hostedRoomId is the last room this device held seat 0 (host) in — kept so
// Home can offer "End session" for a room well after the live socket to it
// is gone (see RelayClient's onHostedRoomIdUpdated), using the same
// peerId + reconnectToken as proof of host identity a reconnect would
// present. Like reconnectToken, it's a single slot for "whatever this
// device most recently did," not a history — reused the moment this device
// hosts (or reclaims) any other room.
data class RelayIdentity(val peerId: String, val reconnectToken: String? = null, val hostedRoomId: String? = null)

private const val PEER_ID_KEY = "relay_peer_id"
private const val RECONNECT_TOKEN_KEY = "relay_reconnect_token"
private const val HOSTED_ROOM_ID_KEY = "relay_hosted_room_id"

@OptIn(ExperimentalUuidApi::class)
class RelayIdentityStore(private val settings: ObservableSettings) {
    fun load(): RelayIdentity {
        val peerId = settings.getStringOrNull(PEER_ID_KEY) ?: Uuid.random().toString().also { fresh ->
            settings.putString(PEER_ID_KEY, fresh)
        }
        return RelayIdentity(peerId, settings.getStringOrNull(RECONNECT_TOKEN_KEY), settings.getStringOrNull(HOSTED_ROOM_ID_KEY))
    }

    fun saveReconnectToken(token: String) {
        settings.putString(RECONNECT_TOKEN_KEY, token)
    }

    fun saveHostedRoomId(roomId: String?) {
        if (roomId == null) settings.remove(HOSTED_ROOM_ID_KEY) else settings.putString(HOSTED_ROOM_ID_KEY, roomId)
    }
}
