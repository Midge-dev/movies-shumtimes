package com.moviesshumtimes.tv.data.plex

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val DEFAULT_TIMEOUT_MS = 15_000L

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
