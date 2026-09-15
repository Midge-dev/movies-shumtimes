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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.data.plex.PlexCollection
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexLibraryItem
import com.moviesshumtimes.tv.data.plex.PlexSection
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.ui.common.ClickToTypeTextField
import com.moviesshumtimes.tv.ui.common.LoadingScreen
import com.moviesshumtimes.tv.ui.common.NeonScrollbar
import com.moviesshumtimes.tv.ui.common.ShumArtwork
import com.moviesshumtimes.tv.ui.common.onDpadLongPress
import com.moviesshumtimes.tv.ui.kit.FocusableSurface
import com.moviesshumtimes.tv.ui.kit.ShumBorder
import com.moviesshumtimes.tv.ui.kit.ShumCard
import com.moviesshumtimes.tv.ui.kit.ShumCardContainer
import com.moviesshumtimes.tv.ui.kit.ShumColors
import com.moviesshumtimes.tv.ui.kit.ShumFilterChip
import com.moviesshumtimes.tv.ui.kit.ShumGlow
import com.moviesshumtimes.tv.ui.kit.ShumOutlinedButton
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppDimBorder
import com.moviesshumtimes.tv.ui.theme.AppOnSurface
import com.moviesshumtimes.tv.ui.theme.AppOnSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.AppSurface
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient
import com.moviesshumtimes.tv.ui.theme.NeonPurplePressed

private const val GRID_COLUMNS = 5

private enum class BrowseTab(val label: String) {
    ALL("All"),
    COLLECTIONS("Collections"),
    GENRE("Genre"),
}

