package com.moviesshumtimes.tv.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val relayUrl: String = DEFAULT_RELAY_URL,
    val maxVideoBitrateKbps: Int = DEFAULT_MAX_BITRATE_KBPS,
    val forceBurnSubtitles: Boolean = false,
    // Plex resource machineIdentifier of the server to browse, or null to
    // use the default auto-pick heuristic (see PlexResourcesApi.findReachableServer).
    val selectedServerId: String? = null,
) {
    companion object {
        // Placeholder LAN address — every install configures its own relay
        // via Settings (either typed manually or via the "Pair from phone"
        // flow), since a baked-in URL/token doesn't generalize once this
        // app is shared beyond one household's relay (port must match
        // relay/server.js's default, PORT env var else 8080).
        const val DEFAULT_RELAY_URL = "ws://192.168.0.12:8080"
        const val DEFAULT_MAX_BITRATE_KBPS = 8000
    }
}

object SettingsStore {
    private val RELAY_URL_KEY = stringPreferencesKey("relay_url")
    private val MAX_BITRATE_KEY = intPreferencesKey("max_video_bitrate_kbps")
    private val FORCE_BURN_KEY = booleanPreferencesKey("force_burn_subtitles")
    private val SELECTED_SERVER_ID_KEY = stringPreferencesKey("selected_server_id")

    fun observe(context: Context): Flow<AppSettings> =
        context.settingsDataStore.data.map { prefs ->
            AppSettings(
                relayUrl = prefs[RELAY_URL_KEY] ?: AppSettings.DEFAULT_RELAY_URL,
                maxVideoBitrateKbps = prefs[MAX_BITRATE_KEY] ?: AppSettings.DEFAULT_MAX_BITRATE_KBPS,
                forceBurnSubtitles = prefs[FORCE_BURN_KEY] ?: false,
                selectedServerId = prefs[SELECTED_SERVER_ID_KEY],
            )
        }

    suspend fun save(context: Context, settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[RELAY_URL_KEY] = settings.relayUrl
            prefs[MAX_BITRATE_KEY] = settings.maxVideoBitrateKbps
            prefs[FORCE_BURN_KEY] = settings.forceBurnSubtitles
            if (settings.selectedServerId != null) {
                prefs[SELECTED_SERVER_ID_KEY] = settings.selectedServerId
            } else {
                prefs.remove(SELECTED_SERVER_ID_KEY)
            }
        }
    }
}
