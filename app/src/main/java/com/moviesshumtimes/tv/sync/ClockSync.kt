package com.moviesshumtimes.tv.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// NTP-style clock-offset estimation against the session host (guest side).
// Two independent Android TVs on two different networks have no reason to
// agree on wall-clock time; the old sync engine assumed they did (comparing
// raw `sentAtEpochMs` deltas), which is itself a plausible contributor to
// "spotty" sync independent of the buffering-vs-pause bug. This measures the
// actual offset instead of assuming it's zero.
//
// Sends pings via [sendPing] (the caller wraps them into RelayEvents
// addressed to the host) and consumes pongs via [onPong]. Keeps a rolling
// window of samples and reports the offset of the lowest-RTT sample — a
// single clean exchange beats an average polluted by jittery ones.
class ClockSync(
    private val scope: CoroutineScope,
    private val sendPing: (pingId: Long) -> Unit,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private companion object {
        const val WINDOW_SIZE = 8
        // 1000ms (Plezy's original default) assumes a well-connected
        // network. This app's real topology is two separate households on
        // residential internet routed through a relay hop — round trips
        // regularly exceeding 1s are normal there, not a broken network.
        // Rejecting those samples meant offsetMs stayed null forever on a
        // real cross-household test, so the guest fell back to assuming
        // zero clock difference between the two TVs — if their system
        // clocks actually differed, that's permanent, unresolvable
        // "drift" that no amount of correction could fix, triggering a
        // hard-seek every single cooldown cycle forever.
        const val MAX_ACCEPTED_RTT_MS = 5_000L
        const val INTERVAL_MS = 5_000L
        const val BURST_SPACING_MS = 500L
        const val BURST_COUNT = 3
        const val PENDING_EXPIRY_MS = 10_000L
    }

    private data class Sample(val offsetMs: Long, val rttMs: Long)

    // In-flight pings: pingId -> local send time. Multiple may be pending.
    private val pending = mutableMapOf<Long, Long>()

    // Accepted samples, oldest first.
    private val samples = mutableListOf<Sample>()

    private var job: Job? = null

    /// How far ahead the host's clock is vs ours, or null before any sample.
    val offsetMs: Long? get() = best()?.offsetMs

    /// Lowest RTT to the host in the sample window, or null before any sample.
    val minRttMs: Long? get() = best()?.rttMs

    private fun best(): Sample? = samples.minByOrNull { it.rttMs }

    /// Local time translated into the host's clock (identity until a sample
    /// arrives — callers needing a guarantee should check [offsetMs]).
    fun hostNowMs(): Long = nowMs() + (offsetMs ?: 0)

    /// Begin measuring: a short convergence burst, then a steady interval.
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

    /// Feed a pong from the host. [remoteTimestampMs] is the host's clock
    /// when it created the pong.
    fun onPong(pingId: Long, remoteTimestampMs: Long) {
        val sentAt = pending.remove(pingId) ?: return // Not ours or already expired.
        val now = nowMs()
        val rtt = now - sentAt
        if (rtt < 0 || rtt > MAX_ACCEPTED_RTT_MS) return
        val offset = remoteTimestampMs - sentAt - (rtt / 2)
        samples.add(Sample(offset, rtt))
        if (samples.size > WINDOW_SIZE) samples.removeAt(0)
    }
}
