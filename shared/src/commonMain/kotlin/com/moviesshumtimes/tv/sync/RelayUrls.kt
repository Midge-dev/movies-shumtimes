package com.moviesshumtimes.tv.sync

// Splits a relay ws(s):// URL into its http(s) equivalent base (scheme +
// host + port, no path) plus whatever query string it carried (typically
// ?token=...) — deliberately hand-rolled string parsing rather than
// java.net.URI, which isn't available outside the JVM target and this
// module is commonMain (shared with the eventual tvOS build).
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
