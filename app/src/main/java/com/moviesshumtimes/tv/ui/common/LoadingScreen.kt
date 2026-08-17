package com.moviesshumtimes.tv.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

// Full-screen "please wait" state (login handoff, connecting to a server, …)
// — spinner-plus-message replacing what used to be bare Text(), so these
// in-between moments read as "working on it" rather than a stalled screen.
@Composable
fun LoadingScreen(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppLoadingIndicator()
        Text(message)
    }
}
