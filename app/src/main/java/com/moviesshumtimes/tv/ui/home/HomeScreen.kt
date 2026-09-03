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
import com.moviesshumtimes.tv.ui.theme.NeonPurpleProgressGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TYPE_EPISODE = "episode"

private const val ROW_STAGGER_PERIOD = 6

@Composable
fun HomeScreen(
    server: PlexServer,
    onDeck: List<PlexOnDeckItem>,
    recentlyAdded: List<PlexLibraryItem>,
    recentActivity: List<PlexOnDeckItem>,
    suggestions: List<PlexOnDeckItem>,
    liveRooms: List<MergedRoom>,
    myRoomId: String?,
    hostedRoomIds: Set<String>,
    onEndSession: suspend (MergedRoom) -> Boolean,
    onSelectRoom: (MergedRoom) -> Unit,
    onResume: (PlexOnDeckItem) -> Unit,
    onRemove: (PlexOnDeckItem) -> Unit,
    onSelectRecentlyAdded: (PlexLibraryItem) -> Unit,
    onSelectRecentActivity: (PlexOnDeckItem) -> Unit,
    onSelectSuggestion: (PlexOnDeckItem) -> Unit,
) {
    val firstItemFocus = remember { FocusRequester() }
    val watchTogetherGetsFocus = liveRooms.isNotEmpty()
    val continueWatchingGetsFocus = !watchTogetherGetsFocus && onDeck.isNotEmpty()
    val recentActivityGetsFocus =
        !watchTogetherGetsFocus && !continueWatchingGetsFocus && recentActivity.isNotEmpty()
    val recentlyAddedGetsFocus =
        !watchTogetherGetsFocus && !continueWatchingGetsFocus && !recentActivityGetsFocus && recentlyAdded.isNotEmpty()
    val suggestionsGetsFocus = !watchTogetherGetsFocus && !continueWatchingGetsFocus &&
        !recentActivityGetsFocus && !recentlyAddedGetsFocus && suggestions.isNotEmpty()
    val homeListState = rememberLazyListState()
    LaunchedEffect(watchTogetherGetsFocus, onDeck, recentActivity, recentlyAdded, suggestions) {
        if (watchTogetherGetsFocus) {
            runCatching { homeListState.animateScrollToItem(0) }
        }
        repeat(5) {
            if (runCatching { firstItemFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos {}
        }
    }

    LazyColumn(
        state = homeListState,
        modifier = Modifier.fillMaxSize(),
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
            HomeRow(title = "Recently Finished Watching", items = recentActivity, key = { it.ratingKey }) { item, index ->
                SuggestionPoster(
                    server = server,
                    item = item,
                    onClick = { onSelectRecentActivity(item) },
                    modifier = if (index == 0 && recentActivityGetsFocus) Modifier.focusRequester(firstItemFocus) else Modifier,
                    staggerDelayMs = (index % ROW_STAGGER_PERIOD) * 120,
                )
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

private const val VISIBLE_ROOM_CARDS = 3

data class MergedRoom(val relay: RelayEntry, val room: RelayRoomSummary)

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

    var cardFocused by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (cardFocused) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "roomCardFocusScale",
    )
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.width(6.dp).height(6.dp)
                        .background(if (isHosted && failed) AppOnSurfaceVariant else NeonPurple, RoundedCornerShape(50)),
                )
                Text(text = "Available on ${merged.relay.nickname}", color = AppOnSurfaceVariant)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
    var confirmArmed by remember(item.ratingKey) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var hasBeenFocusedSinceConfirm by remember(item.ratingKey) { mutableStateOf(false) }

    BackHandler(enabled = confirmingRemove) { confirmingRemove = false }

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
                                .background(NeonPurpleProgressGradient),
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
