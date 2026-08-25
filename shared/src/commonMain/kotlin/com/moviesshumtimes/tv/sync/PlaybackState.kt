package com.moviesshumtimes.tv.sync

enum class PlaybackPhase { LOADING, WAITING_FOR_PEERS, PAUSED, PLAYING }

enum class PlaybackActionHint { PLAY, PAUSE, SEEK }

// The host's authoritative view of where playback is and should be. Guests
// reconcile their local player toward this rather than trusting their own
// play/pause/seek history (the old design's approach, and the source of
// the "spotty" sync — a guest's own buffering could get broadcast as a
// false pause). No per-media "epoch" tracking like Plezy's version needs —
// each PlayerScreen instance here is already scoped to exactly one movie.
data class PlaybackState(
    val seq: Int,
    val phase: PlaybackPhase,
    val anchorPositionMs: Long,
    val anchorHostTimeMs: Long,
    val waitingOn: List<String> = emptyList(),
    val actorPeerId: String? = null,
    val actionHint: PlaybackActionHint? = null,
) {
    // Where the player should be right now, in the host's clock. Only
    // meaningful while playing — paused/waiting/loading anchor a fixed spot.
    fun targetPositionMs(hostNowMs: Long): Long =
        if (phase == PlaybackPhase.PLAYING) {
            (anchorPositionMs + (hostNowMs - anchorHostTimeMs)).coerceAtLeast(0)
        } else {
            anchorPositionMs
        }
}

// A peer's local readiness, as last reported to the host. Buffering is
// reported separately from play/pause intent — that separation is the
// whole fix for the old bug where a transcode hiccup got broadcast as a
// real pause.
data class PeerStatus(val ready: Boolean, val buffering: Boolean, val positionMs: Long, val rttMs: Long? = null)

enum class ControlRequestKind { PLAY, PAUSE, SEEK }

data class ControlRequest(val kind: ControlRequestKind, val positionMs: Long? = null)
