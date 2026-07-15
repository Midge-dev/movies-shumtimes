package com.moviesshumtimes.tv.sync

// Placeholder until Phase G adds a settings screen for this. Currently
// points at a relay running on the dev machine's LAN for same-network
// testing — swap for the deployed Render wss:// URL (and make it
// user-configurable) before actually watching with the cousin.
object RelayConfig {
    const val URL = "ws://192.168.0.12:8099"
}
