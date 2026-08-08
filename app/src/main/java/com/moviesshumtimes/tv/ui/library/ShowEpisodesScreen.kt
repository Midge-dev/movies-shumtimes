package com.moviesshumtimes.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.WideCardContainer
import coil3.compose.AsyncImage
import com.moviesshumtimes.tv.data.plex.PlexEpisode
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.ui.theme.neonPurpleCardBorder
import com.moviesshumtimes.tv.ui.theme.neonPurpleCardGlow

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

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)) {
            Text(text = showTitle, style = MaterialTheme.typography.displaySmall)
            Text(text = seasonTitle, style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(episodes, key = { it.ratingKey }) { episode ->
                EpisodeRow(server = server, episode = episode, onClick = { onSelect(episode) })
            }
        }
    }
}

@Composable
private fun EpisodeRow(server: PlexServer, episode: PlexEpisode, onClick: () -> Unit) {
    val heading = episode.index?.let { "${it}. ${episode.title}" } ?: episode.title
    WideCardContainer(
        modifier = Modifier.fillMaxWidth(),
        imageCard = { interactionSource ->
            Card(
                onClick = onClick,
                interactionSource = interactionSource,
                border = neonPurpleCardBorder(),
                glow = neonPurpleCardGlow(),
                modifier = Modifier.width(160.dp).height(90.dp),
            ) {
                AsyncImage(
                    model = PlexImageUrl.of(server, episode.thumb),
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        title = { Text(text = heading, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        description = {
            episode.summary?.let { summary -> Text(text = summary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
    )
}
