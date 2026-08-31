package com.moviesshumtimes.tv.data.plex

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

// No caller of this had ever installed HttpTimeout (RelayDirectoryApi's own
// client is separate and does), so an unresponsive-but-not-refused Plex
// server — a real home-server failure mode, not just "connection refused" —
// left every suspend call through this client hung forever with no way for
// the UI to show a retry state. 15s is generous enough for a slow home
// connection or a large library page; PlexResourcesApi's connectClient
// already overrides this lower (4s) for its own per-connection reachability
// probes via `configure` below.
private const val DEFAULT_TIMEOUT_MS = 15_000L

// Ktor picks its engine per-platform automatically from whatever engine
// artifact is on that target's classpath (OkHttp on Android, Darwin on
// Apple platforms — see shared/build.gradle.kts) — callers never choose
// one explicitly, unlike the old per-class `OkHttpClient()` this replaces.
// `configure` runs inside the same builder scope (e.g. to `install` an
// extra plugin like HttpTimeout) — Ktor plugins can only be installed at
// construction time, not patched onto an already-built HttpClient. Running
// it after the defaults below means a caller's own `install(HttpTimeout) {}`
// replaces this one's config rather than conflicting with it.
internal fun plexHttpClient(configure: HttpClientConfig<*>.() -> Unit = {}): HttpClient = HttpClient {
    expectSuccess = true
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        connectTimeoutMillis = DEFAULT_TIMEOUT_MS
        requestTimeoutMillis = DEFAULT_TIMEOUT_MS
    }
    configure()
}
