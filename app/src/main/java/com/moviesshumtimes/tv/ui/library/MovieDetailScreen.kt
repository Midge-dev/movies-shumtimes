package com.moviesshumtimes.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexLibraryItem
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonBorder
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonGlow

@Composable
fun MovieDetailScreen(
    server: PlexServer,
    movie: PlexLibraryItem,
    isShow: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = PlexImageUrl.of(server, movie.art ?: movie.thumb),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .padding(48.dp),
        ) {
            Text(text = movie.title, style = MaterialTheme.typography.displaySmall, color = Color.White)
            movie.year?.let { year ->
                Text(text = year.toString(), color = Color.White, modifier = Modifier.padding(top = 8.dp))
            }
            movie.summary?.let { summary ->
                Text(text = summary, color = Color.White, modifier = Modifier.padding(top = 16.dp))
            }
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                border = neonPurpleButtonBorder(),
                glow = neonPurpleButtonGlow(),
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text(if (isShow) "View Seasons" else "Play")
            }
        }
    }
}
