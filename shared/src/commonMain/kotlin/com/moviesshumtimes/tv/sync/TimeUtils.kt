package com.moviesshumtimes.tv.sync

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
