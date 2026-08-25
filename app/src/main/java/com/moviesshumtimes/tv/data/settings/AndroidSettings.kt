package com.moviesshumtimes.tv.data.settings

import android.content.Context
import com.moviesshumtimes.tv.data.plex.PlexIdentity
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings

// Only Android needs a Context to build its underlying key-value store
// (SharedPreferences); Apple platforms construct NSUserDefaultsSettings
// with no Context equivalent needed. So this Context->ObservableSettings
// bridge — the one piece of this that's genuinely platform-specific —
// stays here in the app module rather than in the shared module, which
// only ever sees the portable ObservableSettings interface. Mirrors how
// DataStore's own `preferencesDataStore` delegate was used pre-migration:
// a lightweight per-call wrapper backed by state the OS itself caches
// (SharedPreferences instances are already memoized per file name by
// Android), not a wrapper this file needs to memoize itself.
private fun Context.observableSettings(name: String): ObservableSettings =
    SharedPreferencesSettings(applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE))

val Context.appSettingsStore: SettingsStore get() = SettingsStore(observableSettings("app_settings"))
val Context.relayIdentityStore: RelayIdentityStore get() = RelayIdentityStore(observableSettings("relay_identity"))
val Context.plexIdentityStore: PlexIdentity get() = PlexIdentity(observableSettings("plex_identity"))
