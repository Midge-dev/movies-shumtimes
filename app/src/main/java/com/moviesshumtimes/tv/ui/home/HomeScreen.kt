package com.moviesshumtimes.tv.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexLibraryItem
import com.moviesshumtimes.tv.data.plex.PlexOnDeckItem
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonBorder
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonGlow
import com.moviesshumtimes.tv.ui.theme.neonPurpleCardBorder
import com.moviesshumtimes.tv.ui.theme.neonPurpleCardGlow
import com.moviesshumtimes.tv.ui.theme.whiteButtonColors

private const val TYPE_EPISODE = "episode"

@Composable
fun HomeScreen(
    server: PlexServer,
    onDeck: List<PlexOnDeckItem>,
    recentlyAdded: List<PlexLibraryItem>,
    suggestions: List<PlexOnDeckItem>,
    onResume: (PlexOnDeckItem) -> Unit,
    onRemove: (PlexOnDeckItem) -> Unit,
    onSelectRecentlyAdded: (PlexLibraryItem) -> Unit,
    onSelectSuggestion: (PlexOnDeckItem) -> Unit,
) {
    val firstItemFocus = remember { FocusRequester() }
    // AppNavigationDrawer's sidebar is the first focusable thing in the
    // composition — same pattern as every other screen it wraps (see
    // LibraryScreen's matching comment). Only one row actually gets the
    // requester: whichever is first non-empty, in display order — Continue
    // Watching, then Recently Added, then Suggestions — so focus always
    // lands on the first real poster on screen, not wherever Continue
    // Watching happens to be even when it's empty.
    // Nesting a LazyRow inside a LazyColumn item (needed for the three-row
    // layout below) means the target poster isn't necessarily composed yet
    // on the very first frame this effect runs — confirmed on-device: a
    // single un-retried requestFocus() here silently failed every time,
    // leaving focus stuck in the sidebar with D-pad Down never reaching the
    // content at all. Retrying across a few frames covers that gap.
    val continueWatchingGetsFocus = onDeck.isNotEmpty()
    val recentlyAddedGetsFocus = !continueWatchingGetsFocus && recentlyAdded.isNotEmpty()
    val suggestionsGetsFocus = !continueWatchingGetsFocus && !recentlyAddedGetsFocus && suggestions.isNotEmpty()
    LaunchedEffect(onDeck, recentlyAdded, suggestions) {
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
        modifier = Modifier.fillMaxSize(),
        // Without this the Suggestions row (always last) sat flush against
        // the screen edge — no row after it to supply trailing space, unlike
        // Continue Watching/Recently Added which get breathing room from the
        // row below them.
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleLarge,
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
        style = MaterialTheme.typography.titleLarge,
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
private fun RecentlyAddedPoster(server: PlexServer, item: PlexLibraryItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    StandardCardContainer(
        modifier = Modifier.width(160.dp),
        imageCard = { interactionSource ->
            Card(
                onClick = onClick,
                interactionSource = interactionSource,
                border = neonPurpleCardBorder(),
                glow = neonPurpleCardGlow(),
                modifier = modifier.fillMaxWidth().aspectRatio(2f / 3f),
            ) {
                AsyncImage(
                    model = PlexImageUrl.of(server, item.thumb),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
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
private fun SuggestionPoster(server: PlexServer, item: PlexOnDeckItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    StandardCardContainer(
        modifier = Modifier.width(160.dp),
        imageCard = { interactionSource ->
            Card(
                onClick = onClick,
                interactionSource = interactionSource,
                border = neonPurpleCardBorder(),
                glow = neonPurpleCardGlow(),
                modifier = modifier.fillMaxWidth().aspectRatio(2f / 3f),
            ) {
                AsyncImage(
                    model = PlexImageUrl.of(server, item.thumb),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
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

    StandardCardContainer(
        modifier = Modifier.width(240.dp),
        imageCard = { interactionSource ->
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    // Same two-tone gradient as neonPurpleCardBorder() below
                    // (RecentlyAdded/Suggestions posters, ShowSeasons/
                    // ShowEpisodes/LibraryScreen) — this one's hand-rolled
                    // since Card has no onLongClick to hang the remove
                    // gesture off of, but the focus border should still
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
                    AsyncImage(
                        model = PlexImageUrl.of(server, item.thumb),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
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
            Text("Remove from Continue Watching?", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConfirm,
                    colors = whiteButtonColors(),
                    border = neonPurpleButtonBorder(),
                    glow = neonPurpleButtonGlow(),
                    modifier = Modifier.focusRequester(removeFocus),
                ) { Text("Remove") }
                Button(onClick = onCancel, colors = whiteButtonColors()) { Text("Cancel") }
            }
        }
    }
}
