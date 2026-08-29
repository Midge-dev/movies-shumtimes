package com.moviesshumtimes.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.moviesshumtimes.tv.data.plex.PlexLibraryItem
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.ui.common.ShumArtwork
import com.moviesshumtimes.tv.ui.kit.ShumCard
import com.moviesshumtimes.tv.ui.kit.ShumCardContainer
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppOnSurfaceVariant

private const val GRID_COLUMNS = 5

// Reached by pressing a cast/crew member on MovieDetail (design spec 09c) —
// intentionally a plain grid rather than LibraryScreen's full sort/filter
// UI, since a person-filtered view isn't "browsing a section" and has no
// natural section to highlight in the nav drawer.
@Composable
fun PersonFilmographyScreen(
    server: PlexServer,
    personName: String,
    items: List<PlexLibraryItem>,
    onSelectItem: (PlexLibraryItem) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(personName) {
        runCatching { firstItemFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 32.dp, top = 32.dp, end = 32.dp)) {
            Text(text = personName, style = ShumTypography.headlineMedium)
            Text(text = "${items.size} titles", color = AppOnSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                contentPadding = PaddingValues(32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, item -> item.ratingKey }) { index, item ->
                    FilmographyPoster(
                        server = server,
                        item = item,
                        onClick = { onSelectItem(item) },
                        modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilmographyPoster(
    server: PlexServer,
    item: PlexLibraryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                    model = PlexImageUrl.of(server, item.thumb),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    noiseOpacity = 0.4f,
                )
            }
        },
        title = {
            Text(text = item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp))
        },
    )
}