@Composable
fun LibraryScreen(
    server: PlexServer,
    selectedSection: PlexSection,
    items: List<PlexLibraryItem>,
    onSelectItem: (PlexLibraryItem) -> Unit,
    loadCollections: suspend () -> List<PlexCollection>,
) {
    var query by remember(selectedSection.key) { mutableStateOf("") }
    var sortMode by remember(selectedSection.key) { mutableStateOf(SortMode.TITLE) }
    var genreFilter by remember(selectedSection.key) { mutableStateOf<String?>(null) }
    var decadeFilter by remember(selectedSection.key) { mutableStateOf<Int?>(null) }
    var dateAddedFilter by remember(selectedSection.key) { mutableStateOf<DateAddedBucket?>(null) }
    var collectionFilter by remember(selectedSection.key) { mutableStateOf<String?>(null) }
    var sortMenuExpanded by remember(selectedSection.key) { mutableStateOf(false) }
    var filtersExpanded by remember(selectedSection.key) { mutableStateOf(false) }
    var browseTab by remember(selectedSection.key) { mutableStateOf(BrowseTab.ALL) }
    var collections by remember(selectedSection.key) { mutableStateOf<List<PlexCollection>?>(null) }

    val availableGenres = remember(items) { items.flatMap { item -> item.genres.map { it.tag } }.distinct().sorted() }
    val availableDecades = remember(items) { items.mapNotNull { decadeOf(it) }.distinct().sortedDescending() }
    val displayedItems = remember(items, query, sortMode, genreFilter, decadeFilter, dateAddedFilter, collectionFilter) {
        applyLibraryFilters(items, query, sortMode, genreFilter, decadeFilter, dateAddedFilter, collectionFilter)
    }

    LaunchedEffect(browseTab, selectedSection.key) {
        if (browseTab == BrowseTab.COLLECTIONS && collections == null) {
            collections = runCatching { loadCollections() }.getOrDefault(emptyList())
        }
    }

    val allTabFocus = remember { FocusRequester() }
    val collectionsTabFocus = remember { FocusRequester() }
    val genreTabFocus = remember { FocusRequester() }
    val firstCollectionCardFocus = remember { FocusRequester() }
    val firstGenreTileFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val sortButtonFocus = remember { FocusRequester() }
    val filterButtonFocus = remember { FocusRequester() }
    val clearAllFocus = remember { FocusRequester() }
    val sortRowFocuses = remember { SortMode.entries.associateWith { FocusRequester() } }
    val genreFocuses = remember(availableGenres) { availableGenres.associateWith { FocusRequester() } }
    val decadeFocuses = remember(availableDecades) { availableDecades.associateWith { FocusRequester() } }
    val dateAddedFocuses = remember { DateAddedBucket.entries.associateWith { FocusRequester() } }

    BackHandler(enabled = sortMenuExpanded) {
        sortMenuExpanded = false
        runCatching { sortButtonFocus.requestFocus() }
    }
    BackHandler(enabled = filtersExpanded) {
        filtersExpanded = false
        runCatching { filterButtonFocus.requestFocus() }
    }

    LaunchedEffect(selectedSection.key) {
        runCatching { searchFocus.requestFocus() }
    }

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
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 16.dp, end = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrowseTab.entries.forEachIndexed { index, tab ->
                    val tabFocus = when (tab) {
                        BrowseTab.ALL -> allTabFocus
                        BrowseTab.COLLECTIONS -> collectionsTabFocus
                        BrowseTab.GENRE -> genreTabFocus
                    }
                    val downFocus = when (tab) {
                        BrowseTab.ALL -> searchFocus
                        BrowseTab.COLLECTIONS -> if (collections.isNullOrEmpty()) FocusRequester.Cancel else firstCollectionCardFocus
                        BrowseTab.GENRE -> if (availableGenres.isEmpty()) FocusRequester.Cancel else firstGenreTileFocus
                    }
                    ShumFilterChip(
                        selected = browseTab == tab,
                        onClick = { browseTab = tab },
                        modifier = Modifier
                            .focusRequester(tabFocus)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = downFocus
                                if (index > 0) {
                                    left = when (BrowseTab.entries[index - 1]) {
                                        BrowseTab.ALL -> allTabFocus
                                        BrowseTab.COLLECTIONS -> collectionsTabFocus
                                        BrowseTab.GENRE -> genreTabFocus
                                    }
                                }
                                if (index < BrowseTab.entries.lastIndex) {
                                    right = when (BrowseTab.entries[index + 1]) {
                                        BrowseTab.ALL -> allTabFocus
                                        BrowseTab.COLLECTIONS -> collectionsTabFocus
                                        BrowseTab.GENRE -> genreTabFocus
                                    }
                                }
                            },
                    ) {
                        Text(tab.label)
                    }
                }
            }

            if (browseTab == BrowseTab.ALL) {
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
                        onNavigateRight = { runCatching { sortButtonFocus.requestFocus() } },
                        modifier = Modifier
                            .background(AppSurfaceVariant)
                            .padding(12.dp)
                            .width(320.dp)
                            .focusRequester(searchFocus)
                            .focusProperties { up = allTabFocus },
                    )
                    ShumOutlinedButton(
                        onClick = { sortMenuExpanded = true },
                        modifier = Modifier
                            .focusRequester(sortButtonFocus)
                            .focusProperties { up = allTabFocus; left = searchFocus; right = filterButtonFocus },
                    ) {
                        Text("Sort: ${sortMode.label}")
                        Text(" ▾", modifier = Modifier.padding(start = 8.dp))
                    }
                    FilterTrigger(
                        appliedCount = listOfNotNull(genreFilter, decadeFilter, dateAddedFilter, collectionFilter).size,
                        onClick = { filtersExpanded = true },
                        modifier = Modifier
                            .focusRequester(filterButtonFocus)
                            .focusProperties { up = allTabFocus; left = sortButtonFocus },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onDpadLongPress(Key.DirectionUp) { runCatching { searchFocus.requestFocus() } },
                ) {
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
                                staggerDelayMs = (index % GRID_COLUMNS) * 120,
                            )
                        }
                    }
                    if (displayedItems.isEmpty()) {
                        Text("Nothing matches these filters.", modifier = Modifier.padding(32.dp))
                    }
                }
            } else if (browseTab == BrowseTab.COLLECTIONS) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val loadedCollections = collections
                    when {
                        loadedCollections == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingScreen("Loading collections…")
                        }
                        loadedCollections.isEmpty() -> Text("No collections found", modifier = Modifier.padding(32.dp))
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(GRID_COLUMNS),
                            contentPadding = PaddingValues(32.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(loadedCollections, key = { _, collection -> collection.ratingKey }) { index, collection ->
                                CollectionCard(
                                    server = server,
                                    collection = collection,
                                    onClick = {
                                        collectionFilter = collection.title
                                        browseTab = BrowseTab.ALL
                                        runCatching { searchFocus.requestFocus() }
                                    },
                                    modifier = if (index == 0) Modifier.focusRequester(firstCollectionCardFocus) else Modifier,
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (availableGenres.isEmpty()) {
                        Text("No genres found", modifier = Modifier.padding(32.dp))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(GRID_COLUMNS),
                            contentPadding = PaddingValues(32.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(availableGenres, key = { _, genre -> genre }) { index, genre ->
                                GenreTile(
                                    genre = genre,
                                    onClick = {
                                        genreFilter = genre
                                        browseTab = BrowseTab.ALL
                                        runCatching { searchFocus.requestFocus() }
                                    },
                                    modifier = if (index == 0) Modifier.focusRequester(firstGenreTileFocus) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }

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
                collectionFilter = collectionFilter,
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
                    collectionFilter = null
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
            Box(modifier = Modifier.width(16.dp)) {
                if (applied) Text("✓")
            }
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

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

@Composable
private fun FilterMenu(
    items: List<PlexLibraryItem>,
    query: String,
    availableGenres: List<String>,
    availableDecades: List<Int>,
    genreFilter: String?,
    decadeFilter: Int?,
    dateAddedFilter: DateAddedBucket?,
    collectionFilter: String?,
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
    val anyApplied = genreFilter != null || decadeFilter != null || dateAddedFilter != null || collectionFilter != null

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
                    val dimmed = applyLibraryFilters(
                        items, query, SortMode.TITLE, genre, decadeFilter, dateAddedFilter, collectionFilter,
                    ).isEmpty()
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
                    val dimmed = applyLibraryFilters(
                        items, query, SortMode.TITLE, genreFilter, decade, dateAddedFilter, collectionFilter,
                    ).isEmpty()
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
                val dimmed = applyLibraryFilters(
                    items, query, SortMode.TITLE, genreFilter, decadeFilter, bucket, collectionFilter,
                ).isEmpty()
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
                    noiseOpacity = 0.4f,
                    staggerDelayMs = staggerDelayMs,
                )
            }
        },
        title = {
            Text(text = item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp))
        },
    )
}

@Composable
private fun CollectionCard(
    server: PlexServer,
    collection: PlexCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShumCardContainer(
        modifier = modifier.width(160.dp),
        imageCard = { interactionSource ->
            ShumCard(
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            ) {
                ShumArtwork(
                    model = PlexImageUrl.of(server, collection.thumb),
                    contentDescription = collection.title,
                    modifier = Modifier.fillMaxSize(),
                    noiseOpacity = 0.4f,
                )
            }
        },
        title = {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(text = collection.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val childCount = collection.childCount
                if (childCount != null) {
                    Text(
                        text = "$childCount title${if (childCount == 1) "" else "s"}",
                        color = AppOnSurfaceVariant,
                    )
                }
            }
        },
    )
}

private val genreTileShape = RoundedCornerShape(8.dp)
private val genreTileColors = ShumColors(container = AppSurfaceVariant, content = AppWhite, focusedContainer = NeonPurple)
private val genreTileBorder = ShumBorder(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val genreTileGlow = ShumGlow(focusedColor = NeonPurpleGlow)

@Composable
private fun GenreTile(genre: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.width(160.dp).aspectRatio(2f / 3f),
        shape = genreTileShape,
        colors = genreTileColors,
        border = genreTileBorder,
        glow = genreTileGlow,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = genre,
            style = ShumTypography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
    }
}
