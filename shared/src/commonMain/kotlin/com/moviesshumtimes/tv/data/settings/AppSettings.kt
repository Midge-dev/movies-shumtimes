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

enum class ChatOverlayCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

@Serializable
data class RelayEntry(
    val id: String,
    val nickname: String,
    val url: String,
    val isDefault: Boolean = false,
)

data class AppSettings(
    val relays: List<RelayEntry> = emptyList(),
    val maxHostSeats: Int = DEFAULT_MAX_HOST_SEATS,
    val maxVideoBitrateKbps: Int = DEFAULT_MAX_BITRATE_KBPS,
    val forceBurnSubtitles: Boolean = false,
    val showChatOverlay: Boolean = true,
    val chatOverlayCorner: ChatOverlayCorner = ChatOverlayCorner.BOTTOM_END,
    val selectedServerId: String? = null,
) {
    companion object {
        const val DEFAULT_MAX_BITRATE_KBPS = 8000
        const val DEFAULT_MAX_HOST_SEATS = 8

        val BITRATE_PRESETS = listOf(
            BitratePreset(2000, "2 Mbps (Low)"),
            BitratePreset(4000, "4 Mbps (Medium)"),
            BitratePreset(8000, "8 Mbps (Good)"),
            BitratePreset(20000, "20 Mbps (High)"),
        )

        val MAX_HOST_SEATS_OPTIONS = listOf(2, 4, 6, 8, 12, 14, 16, 18, 20, 22, 24)
    }
}

val AppSettings.defaultRelay: RelayEntry?
    get() = relays.firstOrNull { it.isDefault } ?: relays.firstOrNull()

data class BitratePreset(val kbps: Int, val label: String)

private const val RELAY_URL_KEY = "relay_url"
private const val RELAY_ENTRIES_KEY = "relay_entries"
private const val MAX_HOST_SEATS_KEY = "max_host_seats"
private const val MAX_BITRATE_KEY = "max_video_bitrate_kbps"
private const val FORCE_BURN_KEY = "force_burn_subtitles"
private const val SHOW_CHAT_OVERLAY_KEY = "show_chat_overlay"
private const val CHAT_OVERLAY_CORNER_KEY = "chat_overlay_corner"
private const val SELECTED_SERVER_ID_KEY = "selected_server_id"

private val relayListJson = Json { ignoreUnknownKeys = true }

private data class BaseSettings(
    val maxBitrateKbps: Int,
    val forceBurnSubtitles: Boolean,
    val showChatOverlay: Boolean,
    val maxHostSeats: Int,
)

class SettingsStore(private val settings: ObservableSettings) {
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

    private fun decodeRelays(json: String?): List<RelayEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { relayListJson.decodeFromString<List<RelayEntry>>(json) }.getOrDefault(emptyList())
    }

    private suspend fun migrateLegacyRelayUrlIfNeeded(current: AppSettings): AppSettings = migrationMutex.withLock {
        if (current.relays.isNotEmpty()) return@withLock current
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
