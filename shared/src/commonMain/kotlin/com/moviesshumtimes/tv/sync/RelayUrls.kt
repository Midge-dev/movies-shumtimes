package com.moviesshumtimes.tv.sync

data class RelayHttpUrl(val base: String, val query: String?)

fun relayHttpUrl(relayUrl: String): RelayHttpUrl? {
    val (httpScheme, rest) = when {
        relayUrl.startsWith("wss://") -> "https://" to relayUrl.removePrefix("wss://")
        relayUrl.startsWith("ws://") -> "http://" to relayUrl.removePrefix("ws://")
        else -> return null
    }
    val hostAndPort = rest.substringBefore('/').substringBefore('?')
    if (hostAndPort.isBlank()) return null
    val query = rest.substringAfter('?', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
    return RelayHttpUrl(base = "$httpScheme$hostAndPort", query = query)
}
