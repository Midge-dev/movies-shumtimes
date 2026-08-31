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
