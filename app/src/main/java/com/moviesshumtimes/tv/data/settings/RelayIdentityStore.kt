package com.moviesshumtimes.tv.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.relayIdentityDataStore by preferencesDataStore(name = "relay_identity")

// This device's identity with the relay: a peerId minted once and kept
// forever (so the relay recognizes "the same device" across app restarts —
// though not across a full uninstall, same as the relay pairing URL),
// plus whatever reconnectToken the relay most recently issued for it.
// Presenting both together on reconnect is what lets a device reclaim its
// own seat instead of racing for a free one.
data class RelayIdentity(val peerId: String, val reconnectToken: String? = null)

object RelayIdentityStore {
    private val PEER_ID_KEY = stringPreferencesKey("relay_peer_id")
    private val RECONNECT_TOKEN_KEY = stringPreferencesKey("relay_reconnect_token")

    suspend fun load(context: Context): RelayIdentity {
        val prefs = context.relayIdentityDataStore.data.first()
        val peerId = prefs[PEER_ID_KEY] ?: UUID.randomUUID().toString().also { fresh ->
            context.relayIdentityDataStore.edit { it[PEER_ID_KEY] = fresh }
        }
        return RelayIdentity(peerId, prefs[RECONNECT_TOKEN_KEY])
    }

    suspend fun saveReconnectToken(context: Context, token: String) {
        context.relayIdentityDataStore.edit { it[RECONNECT_TOKEN_KEY] = token }
    }
}
