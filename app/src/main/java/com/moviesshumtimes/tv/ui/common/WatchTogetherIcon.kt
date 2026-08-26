package com.moviesshumtimes.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow

// Two overlapping 14dp circles, accent drawn over glow, 5dp overlap — the
// app's mark for "a room is involved" (design spec 05b). Reused anywhere
// Watch Together / a Lobby is referenced, not just the detail-screen button.
@Composable
fun WatchTogetherIcon(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(width = 23.dp, height = 14.dp)) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(NeonPurpleGlow),
        )
        Box(
            modifier = Modifier
                .offset(x = 9.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(NeonPurple),
        )
    }
}
