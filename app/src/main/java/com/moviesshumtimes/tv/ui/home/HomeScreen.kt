package com.moviesshumtimes.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexLibraryItem
import com.moviesshumtimes.tv.data.plex.PlexOnDeckItem
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.data.settings.RelayEntry
import com.moviesshumtimes.tv.sync.RelayRoomSummary
import com.moviesshumtimes.tv.ui.common.ShumArtwork
import com.moviesshumtimes.tv.ui.kit.ShumButton
import com.moviesshumtimes.tv.ui.kit.ShumCard
import com.moviesshumtimes.tv.ui.kit.ShumCardContainer
import com.moviesshumtimes.tv.ui.kit.ShumOutlinedButton
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.kit.drawGlow
import com.moviesshumtimes.tv.ui.theme.AppOnSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.AppSurface
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TYPE_EPISODE = "episode"

// Loading-placeholder roll-bar phase repeats every this-many row items —
// design spec 05c: without staggering, every simultaneously-loading card in
// a row pulses its roll-bar band in visible unison.
private const val ROW_STAGGER_PERIOD = 6

@Composable
fun HomeScreen(
    server: PlexServer,
    onDeck: List<PlexOnDeckItem>,
    recentlyAdded: List<PlexLibraryItem>,
    suggestions: List<PlexOnDeckItem>,
    liveRooms: List<MergedRoom>,
    // Which room (if any) this device currently occupies — marks that
    // room's card Rejoin instead of Join. Null whenever not in a room at
    // all (including "not connected to any relay").
    myRoomId: String?,
    // Every room this device currently hosts — independent of myRoomId,
    // since each stays populated even after the live connection to it is
    // gone (see MainActivity's hostedRoomIds/RelayIdentity.hostedRooms).
    // Surfaces one "End session" control per hosted room that's still live.
    hostedRoomIds: Set<String>,
    onEndSession: suspend (MergedRoom) -> Boolean,
    onSelectRoom: (MergedRoom) -> Unit,
    onResume: (PlexOnDeckItem) -> Unit,
    onRemove: (PlexOnDeckItem) -> Unit,
    onSelectRecentlyAdded: (PlexLibraryItem) -> Unit,
    onSelectSuggestion: (PlexOnDeckItem) -> Unit,
) {
    val firstItemFocus = remember { FocusRequester() }
    // AppNavigationDrawer's sidebar is the first focusable thing in the
    // composition — same pattern as every other screen it wraps (see
    // LibraryScreen's matching comment). Only one row actually gets the
    // requester: whichever is first non-empty, in display order — Watch
    // Together (design spec 09b: "takes initial focus when it appears"),
    // then Continue Watching, then Recently Added, then Suggestions — so
    // focus always lands on the first real card on screen, not wherever
    // Continue Watching happens to be even when it's empty.
    // Nesting a LazyRow inside a LazyColumn item (needed for the four-row
    // layout below) means the target poster isn't necessarily composed yet
    // on the very first frame this effect runs — confirmed on-device: a
    // single un-retried requestFocus() here silently failed every time,
    // leaving focus stuck in the sidebar with D-pad Down never reaching the
    // content at all. Retrying across a few frames covers that gap.
    val watchTogetherGetsFocus = liveRooms.isNotEmpty()
    val continueWatchingGetsFocus = !watchTogetherGetsFocus && onDeck.isNotEmpty()
    val recentlyAddedGetsFocus = !watchTogetherGetsFocus && !continueWatchingGetsFocus && recentlyAdded.isNotEmpty()
    val suggestionsGetsFocus =
        !watchTogetherGetsFocus && !continueWatchingGetsFocus && !recentlyAddedGetsFocus && suggestions.isNotEmpty()
    // Design spec 09b: "scroll Home to the top ... focus to the first card"
    // — a room can appear while the user is scrolled down browsing
    // Suggestions (rooms are polled every 5s regardless of scroll position),
    // so appearing focused isn't enough on its own if the row itself is
    // still off-screen above the viewport. Named state so this effect can
    // drive it explicitly; every other row already relied on the LazyColumn's
    // own focus-driven scroll-into-view, which only reaches a target that's
    // already been given focus — this covers getting *back to the row itself*
    // first.
    val homeListState = rememberLazyListState()
    // Keyed on watchTogetherGetsFocus (a derived boolean), not the raw
    // liveRooms list — liveRooms is polled every 5s and gets a new list
    // instance each time even when nothing changed, which would otherwise
    // re-run this focus grab on every poll tick and yank focus back to the
    // top of the screen out from under whatever the user had scrolled to.
    LaunchedEffect(watchTogetherGetsFocus, onDeck, recentlyAdded, suggestions) {
        if (watchTogetherGetsFocus) {
            runCatching { homeListState.animateScrollToItem(0) }
        }
        repeat(5) {
            if (runCatching { firstItemFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos {}
        }
    }

    // LazyColumn, not a plain Column — with three stacked rows this can
    // easily exceed one screen's height, and a plain Column neither scrolls
    // nor brings off-screen focus targets into view, which is what made the
    // screen feel "static" (D-pad down had nowhere visible to go once
    // Continue Watching's row scrolled past the bottom edge). LazyColumn's
    // focus-driven scroll-into-view is what makes Down actually work here.
    LazyColumn(
        state = homeListState,
        modifier = Modifier.fillMaxSize(),
        // Without this the Suggestions row (always last) sat flush against
        // the screen edge — no row after it to supply trailing space, unlike
        // Continue Watching/Recently Added which get breathing room from the
        // row below them.
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            WatchTogetherRow(
                server = server,
                rooms = liveRooms,
                myRoomId = myRoomId,
                hostedRoomIds = hostedRoomIds,
                onEndSession = onEndSession,
                onSelectRoom = onSelectRoom,
                firstCardFocusRequester = if (watchTogetherGetsFocus) firstItemFocus else null,
            )
        }

        item {
            Text(
                text = "Continue Watching",
                style = ShumTypography.titleLarge,
                modifier = Modifier.padding(start = 32.dp, top = 32.dp, bottom = 16.dp),
            )
            if (onDeck.isEmpty()) {
                Text(
                    text = "Nothing in progress right now.",
                    modifier = Modifier.padding(start = 32.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(onDeck, key = { _, item -> item.ratingKey }) { index, item ->
                        ContinueWatchingPoster(
                            server = server,
                            item = item,
                            onResume = { onResume(item) },
                            onRemove = { onRemove(item) },
                            modifier = if (index == 0 && continueWatchingGetsFocus) {
                                Modifier.focusRequester(firstItemFocus)
                            } else {
                                Modifier
                            },
                            staggerDelayMs = (index % ROW_STAGGER_PERIOD) * 120,
                        )
                    }
                }
            }
        }

        item {
            HomeRow(title = "Recently Added", items = recentlyAdded, key = { it.ratingKey }) { item, index ->
                RecentlyAddedPoster(
                    server = server,
                    item = item,
                    onClick = { onSelectRecentlyAdded(item) },
                    modifier = if (index == 0 && recentlyAddedGetsFocus) Modifier.focusRequester(firstItemFocus) else Modifier,
                    staggerDelayMs = (index % ROW_STAGGER_PERIOD) * 120,
                )
            }
        }

        item {
            HomeRow(title = "Suggestions", items = suggestions, key = { it.ratingKey }) { item, index ->
                SuggestionPoster(
                    server = server,
                    item = item,
                    onClick = { onSelectSuggestion(item) },
                    modifier = if (index == 0 && suggestionsGetsFocus) Modifier.focusRequester(firstItemFocus) else Modifier,
                    staggerDelayMs = (index % ROW_STAGGER_PERIOD) * 120,
                )
            }
        }
    }
}

// Shared shell for Recently Added / Suggestions — unlike Continue Watching,
// an empty row here is unremarkable (e.g. a brand new server, or nothing
// matched the Suggestions hub yet) and isn't worth an explanatory message,
// so it's skipped entirely rather than rendering an empty-state row.
@Composable
private fun <T> HomeRow(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    itemContent: @Composable (T, Int) -> Unit,
) {
    if (items.isEmpty()) return
    Text(
        text = title,
        style = ShumTypography.titleLarge,
        modifier = Modifier.padding(start = 32.dp, top = 32.dp, bottom = 28.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item -> itemContent(item, index) }
    }
}

// Design spec 09b: how many rooms fit before the "+N more rooms" tile —
// beyond this, all rooms are still real LazyRow items (reachable by normal
// D-pad scrolling), the tile is a visual/paging shortcut, not a hard cutoff.
private const val VISIBLE_ROOM_CARDS = 3

// Design spec 09d: a room paired with which configured relay it came from —
// merged client-side, since the relay itself has no notion of any other
// relay. The same movie live on two relays is two distinct MergedRooms
// (different relay.id), not deduplicated by title.
data class MergedRoom(val relay: RelayEntry, val room: RelayRoomSummary)

// Self-hides entirely when no room is live — never an empty state, never a
// placeholder, same convention as every other row on this screen.
@Composable
private fun WatchTogetherRow(
    server: PlexServer,
    rooms: List<MergedRoom>,
    myRoomId: String?,
    hostedRoomIds: Set<String>,
    onEndSession: suspend (MergedRoom) -> Boolean,
    onSelectRoom: (MergedRoom) -> Unit,
    firstCardFocusRequester: FocusRequester?,
) {
    if (rooms.isEmpty()) return
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val relayCount = remember(rooms) { rooms.map { it.relay.id }.distinct().size }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 32.dp, top = 32.dp, bottom = 16.dp),
        ) {
            Box(modifier = Modifier.width(8.dp).height(8.dp).background(NeonPurple, shape = RoundedCornerShape(50)))
            Text(text = "Watch Together", style = ShumTypography.titleLarge)
            Text(
                text = "${rooms.size} room${if (rooms.size == 1) "" else "s"} live" +
                    " · $relayCount relay${if (relayCount == 1) "" else "s"}",
                color = AppOnSurfaceVariant,
            )
        }
        // Design spec section 11: a room you host is the same card everyone
        // else sees, with one more button — no second row type. Supersedes
        // an earlier draft of this section that put hosted rooms in their
        // own strip above the directory.
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            itemsIndexed(rooms, key = { _, merged -> "${merged.relay.id}:${merged.room.roomId}" }) { index, merged ->
                RoomCard(
                    server = server,
                    merged = merged,
                    isMine = merged.room.roomId == myRoomId,
                    isHosted = merged.room.roomId in hostedRoomIds,
                    onClick = { onSelectRoom(merged) },
                    onEndSession = onEndSession,
                    joinFocusRequester = if (index == 0) firstCardFocusRequester else null,
                )
            }
            if (rooms.size > VISIBLE_ROOM_CARDS) {
                item {
                    OverflowTile(
                        count = rooms.size - VISIBLE_ROOM_CARDS,
                        onClick = { scope.launch { listState.animateScrollToItem(VISIBLE_ROOM_CARDS) } },
                    )
                }
            }
        }
    }
}

