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

// Design spec 09c: below the hero, every section self-hides when its data
// is empty or hasn't arrived yet — no headers over empty rows, no "No
// information available", no skeletons that never resolve. Personal-media
// items are expected to come back with none of this data; the screen simply
// ends after the hero, which is the normal case, not a broken one.
private const val HERO_HEIGHT_DP = 420

// Play and Watch Together are peers, not a mode toggle (design spec 05b):
// Play always goes straight to solo playback, relay untouched — it must
// never require a configured relay. Watch Together is the only path into
// the Lobby, and stays focusable even with no relay configured, routing to
// Settings instead of disabling (a disabled control gives a couch user
// nothing to act on).
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

    // See LibraryScreen's matching comment — AppNavigationDrawer's sidebar
    // is the first focusable thing in the composition, so every wrapped
    // screen needs its own explicit request or D-pad focus defaults there.
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(movie.ratingKey) {
        runCatching { playFocus.requestFocus() }
    }

    // Only shows need this — a movie's own ratingKey is always the play
    // target. Re-resolved per title since the on-deck episode differs show
    // to show; Play/Watch Together no-op until it lands (fast enough in
    // practice not to need a loading state of its own — initial focus sits
    // on Play regardless).
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

    // Cast/crew/ratings/reviews and the related/more-with hubs are a
    // separate fetch from the grid item already on hand (that item only has
    // the fields needed for a poster) — same "pass a suspend loader,
    // populate lazily inside the screen" shape already used for
    // resolveNextEpisode above. null/empty means "not arrived yet" until
    // the effect resolves, at which point each section either renders or
    // stays hidden for good.
    var detail by remember(movie.ratingKey) { mutableStateOf<PlexMovieDetail?>(null) }
    LaunchedEffect(movie.ratingKey) { detail = loadDetail() }

    var relatedHubs by remember(movie.ratingKey) { mutableStateOf<List<PlexHub>>(emptyList()) }
    LaunchedEffect(movie.ratingKey) { relatedHubs = loadRelatedHubs() }

    // Plex auto-generates a same-actor hub for whichever cast member it
    // picks (confirmed this session: not reliably the top-billed/index-0
    // entry) — assuming "index 0" caused the exact same actor to get both
    // the auto hub and a manually-built one, showing as back-to-back
    // duplicate "More with <name>" rows. Instead, read the hub titles Plex
    // actually returned and skip whoever's already covered there. Also
    // dedupe by person before picking, since the same cast member can
    // legitimately appear twice in Role[] (e.g. a dual-credited cameo).
    // Kept to 2 manual rows, each only shown with 3+ titles (a one-poster
    // row is worse than no row); the current movie itself is filtered out
    // of every result.
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

    // The LazyColumn's default focus-scroll only brings the focused button
    // itself into view, not the whole 420dp hero item it lives near the
    // bottom of — scrolling back up from the sections below left the
    // backdrop half cut off above the title. Snap all the way to item 0
    // whenever focus re-enters the hero's action row instead.
    //
    // Driven through a LaunchedEffect rather than launching a coroutine
    // straight from the onFocusChanged callback: calling
    // scope.launch { listState.animateScrollToItem(0) } inline raced
    // against the lazy list's own built-in "bring the newly-focused child
    // into view" adjustment, and which one won depended on which button
    // was focused (Play — tagged with an explicit FocusRequester used for
    // the screen's initial focus — consistently lost the race; Watch
    // Together didn't). A non-animated scrollToItem inside a LaunchedEffect
    // runs after that layout pass instead of fighting it mid-frame.
    val listState = rememberLazyListState()
    var heroActionsFocused by remember { mutableStateOf(false) }
    LaunchedEffect(heroActionsFocused) {
        if (heroActionsFocused) listState.scrollToItem(0)
    }

    // Design spec 09c: 26-30dp gap between info sections.
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
                onActionRowFocusChanged = { hasFocus -> heroActionsFocused = hasFocus },
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
    onActionRowFocusChanged: (hasFocus: Boolean) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(HERO_HEIGHT_DP.dp)) {
        ShumArtwork(
            model = PlexImageUrl.of(server, movie.art ?: movie.thumb),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            // Design spec 05c: grain drops to 30% for a full-bleed backdrop
            // this size, since it sits directly under the title/actions
            // scrim (the Column drawn right after this, still on top).
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
                modifier = Modifier
                    .padding(top = 24.dp)
                    // onFocusChanged reports hasFocus=true whenever any
                    // descendant (Play/Watch Together/Seasons) is focused,
                    // even though this Row has no focus target of its own.
                    .onFocusChanged { onActionRowFocusChanged(it.hasFocus) },
            ) {
                ShumButton(
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(playFocus),
                ) {
                    Text(playLabel)
                }
                ShumOutlinedButton(onClick = onWatchTogether) {
                    WatchTogetherIcon()
                    Text("Watch Together", modifier = Modifier.padding(start = 12.dp))
                }
                if (isShow) {
                    ShumOutlinedButton(onClick = onSeasons) {
                        Text("Seasons")
                    }
                }
            }
        }
    }
}
