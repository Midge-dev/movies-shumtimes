package com.moviesshumtimes.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexLibraryItem
import com.moviesshumtimes.tv.data.plex.PlexSection
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.ui.common.ClickToTypeTextField
import com.moviesshumtimes.tv.ui.common.NeonScrollbar
import com.moviesshumtimes.tv.ui.common.ShumArtwork
import com.moviesshumtimes.tv.ui.kit.FocusableSurface
import com.moviesshumtimes.tv.ui.kit.ShumBorder
import com.moviesshumtimes.tv.ui.kit.ShumCard
import com.moviesshumtimes.tv.ui.kit.ShumCardContainer
import com.moviesshumtimes.tv.ui.kit.ShumColors
import com.moviesshumtimes.tv.ui.kit.ShumGlow
import com.moviesshumtimes.tv.ui.kit.ShumOutlinedButton
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppDimBorder
import com.moviesshumtimes.tv.ui.theme.AppOnSurface
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.AppSurface
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient
import com.moviesshumtimes.tv.ui.theme.NeonPurplePressed

private const val GRID_COLUMNS = 5

@Composable
fun LibraryScreen(
    server: PlexServer,
    selectedSection: PlexSection,
    items: List<PlexLibraryItem>,
    onSelectItem: (PlexLibraryItem) -> Unit,
) {
    var query by remember(selectedSection.key) { mutableStateOf("") }
    var sortMode by remember(selectedSection.key) { mutableStateOf(SortMode.TITLE) }
    var genreFilter by remember(selectedSection.key) { mutableStateOf<String?>(null) }
    var decadeFilter by remember(selectedSection.key) { mutableStateOf<Int?>(null) }
    var dateAddedFilter by remember(selectedSection.key) { mutableStateOf<DateAddedBucket?>(null) }
    var sortMenuExpanded by remember(selectedSection.key) { mutableStateOf(false) }
    var filtersExpanded by remember(selectedSection.key) { mutableStateOf(false) }

    val availableGenres = remember(items) { items.flatMap { item -> item.genres.map { it.tag } }.distinct().sorted() }
    val availableDecades = remember(items) { items.mapNotNull { decadeOf(it) }.distinct().sortedDescending() }
    val displayedItems = remember(items, query, sortMode, genreFilter, decadeFilter, dateAddedFilter) {
        applyLibraryFilters(items, query, sortMode, genreFilter, decadeFilter, dateAddedFilter)
    }

    val searchFocus = remember { FocusRequester() }
    val sortButtonFocus = remember { FocusRequester() }
    val filterButtonFocus = remember { FocusRequester() }
    val clearAllFocus = remember { FocusRequester() }
    val sortRowFocuses = remember { SortMode.entries.associateWith { FocusRequester() } }
    val genreFocuses = remember(availableGenres) { availableGenres.associateWith { FocusRequester() } }
    val decadeFocuses = remember(availableDecades) { availableDecades.associateWith { FocusRequester() } }
    val dateAddedFocuses = remember { DateAddedBucket.entries.associateWith { FocusRequester() } }

    // Only one of these can be open at a time — the two BackHandlers stay
    // independently gated so Back always closes whichever menu is showing
    // and hands focus back to the trigger that opened it.
    BackHandler(enabled = sortMenuExpanded) {
        sortMenuExpanded = false
        runCatching { sortButtonFocus.requestFocus() }
    }
    BackHandler(enabled = filtersExpanded) {
        filtersExpanded = false
        runCatching { filterButtonFocus.requestFocus() }
    }

    // AppNavigationDrawer wraps this screen now, and its sidebar is the
    // first focusable thing in the composition — without an explicit
    // request here, D-pad focus defaults to (and gets stuck in) the nav
    // rail instead of the library content on every entry. Keyed on the
    // section so switching sections from the drawer re-focuses content
    // too, not just the very first time this screen appears.
    LaunchedEffect(selectedSection.key) {
        runCatching { searchFocus.requestFocus() }
    }

    // Design spec section 06b: focus opens on the currently applied option,
    // not the first row — reopening Sort with "Date Added" active puts focus
    // on Date Added.
    LaunchedEffect(sortMenuExpanded) {
        if (!sortMenuExpanded) return@LaunchedEffect
        runCatching { sortRowFocuses.getValue(sortMode).requestFocus() }
    }
    LaunchedEffect(filtersExpanded, availableGenres, availableDecades) {
        if (!filtersExpanded) return@LaunchedEffect
        val target = genreFilter?.let(genreFocuses::get)
            ?: decadeFilter?.let(decadeFocuses::get)
            ?: dateAddedFilter?.let(dateAddedFocuses::get)
            ?: genreFocuses.values.firstOrNull()
            ?: decadeFocuses.values.firstOrNull()
            ?: dateAddedFocuses.values.first()
        runCatching { target.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClickToTypeTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(color = AppOnSurface),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Search ${selectedSection.title}…", color = AppWhite)
                        }
                        inner()
                    },
                    modifier = Modifier
                        .background(AppSurfaceVariant)
                        .padding(12.dp)
                        .width(320.dp)
                        .focusRequester(searchFocus)
                        .focusProperties { down = sortButtonFocus },
                )
                // States its current value in the label so it never needs a
                // badge — see FilterTrigger below for the one that does.
                ShumOutlinedButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier
                        .focusRequester(sortButtonFocus)
                        .focusProperties { up = searchFocus; down = filterButtonFocus },
                ) {
                    Text("Sort: ${sortMode.label}")
                    Text(" ▾", modifier = Modifier.padding(start = 8.dp))
                }
                FilterTrigger(
                    appliedCount = listOfNotNull(genreFilter, decadeFilter, dateAddedFilter).size,
                    onClick = { filtersExpanded = true },
                    modifier = Modifier
                        .focusRequester(filterButtonFocus)
                        .focusProperties { up = searchFocus },
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    contentPadding = PaddingValues(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(displayedItems, key = { _, item -> item.ratingKey }) { index, item ->
                        LibraryPoster(
                            server = server,
                            item = item,
                            onClick = { onSelectItem(item) },
                            // Stagger by column, not absolute list index, so
                            // late items in a long library don't inherit an
                            // absurd multi-second delay — design spec 05c's
                            // "index × 120ms" guard against a grid pulsing
                            // in unison only needs to vary across one row.
                            staggerDelayMs = (index % GRID_COLUMNS) * 120,
                        )
                    }
                }
                // The grid stays mounted (empty) behind this rather than
                // being replaced by an illustration/card — design spec
                // section 06b.
                if (displayedItems.isEmpty()) {
                    Text("Nothing matches these filters.", modifier = Modifier.padding(32.dp))
                }
            }
        }

        // Dimmed, not hidden — the result count in the grid behind stays
        // visible while a menu is open.
        if (sortMenuExpanded || filtersExpanded) {
            Box(modifier = Modifier.fillMaxSize().background(AppScrim.copy(alpha = 0.4f)))
        }

        if (sortMenuExpanded) {
            SortMenu(
                selected = sortMode,
                rowFocuses = sortRowFocuses,
                onSelect = {
                    sortMode = it
                    sortMenuExpanded = false
                    runCatching { sortButtonFocus.requestFocus() }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 368.dp, top = 96.dp),
            )
        }

        if (filtersExpanded) {
            FilterMenu(
                items = items,
                query = query,
                availableGenres = availableGenres,
                availableDecades = availableDecades,
                genreFilter = genreFilter,
                decadeFilter = decadeFilter,
                dateAddedFilter = dateAddedFilter,
                genreFocuses = genreFocuses,
                decadeFocuses = decadeFocuses,
                dateAddedFocuses = dateAddedFocuses,
                clearAllFocus = clearAllFocus,
                onGenreSelect = { genreFilter = if (genreFilter == it) null else it },
                onDecadeSelect = { decadeFilter = if (decadeFilter == it) null else it },
                onDateAddedSelect = { dateAddedFilter = if (dateAddedFilter == it) null else it },
                onClearAll = {
                    genreFilter = null
                    decadeFilter = null
                    dateAddedFilter = null
                    filtersExpanded = false
                    runCatching { filterButtonFocus.requestFocus() }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 32.dp),
            )
        }
    }
}