// Design spec section 11: identical 300dp card for every room, hosted or
// not — same artwork, host line, relay line, focus border/glow/scale (all
// from ShumArtwork/ShumButton's own established treatment; nothing card-wide
// is hand-rolled here). A room you host reads as yours from three things:
// the "You're hosting" badge, "You hosting" replacing the host line, and a
// second action. Join keeps its place, weight and label on every card,
// including your own, so the first thing under focus is always the way in,
// never the way to end it. Supersedes an earlier draft that made the poster
// itself the click target — every action is now its own focusable pill,
// consistent with section 10's "every control is a focusable surface".
@Composable
private fun RoomCard(
    server: PlexServer,
    merged: MergedRoom,
    isMine: Boolean,
    isHosted: Boolean,
    onClick: () -> Unit,
    onEndSession: suspend (MergedRoom) -> Boolean,
    modifier: Modifier = Modifier,
    joinFocusRequester: FocusRequester? = null,
) {
    val room = merged.room
    val scope = rememberCoroutineScope()
    val full = room.occupants >= room.maxSeats
    val joinLabel = when {
        full -> "Full"
        isMine -> "Rejoin"
        else -> "Join"
    }
    // End session ends the room immediately regardless of who's seated in
    // it — no confirm step. Failure is quiet: the card stays, its dot
    // greys, the button reads "Can't reach relay" for four seconds, then
    // reverts. Nothing is queued — an unreachable relay isn't holding the
    // room open either.
    var failed by remember(room.roomId) { mutableStateOf(false) }

    LaunchedEffect(failed) {
        if (failed) {
            delay(4_000)
            failed = false
        }
    }

    fun endNow() {
        scope.launch { if (!onEndSession(merged)) failed = true }
    }

    // "Identical 300dp card for every room, hosted or not — same artwork,
    // same host line, same relay line, same focus border, glow and 1.04
    // scale" (design spec section 11). The card is a focusGroup of several
    // independent button pills now rather than one FocusableSurface, so this
    // reaches for the same glow/scale by hand rather than through
    // FocusableSurface — same idiom the old HostedRoomCard used, and for the
    // same reason (ContinueWatchingPoster's confirm overlay too).
    var cardFocused by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (cardFocused) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "roomCardFocusScale",
    )
    // Join sits well below the card's own top edge now (poster, host row,
    // relay row all come first) — the LazyColumn's *default* focus-scroll
    // only guarantees the specific focused button's own small rect is
    // visible, not the card it belongs to, so it was leaving the poster
    // scrolled out above the viewport whenever focus landed on a button.
    // Explicitly requesting the whole card's bounds on focus is what
    // BringIntoViewRequester exists for.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // Mirrors FocusableSurface's own canonical modifier order (drawGlow, then
    // clip, then background, then border) rather than inventing a new one —
    // glow has to be drawn *before* the clip so its outward reach isn't cut
    // off by the card's own rounded-rect bounds, exactly the ordering bug
    // that would otherwise make the glow invisible.
    Column(
        modifier = modifier
            .width(300.dp)
            .zIndex(if (cardFocused) 1f else 0f)
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .let { if (cardFocused) it.drawGlow(RoomCardShape, NeonPurpleGlow, 14.dp, 0.22f) else it }
            .clip(RoomCardShape)
            .background(AppSurface, RoomCardShape)
            .let { if (cardFocused) it.border(BorderStroke(2.dp, NeonPurpleGradient), RoomCardShape) else it }
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusGroup()
            .onFocusChanged { state ->
                cardFocused = state.hasFocus
                if (state.hasFocus) scope.launch { runCatching { bringIntoViewRequester.bringIntoView() } }
            },
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(104.dp)) {
            ShumArtwork(
                model = PlexImageUrl.of(server, room.thumb),
                contentDescription = room.title,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, AppScrim.copy(alpha = 0.85f)))),
            )
            Text(
                text = room.title,
                color = AppWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            )
            if (isHosted || isMine) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(NeonPurple.copy(alpha = 0.9f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(text = if (isHosted) "You're hosting" else "You're in", color = AppWhite, style = roomBadgeStyle)
                }
            }
        }
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 34dp initial-letter avatar next to a two-line host/occupancy
            // stack — same accent@.35 fallback idiom as every other avatar
            // in this app (LobbyPersonCard, PersonFilmographyScreen), just
            // smaller. Missing from the very first pass of this card; the
            // design mockup pairs it with the host line, not text alone.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(NeonPurple.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = room.hostName.take(1).uppercase(), color = AppWhite, style = roomAvatarInitialStyle)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isHosted) "You hosting" else "${room.hostName} hosting",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${room.occupants} of ${room.maxSeats} watching",
                        color = AppOnSurfaceVariant,
                    )
                }
            }
            // Design spec 09d: one line, dot + "Available on <nickname>" —
            // the only new content a merged multi-relay view adds to an
            // otherwise unchanged 09b card.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.width(6.dp).height(6.dp)
                        .background(if (isHosted && failed) AppOnSurfaceVariant else NeonPurple, RoundedCornerShape(50)),
                )
                Text(text = "Available on ${merged.relay.nickname}", color = AppOnSurfaceVariant)
            }
            Row(
                // 16dp, not 8 — anything less than the shared 14dp glow
                // radius (FocusableSurface's ShumGlow default) means a
                // focused button's glow visually washes over its neighbor's
                // own idle fill, reading as a smear of stacked colors rather
                // than two distinct controls. Same spacing and the same
                // `compact` buttons as ContinueWatchingPoster's Remove/
                // Cancel pair — two cards with a same-shaped action-pair
                // problem should look and behave identically.
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Design spec 11's action row is the last thing in the card,
                // with nothing focusable above it (the text lines above
                // aren't focus targets) — without an explicit `up`, Compose's
                // default spatial search falls back to the nearest focusable
                // it can find at all, which on Home's very first row is the
                // nav drawer's rail. Landing there and immediately bouncing
                // back out reads as the drawer flickering open/closed, same
                // bug class as this app's other documented focus-escape
                // fixes (see AppNavigationDrawer's own history). Cancel
                // consumes the key press instead of searching further; left/
                // right/down are untouched since those need to keep working
                // (between the two pills, and down into Continue Watching).
                val blockUpEscape = Modifier.focusProperties { up = FocusRequester.Cancel }
                if (isHosted && failed) {
                    Text(text = "Can't reach relay", color = AppOnSurfaceVariant)
                } else {
                    ShumButton(
                        onClick = onClick,
                        enabled = !full,
                        compact = true,
                        modifier = blockUpEscape.let {
                            if (joinFocusRequester != null) it.focusRequester(joinFocusRequester) else it
                        },
                    ) { Text(joinLabel) }
                    if (isHosted) {
                        ShumOutlinedButton(onClick = { endNow() }, compact = true, modifier = blockUpEscape) { Text("End session") }
                    }
                }
            }
        }
    }
}

