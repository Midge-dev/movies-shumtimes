package com.moviesshumtimes.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviesshumtimes.tv.data.plex.PlexEpisode
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.ui.common.ShumArtwork
import com.moviesshumtimes.tv.ui.common.WatchTogetherIcon
import com.moviesshumtimes.tv.ui.kit.ShumButton
import com.moviesshumtimes.tv.ui.kit.ShumOutlinedButton
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow

private const val HERO_HEIGHT_DP = 420

@Composable
fun EpisodeDetailScreen(
    server: PlexServer,
    showTitle: String,
    episode: PlexEpisode,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onWatchTogether: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(episode.ratingKey) {
        runCatching { primaryFocus.requestFocus() }
    }

    val hasResume = (episode.viewOffset ?: 0L) > 0L
    val kicker = buildString {
        append(showTitle)
        episode.parentIndex?.let { append(" · Season $it") }
        episode.index?.let { append(" · Episode $it") }
    }
    val metaLine = buildList {
        episode.originallyAvailableAt?.let { add(it) }
        episode.duration?.let { add(formatRuntime(it)) }
        if (hasResume) {
            val remainingMs = (episode.duration ?: 0L) - (episode.viewOffset ?: 0L)
            if (remainingMs > 0) add("${formatRuntime(remainingMs)} left")
        }
    }.joinToString(" · ")

    Box(modifier = Modifier.fillMaxSize().height(HERO_HEIGHT_DP.dp)) {
        ShumArtwork(
            model = PlexImageUrl.of(server, episode.thumb),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            noiseOpacity = 0.3f,
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, AppScrim)))
                .padding(48.dp),
        ) {
            Text(text = kicker, style = episodeKickerStyle, color = NeonPurpleGlow)
            Text(
                text = episode.title,
                style = ShumTypography.displaySmall,
                color = AppWhite,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (metaLine.isNotEmpty()) {
                Text(text = metaLine, color = AppWhite.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
            }
            episode.summary?.let { summary ->
                Text(text = summary, color = AppWhite, modifier = Modifier.padding(top = 16.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 24.dp),
            ) {
                ShumButton(
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(primaryFocus),
                ) {
                    Text(if (hasResume) "Resume ${formatTimecode(episode.viewOffset ?: 0L)}" else "Play from start")
                }
                if (hasResume) {
                    ShumOutlinedButton(onClick = onPlayFromStart) {
                        Text("Play from start")
                    }
                }
                ShumOutlinedButton(onClick = onWatchTogether) {
                    WatchTogetherIcon()
                    Text("Watch Together", modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

private val episodeKickerStyle = TextStyle(fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)

private fun formatRuntime(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatTimecode(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
