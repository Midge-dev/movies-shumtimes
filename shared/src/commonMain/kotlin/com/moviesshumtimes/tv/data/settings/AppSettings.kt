package com.moviesshumtimes.tv.data.settings

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getIntFlow
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Design spec section 07: which screen corner the chat toast stack anchors
// to. Stack growth direction and text alignment both derive from this in
// ChatOverlay — top corners stack downward (newest nearest the corner), text
// follows the horizontal edge (START = left, END = right).
enum class ChatOverlayCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

// Design spec 09d: relays are now a list, not one URL — different friends in
// a larger group may each run their own (cloud or home-hosted, it doesn't
// matter which). `isDefault` picks which one Watch Together hosts a new room
// on; at most one entry should have it set (SettingsStore enforces this on
// save, see below).
@Serializable
data class RelayEntry(
    val id: String,
    val nickname: String,
    val url: String,
    val isDefault: Boolean = false,
)

data class AppSettings(
    // Empty until the user adds one — no baked-in/placeholder fallback,
    // since one doesn't generalize once this app is shared beyond one
    // household's relay. RelaySetupScreen prompts for the first one right
    // after login; MainActivity treats an empty list as "no watch-together
    // relay" and skips straight to solo playback rather than showing a
    // Lobby with no one to wait for.
    val relays: List<RelayEntry> = emptyList(),
    // Design spec section 14 "Maximum seats" — a client-side cap on rooms
    // this device hosts, sent with the room when it opens (RoomIntent.
    // Create.maxSeats); the relay's own constant is still the ceiling.
    // Lowering it never evicts anyone already in a live room, since a room's
    // seat count is fixed at creation time — it only affects rooms created
    // after the change.
    val maxHostSeats: Int = DEFAULT_MAX_HOST_SEATS,
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
        const val DEFAULT_MAX_HOST_SEATS = 8

        // Shared by SettingsScreen and the in-player quality picker so both
        // surfaces offer the same choices with the same friendly labels —
        // most viewers have no intuition for what "8 Mbps" costs them, so
        // the plain number is paired with a plain-English quality tier.
        // Dropped the trailing "quality" word (was "Low quality)" etc.) —
        // it's redundant against the row's own "Max transcode video
        // bitrate" label, and the couple dp it costs is what actually keeps
        // all four presets on screen at once without a horizontal scroll.
        val BITRATE_PRESETS = listOf(
            BitratePreset(2000, "2 Mbps (Low)"),
            BitratePreset(4000, "4 Mbps (Medium)"),
            BitratePreset(8000, "8 Mbps (Good)"),
            BitratePreset(20000, "20 Mbps (High)"),
        )

        // Design spec section 14: eleven entries, scrolls (unlike Sort's
        // short list) — the menu opens with focus on the applied value
        // wherever it sits in this list.
        val MAX_HOST_SEATS_OPTIONS = listOf(2, 4, 6, 8, 12, 14, 16, 18, 20, 22, 24)
    }
}

// Design spec 09d: "Always the default. No prompt." — Watch Together hosts
// on whichever relay is flagged default, falling back to the first
// configured relay if none is explicitly flagged (defensive; save() below
// keeps exactly one flagged once there's at least one entry).
val AppSettings.defaultRelay: RelayEntry?
    get() = relays.firstOrNull { it.isDefault } ?: relays.firstOrNull()

data class BitratePreset(val kbps: Int, val label: String)

private const val RELAY_URL_KEY = "relay_url" // legacy — see migration in observe()
private const val RELAY_ENTRIES_KEY = "relay_entries"
private const val MAX_HOST_SEATS_KEY = "max_host_seats"
private const val MAX_BITRATE_KEY = "max_video_bitrate_kbps"
private const val FORCE_BURN_KEY = "force_burn_subtitles"
private const val SHOW_CHAT_OVERLAY_KEY = "show_chat_overlay"
private const val CHAT_OVERLAY_CORNER_KEY = "chat_overlay_corner"
private const val SELECTED_SERVER_ID_KEY = "selected_server_id"

private val relayListJson = Json { ignoreUnknownKeys = true }

// combine() only has named overloads up to 5 flows, so the settings fields
// are combined in two groups (see observe() below) rather than one flat
// call.
private data class BaseSettings(
    val maxBitrateKbps: Int,
    val forceBurnSubtitles: Boolean,
    val showChatOverlay: Boolean,
    val maxHostSeats: Int,
)

