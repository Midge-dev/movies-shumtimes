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
data class RelayIdentity(val peerId: String, val reconnectToken: String? = null)

private const val PEER_ID_KEY = "relay_peer_id"
private const val RECONNECT_TOKEN_KEY = "relay_reconnect_token"

@OptIn(ExperimentalUuidApi::class)
class RelayIdentityStore(private val settings: ObservableSettings) {
    fun load(): RelayIdentity {
        val peerId = settings.getStringOrNull(PEER_ID_KEY) ?: Uuid.random().toString().also { fresh ->
            settings.putString(PEER_ID_KEY, fresh)
        }
        return RelayIdentity(peerId, settings.getStringOrNull(RECONNECT_TOKEN_KEY))
    }

    fun saveReconnectToken(token: String) {
        settings.putString(RECONNECT_TOKEN_KEY, token)
    }
}
