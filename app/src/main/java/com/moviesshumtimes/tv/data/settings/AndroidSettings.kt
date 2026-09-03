package com.moviesshumtimes.tv.data.settings

import android.content.Context
import com.moviesshumtimes.tv.data.plex.AndroidTokenStore
import com.moviesshumtimes.tv.data.plex.PlexIdentity
import com.moviesshumtimes.tv.data.plex.SecureTokenStore
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings

private fun Context.observableSettings(name: String): ObservableSettings =
    SharedPreferencesSettings(applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE))

val Context.appSettingsStore: SettingsStore get() = SettingsStore(observableSettings("app_settings"))
val Context.relayIdentityStore: RelayIdentityStore get() = RelayIdentityStore(observableSettings("relay_identity"))
val Context.plexIdentityStore: PlexIdentity get() = PlexIdentity(observableSettings("plex_identity"))

val Context.tokenStore: SecureTokenStore get() = AndroidTokenStore(applicationContext)
