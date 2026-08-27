package com.moviesshumtimes.tv.data.settings

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getIntFlow
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

// Design spec section 07: which screen corner the chat toast stack anchors
// to. Stack growth direction and text alignment both derive from this in
// ChatOverlay — top corners stack downward (newest nearest the corner), text
// follows the horizontal edge (START = left, END = right).
enum class ChatOverlayCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

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
    val chatOverlayCorner: ChatOverlayCorner = ChatOverlayCorner.BOTTOM_END,
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
private const val CHAT_OVERLAY_CORNER_KEY = "chat_overlay_corner"
private const val SELECTED_SERVER_ID_KEY = "selected_server_id"

// combine() only has named overloads up to 5 flows, so the six settings
// fields are combined in two groups of four/three (see observe() below)
// rather than one flat call.
private data class BaseSettings(
    val relayUrl: String?,
    val maxBitrateKbps: Int,
    val forceBurnSubtitles: Boolean,
    val showChatOverlay: Boolean,
)

// `settings` is whatever platform-native key-value store the caller wires
// up — SharedPreferences-backed on Android, NSUserDefaults-backed on
// Apple platforms (see each platform's own app-layer construction; this
// class never touches Context/NSUserDefaults itself, only the portable
// Settings abstraction).
class SettingsStore(private val settings: ObservableSettings) {
    @OptIn(ExperimentalSettingsApi::class)
    fun observe(): Flow<AppSettings> {
        val base = combine(
            settings.getStringOrNullFlow(RELAY_URL_KEY),
            settings.getIntFlow(MAX_BITRATE_KEY, AppSettings.DEFAULT_MAX_BITRATE_KBPS),
            settings.getBooleanFlow(FORCE_BURN_KEY, false),
            settings.getBooleanFlow(SHOW_CHAT_OVERLAY_KEY, true),
        ) { relayUrl, maxBitrateKbps, forceBurnSubtitles, showChatOverlay ->
            BaseSettings(relayUrl, maxBitrateKbps, forceBurnSubtitles, showChatOverlay)
        }
        return combine(
            base,
            settings.getStringOrNullFlow(SELECTED_SERVER_ID_KEY),
            settings.getStringOrNullFlow(CHAT_OVERLAY_CORNER_KEY),
        ) { b, selectedServerId, cornerName ->
            AppSettings(
                relayUrl = b.relayUrl,
                maxVideoBitrateKbps = b.maxBitrateKbps,
                forceBurnSubtitles = b.forceBurnSubtitles,
                showChatOverlay = b.showChatOverlay,
                chatOverlayCorner = ChatOverlayCorner.entries.firstOrNull { it.name == cornerName }
                    ?: ChatOverlayCorner.BOTTOM_END,
                selectedServerId = selectedServerId,
            )
        }
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
        settings.putString(CHAT_OVERLAY_CORNER_KEY, appSettings.chatOverlayCorner.name)
        if (appSettings.selectedServerId != null) {
            settings.putString(SELECTED_SERVER_ID_KEY, appSettings.selectedServerId)
        } else {
            settings.remove(SELECTED_SERVER_ID_KEY)
        }
    }
}
