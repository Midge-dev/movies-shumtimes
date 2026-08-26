package com.moviesshumtimes.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexLibraryItem
import com.moviesshumtimes.tv.data.plex.PlexOnDeckItem
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.ui.common.WatchTogetherIcon
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonBorder
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonGlow
import com.moviesshumtimes.tv.ui.theme.whiteButtonColors
import com.moviesshumtimes.tv.ui.theme.whiteOutlinedButtonColors

// Play and Watch Together are peers, not a mode toggle (design spec 05b):
// Play always goes straight to solo playback, relay untouched — it must
// never require a configured relay. Watch Together is the only path into
// the Lobby, and stays focusable even with no relay configured, routing to
// Settings instead of disabling (a disabled control gives a couch user
// nothing to act on).
@Composable
fun MovieDetailScreen(
    server: PlexServer,
    movie: PlexLibraryItem,
    isShow: Boolean,
    onBack: () -> Unit,
    onPlay: (targetRatingKey: String) -> Unit,
    onWatchTogether: (targetRatingKey: String) -> Unit,
    onSeasons: () -> Unit,
    resolveNextEpisode: suspend () -> PlexOnDeckItem?,
) {
    BackHandler(onBack = onBack)

    // See LibraryScreen's matching comment — AppNavigationDrawer's sidebar
    // is the first focusable thing in the composition, so every wrapped
    // screen needs its own explicit request or D-pad focus defaults there.
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(movie.ratingKey) {
        runCatching { playFocus.requestFocus() }
    }

    // Only shows need this — a movie's own ratingKey is always the play
    // target. Re-resolved per title since the on-deck episode differs show
    // to show; Play/Watch Together no-op until it lands (fast enough in
    // practice not to need a loading state of its own — initial focus sits
    // on Play regardless).
    var nextEpisode by remember(movie.ratingKey) { mutableStateOf<PlexOnDeckItem?>(null) }
    LaunchedEffect(movie.ratingKey) {
        if (isShow) nextEpisode = resolveNextEpisode()
    }
    val playTarget = if (isShow) nextEpisode?.ratingKey else movie.ratingKey
    val playLabel = if (isShow) {
        nextEpisode?.let { "Play S${it.parentIndex}E${it.index}" } ?: "Play"
    } else {
        "Play"
    }

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
                .background(Brush.verticalGradient(listOf(Color.Transparent, AppScrim)))
                .padding(48.dp),
        ) {
            Text(text = movie.title, style = MaterialTheme.typography.displaySmall, color = AppWhite)
            movie.year?.let { year ->
                Text(text = year.toString(), color = AppWhite, modifier = Modifier.padding(top = 8.dp))
            }
            movie.summary?.let { summary ->
                Text(text = summary, color = AppWhite, modifier = Modifier.padding(top = 16.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Button(
                    onClick = { playTarget?.let(onPlay) },
                    colors = whiteButtonColors(),
                    border = neonPurpleButtonBorder(),
                    glow = neonPurpleButtonGlow(),
                    modifier = Modifier.focusRequester(playFocus),
                ) {
                    Text(playLabel)
                }
                OutlinedButton(
                    onClick = { playTarget?.let(onWatchTogether) },
                    colors = whiteOutlinedButtonColors(),
                    border = neonPurpleButtonBorder(),
                    glow = neonPurpleButtonGlow(),
                ) {
                    WatchTogetherIcon()
                    Text("Watch Together", modifier = Modifier.padding(start = 12.dp))
                }
                if (isShow) {
                    OutlinedButton(
                        onClick = onSeasons,
                        colors = whiteOutlinedButtonColors(),
                        border = neonPurpleButtonBorder(),
                        glow = neonPurpleButtonGlow(),
                    ) {
                        Text("Seasons")
                    }
                }
            }
        }
    }
}
