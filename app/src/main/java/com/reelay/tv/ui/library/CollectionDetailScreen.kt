package com.reelay.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelay.tv.data.plex.PlexCollection
import com.reelay.tv.data.plex.PlexImageUrl
import com.reelay.tv.data.plex.PlexLibraryItem
import com.reelay.tv.data.plex.PlexServer
import com.reelay.tv.ui.common.Artwork
import com.reelay.tv.ui.kit.Card
import com.reelay.tv.ui.kit.CardContainer
import com.reelay.tv.ui.kit.Typography
import com.reelay.tv.ui.kit.Text
import com.reelay.tv.ui.theme.AppOnSurfaceVariant

private const val GRID_COLUMNS = 5

@Composable
fun CollectionDetailScreen(
    server: PlexServer,
    collection: PlexCollection,
    items: List<PlexLibraryItem>,
    onSelectItem: (PlexLibraryItem) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(collection.ratingKey) {
        runCatching { firstItemFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(start = 32.dp, top = 32.dp, end = 32.dp),
        ) {
            Text(text = collection.title, style = Typography.headlineMedium)
            Text(
                text = "${items.size} title${if (items.size == 1) "" else "s"}",
                color = AppOnSurfaceVariant,
            )
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
                    CollectionItemPoster(
                        server = server,
                        item = item,
                        onClick = { onSelectItem(item) },
                        modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
                    )
                }
            }
            if (items.isEmpty()) {
                Text("No titles found in this collection.", modifier = Modifier.padding(32.dp))
            }
        }
    }
}

@Composable
private fun CollectionItemPoster(
    server: PlexServer,
    item: PlexLibraryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardContainer(
        modifier = Modifier.width(160.dp),
        imageCard = { interactionSource ->
            Card(
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = modifier.fillMaxWidth().aspectRatio(2f / 3f),
            ) {
                Artwork(
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
