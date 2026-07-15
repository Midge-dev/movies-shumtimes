package com.moviesshumtimes.tv.data.plex

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.first

private val Context.identityDataStore by preferencesDataStore(name = "plex_identity")

// Plex requires a stable X-Plex-Client-Identifier on every request, generated
// once per install and reused forever after — not a per-session value.
object PlexIdentity {
    private val CLIENT_IDENTIFIER_KEY = stringPreferencesKey("client_identifier")

    suspend fun getOrCreateClientIdentifier(context: Context): String {
        val store = context.identityDataStore
        store.data.first()[CLIENT_IDENTIFIER_KEY]?.let { return it }

        val generated = UUID.randomUUID().toString()
        store.edit { prefs -> prefs[CLIENT_IDENTIFIER_KEY] = generated }
        return generated
    }
}
