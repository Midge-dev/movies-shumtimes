package com.moviesshumtimes.tv.sync

enum class PlaybackPhase { LOADING, WAITING_FOR_PEERS, PAUSED, PLAYING }

enum class PlaybackActionHint { PLAY, PAUSE, SEEK }

data class PlaybackState(
    val seq: Int,
    val phase: PlaybackPhase,
    val anchorPositionMs: Long,
    val anchorHostTimeMs: Long,
    val waitingOn: List<String> = emptyList(),
    val actorPeerId: String? = null,
    val actionHint: PlaybackActionHint? = null,
) {
    fun targetPositionMs(hostNowMs: Long): Long =
        if (phase == PlaybackPhase.PLAYING) {
            (anchorPositionMs + (hostNowMs - anchorHostTimeMs)).coerceAtLeast(0)
        } else {
            anchorPositionMs
        }
}

data class PeerStatus(val ready: Boolean, val buffering: Boolean, val positionMs: Long, val rttMs: Long? = null)

enum class ControlRequestKind { PLAY, PAUSE, SEEK }

data class ControlRequest(val kind: ControlRequestKind, val positionMs: Long? = null)
