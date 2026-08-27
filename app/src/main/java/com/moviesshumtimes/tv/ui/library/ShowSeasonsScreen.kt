package com.moviesshumtimes.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexSeason
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.ui.common.ShumArtwork
import com.moviesshumtimes.tv.ui.kit.ShumCard
import com.moviesshumtimes.tv.ui.kit.ShumCardContainer
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text

private const val GRID_COLUMNS = 5

@Composable
fun ShowSeasonsScreen(
    server: PlexServer,
    showTitle: String,
    seasons: List<PlexSeason>,
    onSelect: (PlexSeason) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    // See LibraryScreen's matching comment — AppNavigationDrawer's sidebar
    // is the first focusable thing in the composition, so every wrapped
    // screen needs its own explicit request or D-pad focus defaults there.
    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(seasons) {
        runCatching { firstItemFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = showTitle,
            style = ShumTypography.displaySmall,
            modifier = Modifier.padding(start = 32.dp, top = 16.dp, end = 32.dp, bottom = 24.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            contentPadding = PaddingValues(32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(seasons, key = { _, it -> it.ratingKey }) { index, season ->
                SeasonPoster(
                    server = server,
                    season = season,
                    onClick = { onSelect(season) },
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
                    staggerDelayMs = (index % GRID_COLUMNS) * 120,
                )
            }
        }
    }
}

@Composable
private fun SeasonPoster(
    server: PlexServer,
    season: PlexSeason,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    staggerDelayMs: Int = 0,
) {
    ShumCardContainer(
        modifier = Modifier.width(160.dp),
        imageCard = { interactionSource ->
            ShumCard(
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = modifier.fillMaxWidth().aspectRatio(2f / 3f),
            ) {
                ShumArtwork(
                    model = PlexImageUrl.of(server, season.thumb),
                    contentDescription = season.title,
                    modifier = Modifier.fillMaxSize(),
                    noiseOpacity = 0.4f,
                    staggerDelayMs = staggerDelayMs,
                )
            }
        },
        title = {
            // Same glow-vs-title collision fix as LibraryScreen's poster.
            Text(text = season.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp))
        },
    )
}