private val RoomCardShape = RoundedCornerShape(8.dp)
private val roomBadgeStyle = TextStyle(fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
private val roomAvatarInitialStyle = TextStyle(fontSize = 12.sp)

@Composable
private fun OverflowTile(count: Int, onClick: () -> Unit) {
    ShumCard(onClick = onClick, modifier = Modifier.width(132.dp).height(212.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(text = "+$count", style = ShumTypography.headlineMedium)
            Text(text = "more rooms", color = AppOnSurfaceVariant)
        }
    }
}

// recentlyAdded surfaces TV content at season granularity ("Season 14",
// confirmed against a real server) — parentTitle is the actual show name
// Plex attaches to season items, which reads much better in a poster row.
private fun recentlyAddedLabel(item: PlexLibraryItem): String {
    val parentTitle = item.parentTitle
    return if (item.type == "season" && parentTitle != null) parentTitle else item.title
}

private fun continueWatchingLabel(item: PlexOnDeckItem): String =
    if (item.type == TYPE_EPISODE && item.grandparentTitle != null && item.parentIndex != null && item.index != null) {
        "${item.grandparentTitle} · S${item.parentIndex}E${item.index}"
    } else {
        item.title
    }

private fun progressFraction(item: PlexOnDeckItem): Float {
    val duration = item.duration?.takeIf { it > 0 } ?: return 0f
    return ((item.viewOffset ?: 0L).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

// Plain "tap to view details" posters — mirrors LibraryScreen's LibraryPoster
// almost exactly. Unlike ContinueWatchingPoster, these have no resume/
// progress-bar/long-press-remove behavior, so they use tv-material3's own
// Card directly instead of the hand-rolled combinedClickable Box.
@Composable
private fun RecentlyAddedPoster(
    server: PlexServer,
    item: PlexLibraryItem,
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
                    model = PlexImageUrl.of(server, item.thumb),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    staggerDelayMs = staggerDelayMs,
                )
            }
        },
        title = {
            Text(
                text = recentlyAddedLabel(item),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 16.dp),
            )
        },
    )
}

