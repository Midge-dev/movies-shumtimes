package com.moviesshumtimes.tv.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple

// Purely decorative inside ShumListItem (which owns the actual click/focus
// row) — a filled dot in an outer ring when selected, empty ring otherwise.
@Composable
fun ShumRadioButton(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .border(2.dp, AppWhite, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(NeonPurple))
        }
    }
}
