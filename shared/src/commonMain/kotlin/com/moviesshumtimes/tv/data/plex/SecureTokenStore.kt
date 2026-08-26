package com.moviesshumtimes.tv.data.plex

// Secure, encrypted-at-rest storage for the Plex account token. Unlike
// AppSettings/RelayIdentityStore/PlexIdentity (step 3), there's no
// multiplatform-settings-style library covering this — Android and Apple
// platforms have genuinely different secure-storage models, not just
// different plumbing to the same idea: Android hand-rolls AES-GCM via the
// Keystore (see AndroidTokenStore in the app module), while Apple
// platforms would store directly in the Keychain, which encrypts at rest
// itself with no application-level crypto needed. So each platform's app
// layer provides its own implementation of this interface, the same shape
// as AppSettings' Context->ObservableSettings bridge — the shared module
// only ever sees this interface, never a platform storage API directly.
interface SecureTokenStore {
    suspend fun saveToken(token: String)
    suspend fun loadToken(): String?
    suspend fun clearToken()
}
