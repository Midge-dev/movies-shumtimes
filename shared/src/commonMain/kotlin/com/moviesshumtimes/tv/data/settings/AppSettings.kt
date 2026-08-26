package com.moviesshumtimes.tv.data.settings

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getIntFlow
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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
    // Watch-together chat still connects and sends/receives normally when
    // this is off — it only stops rendering the fading toast bubbles over
    // the video for viewers who find them distracting.
    val showChatOverlay: Boolean = true,
    // Plex resource machineIdentifier of the server to browse, or null to
    // use the default auto-pick heuristic (see PlexResourcesApi.findReachableServer).
    val selectedServerId: String? = null,
) {
    companion object {
        const val DEFAULT_MAX_BITRATE_KBPS = 8000

        // Shared by SettingsScreen and the in-player quality picker so both
        // surfaces offer the same choices with the same friendly labels —
        // most viewers have no intuition for what "8 Mbps" costs them, so
        // the plain number is paired with a plain-English quality tier.
        val BITRATE_PRESETS = listOf(
            BitratePreset(2000, "2 Mbps (Low quality)"),
            BitratePreset(4000, "4 Mbps (Medium quality)"),
            BitratePreset(8000, "8 Mbps (Good quality)"),
            BitratePreset(20000, "20 Mbps (High quality)"),
        )
    }
}

data class BitratePreset(val kbps: Int, val label: String)

private const val RELAY_URL_KEY = "relay_url"
private const val MAX_BITRATE_KEY = "max_video_bitrate_kbps"
private const val FORCE_BURN_KEY = "force_burn_subtitles"
private const val SHOW_CHAT_OVERLAY_KEY = "show_chat_overlay"
private const val SELECTED_SERVER_ID_KEY = "selected_server_id"

// `settings` is whatever platform-native key-value store the caller wires
// up — SharedPreferences-backed on Android, NSUserDefaults-backed on
// Apple platforms (see each platform's own app-layer construction; this
// class never touches Context/NSUserDefaults itself, only the portable
// Settings abstraction).
class SettingsStore(private val settings: ObservableSettings) {
    @OptIn(ExperimentalSettingsApi::class)
    fun observe(): Flow<AppSettings> = combine(
        settings.getStringOrNullFlow(RELAY_URL_KEY),
        settings.getIntFlow(MAX_BITRATE_KEY, AppSettings.DEFAULT_MAX_BITRATE_KBPS),
        settings.getBooleanFlow(FORCE_BURN_KEY, false),
        settings.getBooleanFlow(SHOW_CHAT_OVERLAY_KEY, true),
        settings.getStringOrNullFlow(SELECTED_SERVER_ID_KEY),
    ) { relayUrl, maxBitrateKbps, forceBurnSubtitles, showChatOverlay, selectedServerId ->
        AppSettings(relayUrl, maxBitrateKbps, forceBurnSubtitles, showChatOverlay, selectedServerId)
    }

    suspend fun save(appSettings: AppSettings) {
        if (appSettings.relayUrl != null) {
            settings.putString(RELAY_URL_KEY, appSettings.relayUrl)
        } else {
            settings.remove(RELAY_URL_KEY)
        }
        settings.putInt(MAX_BITRATE_KEY, appSettings.maxVideoBitrateKbps)
        settings.putBoolean(FORCE_BURN_KEY, appSettings.forceBurnSubtitles)
        settings.putBoolean(SHOW_CHAT_OVERLAY_KEY, appSettings.showChatOverlay)
        if (appSettings.selectedServerId != null) {
            settings.putString(SELECTED_SERVER_ID_KEY, appSettings.selectedServerId)
        } else {
            settings.remove(SELECTED_SERVER_ID_KEY)
        }
    }
}
