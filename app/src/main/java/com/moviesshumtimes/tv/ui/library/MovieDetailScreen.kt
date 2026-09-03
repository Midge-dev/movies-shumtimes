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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.data.plex.PlexHub
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexLibraryItem
import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.plex.PlexOnDeckItem
import com.moviesshumtimes.tv.data.plex.PlexPerson
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.ui.common.ShumArtwork
import com.moviesshumtimes.tv.ui.common.WatchTogetherIcon
import com.moviesshumtimes.tv.ui.kit.ShumButton
import com.moviesshumtimes.tv.ui.kit.ShumOutlinedButton
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.AppWhite
import kotlinx.coroutines.flow.first

private const val HERO_HEIGHT_DP = 420

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
    loadDetail: suspend () -> PlexMovieDetail?,
    loadRelatedHubs: suspend () -> List<PlexHub>,
    loadByActor: suspend (actorId: Long) -> List<PlexLibraryItem>,
    onSelectRelated: (PlexOnDeckItem) -> Unit,
    onSelectPerson: (PlexPerson) -> Unit,
) {
    BackHandler(onBack = onBack)

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(movie.ratingKey) {
        runCatching { playFocus.requestFocus() }
    }

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

    var detail by remember(movie.ratingKey) { mutableStateOf<PlexMovieDetail?>(null) }
    LaunchedEffect(movie.ratingKey) { detail = loadDetail() }

    var relatedHubs by remember(movie.ratingKey) { mutableStateOf<List<PlexHub>>(emptyList()) }
    LaunchedEffect(movie.ratingKey) { relatedHubs = loadRelatedHubs() }

    var coStarRows by remember(movie.ratingKey) { mutableStateOf<List<Pair<PlexPerson, List<PlexLibraryItem>>>>(emptyList()) }
    LaunchedEffect(detail, relatedHubs) {
        val roles = detail?.roles ?: return@LaunchedEffect
        val autoHubNames = relatedHubs.mapNotNull { hub ->
            hub.title.removePrefix("More with ").takeIf { it != hub.title }
        }.toSet()
        val candidates = roles
            .filter { it.tag !in autoHubNames }
            .distinctBy { it.id ?: it.tag }
            .take(2)
        coStarRows = candidates.mapNotNull { person ->
            val actorId = person.id ?: return@mapNotNull null
            val items = loadByActor(actorId).filter { it.ratingKey != movie.ratingKey }
            if (items.size >= 3) person to items else null
        }
    }

    val listState = rememberLazyListState()
    var heroFocusToken by remember { mutableStateOf(0) }
    LaunchedEffect(heroFocusToken) {
        if (heroFocusToken > 0) listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            MovieHero(
                server = server,
                movie = movie,
                playLabel = playLabel,
                playFocus = playFocus,
                isShow = isShow,
                onPlay = { playTarget?.let(onPlay) },
                onWatchTogether = { playTarget?.let(onWatchTogether) },
                onSeasons = onSeasons,
                onActionButtonFocused = { heroFocusToken++ },
            )
        }
        detail?.let { d ->
            item {
                CastCrewRow(
                    server = server,
                    cast = d.roles,
                    crew = d.directors + d.writers,
                    onSelectPerson = onSelectPerson,
                )
            }
            item {
                RatingsReviewsSection(
                    rating = d.rating,
                    audienceRating = d.audienceRating,
                    ratingImage = d.ratingImage,
                    audienceRatingImage = d.audienceRatingImage,
                    reviews = d.reviews,
                )
            }
        }
        items(relatedHubs, key = { it.hubIdentifier ?: it.title }) { hub ->
            PosterRow(title = hub.title, items = hub.items, server = server, onClick = onSelectRelated)
        }
        items(coStarRows, key = { (person, _) -> person.id ?: person.tag }) { (person, items) ->
            PosterRow(
                title = "More with ${person.tag}",
                items = items.map {
                    PlexOnDeckItem(ratingKey = it.ratingKey, type = it.type ?: "movie", title = it.title, thumb = it.thumb)
                },
                server = server,
                onClick = onSelectRelated,
            )
        }
        item { Box(modifier = Modifier.height(48.dp)) }
    }
}

@Composable
private fun MovieHero(
    server: PlexServer,
    movie: PlexLibraryItem,
    playLabel: String,
    playFocus: FocusRequester,
    isShow: Boolean,
    onPlay: () -> Unit,
    onWatchTogether: () -> Unit,
    onSeasons: () -> Unit,
    onActionButtonFocused: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(HERO_HEIGHT_DP.dp)) {
        ShumArtwork(
            model = PlexImageUrl.of(server, movie.art ?: movie.thumb),
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
            Text(text = movie.title, style = ShumTypography.displaySmall, color = AppWhite)
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
                ShumButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .focusRequester(playFocus)
                        .onFocusChanged { if (it.isFocused) onActionButtonFocused() },
                ) {
                    Text(playLabel)
                }
                ShumOutlinedButton(
                    onClick = onWatchTogether,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onActionButtonFocused() },
                ) {
                    WatchTogetherIcon()
                    Text("Watch Together", modifier = Modifier.padding(start = 12.dp))
                }
                if (isShow) {
                    ShumOutlinedButton(
                        onClick = onSeasons,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) onActionButtonFocused() },
                    ) {
                        Text("Seasons")
                    }
                }
            }
        }
    }
}