@Composable
private fun SuggestionPoster(
    server: PlexServer,
    item: PlexOnDeckItem,
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
                    model = PlexImageUrl.of(server, item.thumb),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    staggerDelayMs = staggerDelayMs,
                )
            }
        },
        title = {
            Text(
                text = continueWatchingLabel(item),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 16.dp),
            )
        },
    )
}

// StandardCardContainer's imageCard slot normally hosts a tv-material3 Card,
// but Card only exposes a single onClick — no onLongClick anywhere in this
// library version (confirmed by decompiling tv-material-1.1.0.aar), so
// long-press-to-remove is hand-rolled here with combinedClickable directly,
// same move already made for AppNavigationDrawer's sidebar when
// NavigationDrawer didn't expose a tunable animation either.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingPoster(
    server: PlexServer,
    item: PlexOnDeckItem,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    staggerDelayMs: Int = 0,
) {
    var confirmingRemove by remember(item.ratingKey) { mutableStateOf(false) }
    // The physical remote button that triggers the long-press is usually
    // still held down right when the confirm overlay appears — its eventual
    // release (KeyUp on DirectionCenter/Enter) lands on whichever button the
    // overlay just focused, and without this guard that stray release reads
    // as a real click and confirms/cancels before the user ever chose
    // anything. A time-based debounce doesn't work here since hold duration
    // varies (a long press held past the debounce window still leaks
    // through) — instead this swallows the *exact* trailing release tied to
    // the gesture that opened the overlay, however long it was held, and
    // only starts accepting real clicks once that specific KeyUp has been
    // consumed. Reset to false every time confirmingRemove is (re)armed.
    var confirmArmed by remember(item.ratingKey) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // Swapping to the confirm overlay destroys the previously-focused
    // clickable box for one frame before RemoveConfirmOverlay's own
    // LaunchedEffect can move focus onto its Remove button — without this
    // guard, that transient no-focus frame looks identical to "focus left
    // the card" and immediately cancels the confirm before it's even shown.
    // Same idiom as LibraryScreen's FilterDropdown: only treat a
    // hasFocus=false callback as "left" once focus has genuinely landed
    // inside the confirm overlay at least once.
    var hasBeenFocusedSinceConfirm by remember(item.ratingKey) { mutableStateOf(false) }

    BackHandler(enabled = confirmingRemove) { confirmingRemove = false }

    // Design spec "Focus magnification": 1.04 here (vs. ShumCard's default
    // 1.06) targets the same ~10dp of absolute growth on this wider 240dp
    // card. Scale wraps the whole box — border, glow and content together —
    // same as ShumCard's own placement of the transform.
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "continueWatchingFocusScale",
    )

    ShumCardContainer(
        modifier = Modifier.width(240.dp),
        imageCard = { interactionSource ->
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .zIndex(if (focused) 1f else 0f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(8.dp))
                    // Same two-tone gradient border as ShumCard elsewhere
                    // (RecentlyAdded/Suggestions posters, ShowSeasons/
                    // ShowEpisodes/LibraryScreen) — this one's hand-rolled
                    // since the confirm-overlay focus trap below needs direct
                    // access to this box's own focus state (to swap its
                    // content and drive the border from the same `focused`
                    // flag), which doesn't fit ShumCard's plain onClick/
                    // onLongClick/content shape. The border should still
                    // match every other poster's, not fall back to a flat
                    // single-tone border.
                    .then(
                        if (focused) {
                            Modifier.border(
                                BorderStroke(2.dp, Brush.radialGradient(listOf(NeonPurpleGlow, NeonPurple))),
                                RoundedCornerShape(8.dp),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .focusGroup()
                    .onFocusChanged { state ->
                        focused = state.isFocused
                        if (confirmingRemove) {
                            if (state.hasFocus) {
                                hasBeenFocusedSinceConfirm = true
                            } else if (hasBeenFocusedSinceConfirm) {
                                confirmingRemove = false
                            }
                        }
                    }
                    // Tunnels (fires before descendants), so this reliably
                    // catches the trailing release before Remove/Cancel's
                    // own clickable ever sees it as a click.
                    .onPreviewKeyEvent { keyEvent ->
                        if (confirmingRemove && !confirmArmed) {
                            val isSelect = keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter
                            if (isSelect) {
                                if (keyEvent.type == KeyEventType.KeyUp) confirmArmed = true
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    },
            ) {
                if (confirmingRemove) {
                    RemoveConfirmOverlay(
                        onConfirm = { confirmingRemove = false; onRemove() },
                        onCancel = { confirmingRemove = false },
                    )
                } else {
                    ShumArtwork(
                        model = PlexImageUrl.of(server, item.thumb),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        staggerDelayMs = staggerDelayMs,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(AppScrim.copy(alpha = 0.4f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction(item))
                                .background(NeonPurple),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onResume,
                                onLongClick = {
                                    confirmArmed = false
                                    hasBeenFocusedSinceConfirm = false
                                    confirmingRemove = true
                                },
                            ),
                    )
                }
            }
        },
        title = {
            Text(
                text = continueWatchingLabel(item),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).width(240.dp),
            )
        },
    )
}

// Real "are you sure" confirm shown directly on long-press (not a menu —
// long-press already IS the deliberate trigger). The stray-release problem
// (see ContinueWatchingPoster's confirmArmed/onPreviewKeyEvent) is handled
// by the caller before this ever mounts, so Remove/Cancel's onClick here can
// just be the real actions with no debounce of their own.
//
// D-pad navigation could otherwise escape this overlay entirely (landing on
// the nav drawer) before the user picked Remove or Cancel. focusProperties'
// onExit override traps directional focus inside this group — Left/Right
// between the two buttons still works, but nothing can navigate out except
// an explicit click on one of them or the hardware Back key (handled
// separately by ContinueWatchingPoster's BackHandler, unaffected since Back
// isn't part of directional focus search).
@Composable
private fun RemoveConfirmOverlay(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val removeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { removeFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppScrim.copy(alpha = 0.85f))
            .focusGroup()
            .focusProperties { onExit = { cancelFocusChange() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Remove from Continue Watching?", textAlign = TextAlign.Center, style = ShumTypography.bodyLarge)
            // 16dp + compact, matching RoomCard's Join/End session pair —
            // same "two actions in one card" shape, so it should look and
            // behave the same. Anything tighter than the shared 14dp glow
            // radius lets a focused button's glow wash over its neighbor.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShumButton(
                    onClick = onConfirm,
                    compact = true,
                    modifier = Modifier.focusRequester(removeFocus),
                ) { Text("Remove") }
                ShumButton(onClick = onCancel, compact = true) { Text("Cancel") }
            }
        }
    }
}
