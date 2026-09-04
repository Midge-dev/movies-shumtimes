package com.moviesshumtimes.tv.ui.common

import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

@Suppress("DEPRECATION")
private object NoOpBringIntoViewResponder : BringIntoViewResponder {
    override fun calculateRectForParent(localRect: Rect): Rect = Rect.Zero

    override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
}

@Suppress("DEPRECATION")
fun Modifier.suppressAncestorBringIntoView(): Modifier = bringIntoViewResponder(NoOpBringIntoViewResponder)