private val MenuShape = RoundedCornerShape(8.dp)

// Design spec section 06b: idle transparent, applied-but-unfocused keeps a
// flat @35% accent fill (matching the nav rail's own selected treatment),
// focused always wins with a solid accent fill plus the standard gradient
// ring + glow — the two stack rather than replace each other.
private val menuRowColors = ShumColors(
    container = Color.Transparent,
    content = AppWhite,
    focusedContainer = NeonPurple,
    selectedContainer = NeonPurple.copy(alpha = 0.35f),
)
private val menuRowBorder = ShumBorder(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val menuRowGlow = ShumGlow(focusedColor = NeonPurpleGlow)

@Composable
private fun MenuSectionHeader(label: String) {
    Text(label, style = ShumTypography.titleMedium, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
}

// Replaces the old full-width FilterChip rows — a chip's pill shape reads
// wrong at full width, and its own selected affordance fought the accent
// fill this needs instead. `dimmed` marks an option with 0 matches under the
// other currently-active filters: it stays focusable, just less prominent.
@Composable
private fun MenuOptionRow(
    label: String,
    applied: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val colors = if (dimmed) {
        menuRowColors.copy(
            content = menuRowColors.content.copy(alpha = 0.5f),
            focusedContent = menuRowColors.focusedContent.copy(alpha = 0.5f),
            selectedContent = menuRowColors.selectedContent.copy(alpha = 0.5f),
        )
    } else {
        menuRowColors
    }
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(46.dp),
        selected = applied,
        shape = MenuShape,
        colors = colors,
        border = menuRowBorder,
        glow = menuRowGlow,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Reserved-width leading column so labels line up whether or
            // not a row happens to be checked.
            Box(modifier = Modifier.width(16.dp)) {
                if (applied) Text("✓")
            }
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// D-pad contract (design spec 06b): Up/Down move within the menu without
// wrapping at either end; Left/Right do nothing — the menu owns the
// horizontal axis so a stray press can't jump to the grid behind it; Select
// applies and, for Sort, closes the menu.
@Composable
private fun SortMenu(
    selected: SortMode,
    rowFocuses: Map<SortMode, FocusRequester>,
    onSelect: (SortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = SortMode.entries
    Column(
        modifier = modifier
            .width(300.dp)
            .background(AppSurface, MenuShape)
            .focusGroup()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MenuSectionHeader("Sort by")
        modes.forEachIndexed { index, mode ->
            MenuOptionRow(
                label = mode.label,
                applied = mode == selected,
                onClick = { onSelect(mode) },
                modifier = Modifier
                    .focusRequester(rowFocuses.getValue(mode))
                    .focusProperties {
                        up = if (index > 0) rowFocuses.getValue(modes[index - 1]) else FocusRequester.Cancel
                        down = if (index < modes.lastIndex) rowFocuses.getValue(modes[index + 1]) else FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
            )
        }
    }
}

// Wider than Sort and scrolls: one column of grouped rows (Genre, Release
// Date, Date Added), not three side-by-side, so the nav rail and the grid's
// horizontal rows behind stay reachable. Filter stays open on select so
// several groups can be set in one visit — only Back or losing focus closes
// it. "Clear all" only appears once something is applied; it sits at the top
// of the focus chain rather than the spec mockup's top-right link, which a
// D-pad has no way to reach directly.
@Composable
private fun FilterMenu(
    items: List<PlexLibraryItem>,
    query: String,
    availableGenres: List<String>,
    availableDecades: List<Int>,
    genreFilter: String?,
    decadeFilter: Int?,
    dateAddedFilter: DateAddedBucket?,
    genreFocuses: Map<String, FocusRequester>,
    decadeFocuses: Map<Int, FocusRequester>,
    dateAddedFocuses: Map<DateAddedBucket, FocusRequester>,
    clearAllFocus: FocusRequester,
    onGenreSelect: (String) -> Unit,
    onDecadeSelect: (Int) -> Unit,
    onDateAddedSelect: (DateAddedBucket) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val anyApplied = genreFilter != null || decadeFilter != null || dateAddedFilter != null

    val orderedFocuses = remember(availableGenres, availableDecades) {
        availableGenres.map { genreFocuses.getValue(it) } +
            availableDecades.map { decadeFocuses.getValue(it) } +
            DateAddedBucket.entries.map { dateAddedFocuses.getValue(it) }
    }

    fun neighbors(index: Int) = Modifier.focusProperties {
        up = when {
            index > 0 -> orderedFocuses[index - 1]
            anyApplied -> clearAllFocus
            else -> FocusRequester.Cancel
        }
        down = if (index < orderedFocuses.lastIndex) orderedFocuses[index + 1] else FocusRequester.Cancel
        left = FocusRequester.Cancel
        right = FocusRequester.Cancel
    }

    Row(
        modifier = modifier
            .width(420.dp)
            .heightIn(max = 520.dp)
            .background(AppSurface, MenuShape)
            .focusGroup()
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (anyApplied) {
                MenuOptionRow(
                    label = "Clear all",
                    applied = false,
                    onClick = onClearAll,
                    modifier = Modifier
                        .focusRequester(clearAllFocus)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = orderedFocuses.firstOrNull() ?: FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                )
            }
            if (availableGenres.isNotEmpty()) {
                MenuSectionHeader("Genre")
                availableGenres.forEachIndexed { index, genre ->
                    val dimmed = applyLibraryFilters(items, query, SortMode.TITLE, genre, decadeFilter, dateAddedFilter).isEmpty()
                    MenuOptionRow(
                        label = genre,
                        applied = genre == genreFilter,
                        dimmed = dimmed,
                        onClick = { onGenreSelect(genre) },
                        modifier = Modifier.focusRequester(genreFocuses.getValue(genre)).then(neighbors(index)),
                    )
                }
            }
            if (availableDecades.isNotEmpty()) {
                MenuSectionHeader("Release Date")
                availableDecades.forEachIndexed { index, decade ->
                    val dimmed = applyLibraryFilters(items, query, SortMode.TITLE, genreFilter, decade, dateAddedFilter).isEmpty()
                    MenuOptionRow(
                        label = "${decade}s",
                        applied = decade == decadeFilter,
                        dimmed = dimmed,
                        onClick = { onDecadeSelect(decade) },
                        modifier = Modifier
                            .focusRequester(decadeFocuses.getValue(decade))
                            .then(neighbors(availableGenres.size + index)),
                    )
                }
            }
            MenuSectionHeader("Date Added")
            DateAddedBucket.entries.forEachIndexed { index, bucket ->
                val dimmed = applyLibraryFilters(items, query, SortMode.TITLE, genreFilter, decadeFilter, bucket).isEmpty()
                MenuOptionRow(
                    label = bucket.label,
                    applied = bucket == dateAddedFilter,
                    dimmed = dimmed,
                    onClick = { onDateAddedSelect(bucket) },
                    modifier = Modifier
                        .focusRequester(dateAddedFocuses.getValue(bucket))
                        .then(neighbors(availableGenres.size + availableDecades.size + index)),
                )
            }
        }
        NeonScrollbar(scrollState = scrollState, modifier = Modifier.padding(start = 8.dp))
    }
}

// Filter's trigger states a count instead of its value (there's no single
// value to state across three independent groups): idle+unapplied is
// outlined like Sort, ≥1 applied switches to a solid accent fill that
// persists regardless of focus, with a white badge replacing the caret.
private val FilterTriggerShape = CircleShape

@Composable
private fun FilterTrigger(appliedCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val applied = appliedCount > 0
    val colors = ShumColors(
        container = if (applied) NeonPurple else Color.Transparent,
        content = AppWhite,
        focusedContainer = NeonPurple,
        pressedContainer = NeonPurplePressed,
    )
    val border = ShumBorder(
        idle = if (applied) null else BorderStroke(2.dp, AppDimBorder),
        focused = BorderStroke(2.dp, NeonPurpleGradient),
    )
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = 58.dp, minHeight = 40.dp),
        shape = FilterTriggerShape,
        colors = colors,
        border = border,
        glow = ShumGlow(focusedColor = NeonPurpleGlow),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filter")
            if (applied) {
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 22.dp, minHeight = 22.dp)
                        .background(AppWhite, CircleShape)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$appliedCount", color = NeonPurple)
                }
            } else {
                Text(" ▾")
            }
        }
    }
}

@Composable
private fun LibraryPoster(
    server: PlexServer,
    item: PlexLibraryItem,
    onClick: () -> Unit,
    staggerDelayMs: Int = 0,
) {
    ShumCardContainer(
        modifier = Modifier.width(160.dp),
        imageCard = { interactionSource ->
            ShumCard(
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            ) {
                ShumArtwork(
                    model = PlexImageUrl.of(server, item.thumb),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    // Design spec 05c: grain drops to 40% in a 5-across grid
                    // so it never strobes across a whole page of posters.
                    noiseOpacity = 0.4f,
                    staggerDelayMs = staggerDelayMs,
                )
            }
        },
        title = {
            // StandardCardContainer's default gap between the bordered image
            // and the title below it is too tight for our thicker focus
            // border — the title visually collides with it when focused.
            // 8dp wasn't enough once the glow (not just the border stroke)
            // is accounted for, since CardGlow's elevation shadow extends
            // further than a flat border (reported as still too tight).
            Text(text = item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp))
        },
    )
}
