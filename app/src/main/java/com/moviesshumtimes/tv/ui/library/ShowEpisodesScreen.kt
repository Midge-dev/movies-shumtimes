package com.moviesshumtimes.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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

    // See LibraryScreen's matching comment — AppNavigationDrawer's sidebar
    // is the first focusable thing in the composition, so every wrapped
    // screen needs its own explicit request or D-pad focus defaults there.
    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(episodes) {
        runCatching { firstItemFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 32.dp, top = 16.dp, end = 32.dp, bottom = 24.dp)) {
            Text(text = showTitle, style = MaterialTheme.typography.displaySmall)
            Text(text = seasonTitle, style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            // Episode summaries commonly wrap to 2 lines — 16dp between rows
            // looked cramped once that text filled out (reported as a
            // spacing issue), same "denser row content needs more gap"
            // pattern as the other poster/card lists in this app.
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

// WideCardContainer (tv-material3) doesn't expose a gap parameter between
// its imageCard and title/description slots — the text sat right against
// the thumbnail's edge (reported as overlapping). Same move as elsewhere in
// this app when a library component doesn't expose what's needed: dropped
// for a plain Row with an explicit horizontal gap.
@Composable
private fun EpisodeRow(server: PlexServer, episode: PlexEpisode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val heading = episode.index?.let { "${it}. ${episode.title}" } ?: episode.title
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Card(
            onClick = onClick,
            border = neonPurpleCardBorder(),
            glow = neonPurpleCardGlow(),
            modifier = modifier.width(160.dp).height(90.dp),
        ) {
            AsyncImage(
                model = PlexImageUrl.of(server, episode.thumb),
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
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
