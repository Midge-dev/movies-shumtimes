package com.reelay.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.foundation.BorderStroke
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
import com.reelay.tv.data.plex.PlexHub
import com.reelay.tv.data.plex.PlexImageUrl
import com.reelay.tv.data.plex.PlexLibraryItem
import com.reelay.tv.data.plex.PlexMovieDetail
import com.reelay.tv.data.plex.PlexOnDeckItem
import com.reelay.tv.data.plex.PlexPerson
import com.reelay.tv.data.plex.PlexServer
import com.reelay.tv.ui.common.Artwork
import com.reelay.tv.ui.common.WatchTogetherIcon
import com.reelay.tv.ui.common.WatchlistButton
import com.reelay.tv.ui.kit.Icon
import com.reelay.tv.ui.kit.Border
import com.reelay.tv.ui.kit.Button
import com.reelay.tv.ui.kit.IconButton
import com.reelay.tv.ui.kit.OutlinedButton
import com.reelay.tv.ui.kit.Typography
import com.reelay.tv.ui.kit.Text
import com.reelay.tv.ui.theme.AppDimBorder
import com.reelay.tv.ui.theme.AppScrim
import com.reelay.tv.ui.theme.AppWhite
import com.reelay.tv.ui.theme.NeonPurpleGradient
import kotlinx.coroutines.flow.first

private const val HERO_HEIGHT_DP = 420

private val restartButtonBorder = Border(
    idle = BorderStroke(2.dp, AppDimBorder),
    focused = BorderStroke(2.dp, NeonPurpleGradient),
)

@Composable
fun MovieDetailScreen(
    server: PlexServer,
    movie: PlexLibraryItem,
    isShow: Boolean,
    onBack: () -> Unit,
    onPlay: (targetRatingKey: String) -> Unit,
    onWatchTogether: (targetRatingKey: String) -> Unit,
    onRestartTogether: (targetRatingKey: String) -> Unit,
    onSeasons: () -> Unit,
    isOnWatchlist: (String?) -> Boolean,
    onToggleWatchlist: (String?) -> Unit,
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

    var detail by remember(movie.ratingKey) { mutableStateOf<PlexMovieDetail?>(null) }
    LaunchedEffect(movie.ratingKey) { detail = loadDetail() }

    val hasResume = !isShow && (detail?.viewOffset ?: 0L) > 0L
    val playLabel = if (isShow) {
        nextEpisode?.let { "Play S${it.parentIndex}E${it.index}" } ?: "Play"
    } else if (hasResume) {
        "Continue"
    } else {
        "Play"
    }
    val watchTogetherLabel = if (hasResume) "Continue Together" else "Watch Together"

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
                summary = detail?.summary ?: movie.summary,
                playLabel = playLabel,
                watchTogetherLabel = watchTogetherLabel,
                showRestart = hasResume,
                playFocus = playFocus,
                isShow = isShow,
                onPlay = { playTarget?.let(onPlay) },
                onWatchTogether = { playTarget?.let(onWatchTogether) },
                onRestartTogether = { playTarget?.let(onRestartTogether) },
                onSeasons = onSeasons,
                isOnWatchlist = isOnWatchlist(detail?.guid),
                onToggleWatchlist = { onToggleWatchlist(detail?.guid) },
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
    summary: String?,
    playLabel: String,
    watchTogetherLabel: String,
    showRestart: Boolean,
    playFocus: FocusRequester,
    isShow: Boolean,
    onPlay: () -> Unit,
    onWatchTogether: () -> Unit,
    onRestartTogether: () -> Unit,
    onSeasons: () -> Unit,
    isOnWatchlist: Boolean,
    onToggleWatchlist: () -> Unit,
    onActionButtonFocused: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(HERO_HEIGHT_DP.dp)) {
        Artwork(
            model = PlexImageUrl.of(server, movie.art ?: movie.thumb),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            noiseOpacity = 0.3f,
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to AppScrim.copy(alpha = 0.8f),
                        1f to AppScrim,
                    ),
                )
                .padding(48.dp),
        ) {
            Text(text = movie.title, style = Typography.displaySmall, color = AppWhite)
            movie.year?.let { year ->
                Text(text = year.toString(), color = AppWhite, modifier = Modifier.padding(top = 8.dp))
            }
            summary?.let {
                Text(text = it, color = AppWhite, modifier = Modifier.padding(top = 16.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .focusRequester(playFocus)
                        .onFocusChanged { if (it.isFocused) onActionButtonFocused() },
                ) {
                    Text(playLabel)
                }
                OutlinedButton(
                    onClick = onWatchTogether,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onActionButtonFocused() },
                ) {
                    WatchTogetherIcon()
                    Text(watchTogetherLabel, modifier = Modifier.padding(start = 12.dp))
                }
                if (!isShow) {
                    WatchlistButton(
                        isOnWatchlist = isOnWatchlist,
                        onClick = onToggleWatchlist,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) onActionButtonFocused() },
                    )
                }
                if (isShow) {
                    OutlinedButton(
                        onClick = onSeasons,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) onActionButtonFocused() },
                    ) {
                        Text("Seasons")
                    }
                }
                if (showRestart) {
                    IconButton(
                        onClick = onRestartTogether,
                        border = restartButtonBorder,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) onActionButtonFocused() },
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = "Restart together from the beginning", tint = AppWhite)
                    }
                }
                if (isShow) {
                    WatchlistButton(
                        isOnWatchlist = isOnWatchlist,
                        onClick = onToggleWatchlist,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) onActionButtonFocused() },
                    )
                }
            }
        }
    }
}
