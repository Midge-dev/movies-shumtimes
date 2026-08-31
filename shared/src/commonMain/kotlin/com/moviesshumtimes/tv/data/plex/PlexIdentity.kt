package com.moviesshumtimes.tv.data.plex

import com.russhwolf.settings.ObservableSettings
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val CLIENT_IDENTIFIER_KEY = "client_identifier"

@OptIn(ExperimentalUuidApi::class)
class PlexIdentity(private val settings: ObservableSettings) {
    fun getOrCreateClientIdentifier(): String {
        settings.getStringOrNull(CLIENT_IDENTIFIER_KEY)?.let { return it }

        val generated = Uuid.random().toString()
        settings.putString(CLIENT_IDENTIFIER_KEY, generated)
        return generated
    }
}
