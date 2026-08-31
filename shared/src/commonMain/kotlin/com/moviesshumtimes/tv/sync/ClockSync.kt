package com.moviesshumtimes.tv.sync

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTime::class)
class ClockSync(
    private val scope: CoroutineScope,
    private val sendPing: (pingId: Long) -> Unit,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private companion object {
        const val WINDOW_SIZE = 8
        const val MAX_ACCEPTED_RTT_MS = 5_000L
        const val INTERVAL_MS = 5_000L
        const val BURST_SPACING_MS = 500L
        const val BURST_COUNT = 3
        const val PENDING_EXPIRY_MS = 10_000L
    }

    private data class Sample(val offsetMs: Long, val rttMs: Long)

    private val pending = mutableMapOf<Long, Long>()

    private val samples = mutableListOf<Sample>()

    private var job: Job? = null

    val offsetMs: Long? get() = best()?.offsetMs

    val minRttMs: Long? get() = best()?.rttMs

    private fun best(): Sample? = samples.minByOrNull { it.rttMs }

    fun hostNowMs(): Long = nowMs() + (offsetMs ?: 0)

    fun start() {
        if (job != null) return
        job = scope.launch {
            ping()
            repeat(BURST_COUNT - 1) {
                delay(BURST_SPACING_MS)
                ping()
            }
            while (true) {
                delay(INTERVAL_MS)
                ping()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        pending.clear()
    }

    private fun ping() {
        val now = nowMs()
        pending.entries.removeAll { now - it.value > PENDING_EXPIRY_MS }
        var pingId = now
        while (pending.containsKey(pingId)) pingId++
        pending[pingId] = now
        sendPing(pingId)
    }

    fun onPong(pingId: Long, remoteTimestampMs: Long) {
        val sentAt = pending.remove(pingId) ?: return
        val now = nowMs()
        val rtt = now - sentAt
        if (rtt < 0 || rtt > MAX_ACCEPTED_RTT_MS) return
        val offset = remoteTimestampMs - sentAt - (rtt / 2)
        samples.add(Sample(offset, rtt))
        if (samples.size > WINDOW_SIZE) samples.removeAt(0)
    }
}
