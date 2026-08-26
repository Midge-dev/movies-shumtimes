package com.moviesshumtimes.tv.data.plex

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

// Ktor picks its engine per-platform automatically from whatever engine
// artifact is on that target's classpath (OkHttp on Android, Darwin on
// Apple platforms — see shared/build.gradle.kts) — callers never choose
// one explicitly, unlike the old per-class `OkHttpClient()` this replaces.
// `configure` runs inside the same builder scope (e.g. to `install` an
// extra plugin like HttpTimeout) — Ktor plugins can only be installed at
// construction time, not patched onto an already-built HttpClient.
internal fun plexHttpClient(configure: HttpClientConfig<*>.() -> Unit = {}): HttpClient = HttpClient {
    expectSuccess = true
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    configure()
}
