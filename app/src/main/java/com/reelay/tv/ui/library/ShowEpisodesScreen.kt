package com.reelay.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelay.tv.data.plex.PlexEpisode
import com.reelay.tv.data.plex.PlexImageUrl
import com.reelay.tv.data.plex.PlexServer
import com.reelay.tv.ui.common.Artwork
import com.reelay.tv.ui.kit.Card
import com.reelay.tv.ui.kit.Typography
import com.reelay.tv.ui.kit.Text
import com.reelay.tv.ui.theme.AppScrim
import com.reelay.tv.ui.theme.NeonPurple

@Composable
fun ShowEpisodesScreen(
    server: PlexServer,
    showTitle: String,
    seasonTitle: String,
    episodes: List<PlexEpisode>,
    onSelect: (PlexEpisode) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(episodes) {
        runCatching { firstItemFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 32.dp, top = 16.dp, end = 32.dp, bottom = 24.dp)) {
            Text(text = showTitle, style = Typography.displaySmall)
            Text(text = seasonTitle, style = Typography.bodyLarge)
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(episodes, key = { _, it -> it.ratingKey }) { index, episode ->
                EpisodeRow(
                    server = server,
                    episode = episode,
                    onClick = { onSelect(episode) },
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
                )
            }
        }
    }
}

private fun episodeProgressFraction(episode: PlexEpisode): Float {
    val duration = episode.duration?.takeIf { it > 0 } ?: return 0f
    return ((episode.viewOffset ?: 0L).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun EpisodeRow(server: PlexServer, episode: PlexEpisode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val heading = episode.index?.let { "${it}. ${episode.title}" } ?: episode.title
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Card(
            onClick = onClick,
            modifier = modifier.width(160.dp).height(90.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Artwork(
                    model = PlexImageUrl.of(server, episode.thumb),
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                )
                val progress = episodeProgressFraction(episode)
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(AppScrim.copy(alpha = 0.4f)),
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).background(NeonPurple))
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            episode.summary?.let { summary ->
                Text(
                    text = summary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