// `settings` is whatever platform-native key-value store the caller wires
// up — SharedPreferences-backed on Android, NSUserDefaults-backed on
// Apple platforms (see each platform's own app-layer construction; this
// class never touches Context/NSUserDefaults itself, only the portable
// Settings abstraction).
class SettingsStore(private val settings: ObservableSettings) {
    // Guards migrateLegacyRelayUrlIfNeeded below — observe() can have more
    // than one collector (e.g. two screens both observing settings at cold
    // start), and without this, two collectors can both see an empty
    // relays list + a legacy URL before either write propagates back
    // through ObservableSettings, each writing their own migrated entry and
    // leaving two duplicate "My relay" rows behind.
    private val migrationMutex = Mutex()

    @OptIn(ExperimentalSettingsApi::class)
    fun observe(): Flow<AppSettings> {
        val base = combine(
            settings.getIntFlow(MAX_BITRATE_KEY, AppSettings.DEFAULT_MAX_BITRATE_KBPS),
            settings.getBooleanFlow(FORCE_BURN_KEY, false),
            settings.getBooleanFlow(SHOW_CHAT_OVERLAY_KEY, true),
            settings.getIntFlow(MAX_HOST_SEATS_KEY, AppSettings.DEFAULT_MAX_HOST_SEATS),
        ) { maxBitrateKbps, forceBurnSubtitles, showChatOverlay, maxHostSeats ->
            BaseSettings(maxBitrateKbps, forceBurnSubtitles, showChatOverlay, maxHostSeats)
        }
        return combine(
            base,
            settings.getStringOrNullFlow(SELECTED_SERVER_ID_KEY),
            settings.getStringOrNullFlow(CHAT_OVERLAY_CORNER_KEY),
            settings.getStringOrNullFlow(RELAY_ENTRIES_KEY),
        ) { b, selectedServerId, cornerName, relayEntriesJson ->
            AppSettings(
                relays = decodeRelays(relayEntriesJson),
                maxHostSeats = b.maxHostSeats,
                maxVideoBitrateKbps = b.maxBitrateKbps,
                forceBurnSubtitles = b.forceBurnSubtitles,
                showChatOverlay = b.showChatOverlay,
                chatOverlayCorner = ChatOverlayCorner.entries.firstOrNull { it.name == cornerName }
                    ?: ChatOverlayCorner.BOTTOM_END,
                selectedServerId = selectedServerId,
            )
        }.map { migrateLegacyRelayUrlIfNeeded(it) }
    }

    // One-time migration: an install that only ever had the old single
    // relay_url key gets it wrapped into a single default-nicknamed entry,
    // written through immediately so this only runs once. A fresh install
    // (both keys absent) just gets an empty list, same as before.
    private fun decodeRelays(json: String?): List<RelayEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { relayListJson.decodeFromString<List<RelayEntry>>(json) }.getOrDefault(emptyList())
    }

    private suspend fun migrateLegacyRelayUrlIfNeeded(current: AppSettings): AppSettings = migrationMutex.withLock {
        if (current.relays.isNotEmpty()) return@withLock current
        // Re-check against the store itself, not just `current` — another
        // collector could have already migrated and written through while
        // this call was waiting on the lock above.
        if (settings.getStringOrNull(RELAY_ENTRIES_KEY) != null) return@withLock current
        val legacyUrl = settings.getStringOrNull(RELAY_URL_KEY)?.takeIf { it.isNotBlank() } ?: return@withLock current
        val migrated = current.copy(
            relays = listOf(RelayEntry(id = randomRelayId(), nickname = "My relay", url = legacyUrl, isDefault = true)),
        )
        save(migrated)
        settings.remove(RELAY_URL_KEY)
        migrated
    }

    suspend fun save(appSettings: AppSettings) {
        // Exactly one entry (the first flagged, or the first entry if none
        // are flagged) is ever persisted as default — defends against a
        // caller accidentally producing two "isDefault = true" rows.
        val normalizedRelays = run {
            val defaultId = appSettings.relays.firstOrNull { it.isDefault }?.id
                ?: appSettings.relays.firstOrNull()?.id
            appSettings.relays.map { it.copy(isDefault = it.id == defaultId) }
        }
        if (normalizedRelays.isNotEmpty()) {
            settings.putString(RELAY_ENTRIES_KEY, relayListJson.encodeToString(normalizedRelays))
        } else {
            settings.remove(RELAY_ENTRIES_KEY)
        }
        settings.putInt(MAX_HOST_SEATS_KEY, appSettings.maxHostSeats)
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

private fun randomRelayId(): String = (1..16).map { ('a'..'z').random() }.joinToString("")
