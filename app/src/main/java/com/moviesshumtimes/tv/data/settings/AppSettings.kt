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
    // null until configured — no baked-in/placeholder fallback, since one
    // doesn't generalize once this app is shared beyond one household's
    // relay. RelaySetupScreen prompts for this right after first login;
    // MainActivity treats null as "no watch-together relay" and skips
    // straight to solo playback rather than showing a Lobby with no one to
    // wait for.
    val relayUrl: String? = null,
    val maxVideoBitrateKbps: Int = DEFAULT_MAX_BITRATE_KBPS,
    val forceBurnSubtitles: Boolean = false,
    // Plex resource machineIdentifier of the server to browse, or null to
    // use the default auto-pick heuristic (see PlexResourcesApi.findReachableServer).
    val selectedServerId: String? = null,
) {
    companion object {
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
                relayUrl = prefs[RELAY_URL_KEY],
                maxVideoBitrateKbps = prefs[MAX_BITRATE_KEY] ?: AppSettings.DEFAULT_MAX_BITRATE_KBPS,
                forceBurnSubtitles = prefs[FORCE_BURN_KEY] ?: false,
                selectedServerId = prefs[SELECTED_SERVER_ID_KEY],
            )
        }

    suspend fun save(context: Context, settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            if (settings.relayUrl != null) {
                prefs[RELAY_URL_KEY] = settings.relayUrl
            } else {
                prefs.remove(RELAY_URL_KEY)
            }
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
