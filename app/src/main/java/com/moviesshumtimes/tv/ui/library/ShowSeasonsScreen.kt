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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexSeason
import com.moviesshumtimes.tv.data.plex.PlexServer

@Composable
fun ShowSeasonsScreen(
    server: PlexServer,
    showTitle: String,
    seasons: List<PlexSeason>,
    onSelect: (PlexSeason) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = showTitle,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(seasons, key = { it.ratingKey }) { season ->
                SeasonPoster(server = server, season = season, onClick = { onSelect(season) })
            }
        }
    }
}

@Composable
private fun SeasonPoster(server: PlexServer, season: PlexSeason, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(160.dp)) {
        Column {
            AsyncImage(
                model = PlexImageUrl.of(server, season.thumb),
                contentDescription = season.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            Text(
                text = season.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
