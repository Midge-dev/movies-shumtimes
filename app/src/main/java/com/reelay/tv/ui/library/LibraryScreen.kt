package com.reelay.tv.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelay.tv.data.plex.PlexCollection
import com.reelay.tv.data.plex.PlexImageUrl
import com.reelay.tv.data.plex.PlexLibraryItem
import com.reelay.tv.data.plex.PlexSection
import com.reelay.tv.data.plex.PlexServer
import com.reelay.tv.ui.common.LoadingScreen
import com.reelay.tv.ui.common.NeonScrollbar
import com.reelay.tv.ui.common.Artwork
import com.reelay.tv.ui.common.onDpadLongPress
import com.reelay.tv.ui.kit.FocusableSurface
import com.reelay.tv.ui.kit.Border
import com.reelay.tv.ui.kit.Card
import com.reelay.tv.ui.kit.CardContainer
import com.reelay.tv.ui.kit.Colors
import com.reelay.tv.ui.kit.Glow
import com.reelay.tv.ui.kit.Typography
import com.reelay.tv.ui.kit.Text
import com.reelay.tv.ui.theme.AppBackground
import com.reelay.tv.ui.theme.AppOnSurface
import com.reelay.tv.ui.theme.AppOnSurfaceVariant
import com.reelay.tv.ui.theme.AppSurface
import com.reelay.tv.ui.theme.AppSurfaceVariant
import com.reelay.tv.ui.theme.AppWhite
import com.reelay.tv.ui.theme.NeonPurple
import com.reelay.tv.ui.theme.NeonPurpleGlow
import com.reelay.tv.ui.theme.NeonPurpleGradient

private const val GRID_COLUMNS = 5

private enum class BrowseTab(val label: String) {
    ALL("All"),
    GENRE("Genres"),
    COLLECTIONS("Collections"),
    SEARCH("Search"),
}

@Composable
fun LibraryScreen(
    server: PlexServer,
    selectedSection: PlexSection,
    items: List<PlexLibraryItem>,
    onSelectItem: (PlexLibraryItem) -> Unit,
    loadCollections: suspend () -> List<PlexCollection>,
    onSelectCollection: (PlexCollection) -> Unit,
) {
    var genreFilter by remember(selectedSection.key) { mutableStateOf<String?>(null) }
    var decadeFilter by remember(selectedSection.key) { mutableStateOf<Int?>(null) }
    var dateAddedFilter by remember(selectedSection.key) { mutableStateOf<DateAddedBucket?>(null) }
    var browseTab by remember(selectedSection.key) { mutableStateOf(BrowseTab.ALL) }
    var collections by remember(selectedSection.key) { mutableStateOf<List<PlexCollection>?>(null) }
    var searchQuery by remember(selectedSection.key) { mutableStateOf("") }

    val availableGenres = remember(items) { items.flatMap { item -> item.genres.map { it.tag } }.distinct().sorted() }
    val availableDecades = remember(items) { items.mapNotNull { decadeOf(it) }.distinct().sortedDescending() }
    val genreResults = remember(items, genreFilter, decadeFilter, dateAddedFilter) {
        applyLibraryFilters(items, "", SortMode.TITLE, genreFilter, decadeFilter, dateAddedFilter)
    }
    val searchResults = remember(items, searchQuery) {
        if (searchQuery.isBlank()) emptyList() else applyLibraryFilters(items, searchQuery, SortMode.TITLE, null, null, null)
    }

    LaunchedEffect(browseTab, selectedSection.key) {
        if (browseTab == BrowseTab.COLLECTIONS && collections == null) {
            collections = runCatching { loadCollections() }.getOrDefault(emptyList())
        }
    }

    val allTabFocus = remember { FocusRequester() }
    val genreTabFocus = remember { FocusRequester() }
    val collectionsTabFocus = remember { FocusRequester() }
    val searchTabFocus = remember { FocusRequester() }
    val firstAllItemFocus = remember { FocusRequester() }
    val firstCollectionCardFocus = remember { FocusRequester() }
    val firstGenreResultFocus = remember { FocusRequester() }
    val clearAllFocus = remember { FocusRequester() }
    val genreFocuses = remember(availableGenres) { availableGenres.associateWith { FocusRequester() } }
    val decadeFocuses = remember(availableDecades) { availableDecades.associateWith { FocusRequester() } }
    val dateAddedFocuses = remember { DateAddedBucket.entries.associateWith { FocusRequester() } }
    val keyFocuses = remember { SEARCH_KEY_ROWS.flatten().associateWith { FocusRequester() } }

    val tabFocuses = mapOf(
        BrowseTab.ALL to allTabFocus,
        BrowseTab.GENRE to genreTabFocus,
        BrowseTab.COLLECTIONS to collectionsTabFocus,
        BrowseTab.SEARCH to searchTabFocus,
    )

    val firstGenrePanelFocus = genreFocuses.values.firstOrNull()
        ?: decadeFocuses.values.firstOrNull()
        ?: dateAddedFocuses.values.first()
    val firstSearchKeyFocus = keyFocuses.getValue(SEARCH_KEY_ROWS.first().first())

    LaunchedEffect(selectedSection.key) {
        runCatching { tabFocuses.getValue(browseTab).requestFocus() }
    }

    val downFocus = when (browseTab) {
        BrowseTab.ALL -> if (items.isEmpty()) FocusRequester.Cancel else firstAllItemFocus
        BrowseTab.GENRE -> firstGenrePanelFocus
        BrowseTab.COLLECTIONS -> if (collections.isNullOrEmpty()) FocusRequester.Cancel else firstCollectionCardFocus
        BrowseTab.SEARCH -> firstSearchKeyFocus
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 16.dp, end = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BrowseTab.entries.forEachIndexed { index, tab ->
                LibraryTab(
                    label = tab.label,
                    selected = browseTab == tab,
                    onClick = { browseTab = tab },
                    modifier = Modifier
                        .focusRequester(tabFocuses.getValue(tab))
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = downFocus
                            if (index > 0) left = tabFocuses.getValue(BrowseTab.entries[index - 1])
                            if (index < BrowseTab.entries.lastIndex) right = tabFocuses.getValue(BrowseTab.entries[index + 1])
                        },
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(AppSurfaceVariant))

        when (browseTab) {
            BrowseTab.ALL -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(selectedSection.title, style = Typography.titleMedium)
                    Text("${items.size} titles · A–Z", color = AppOnSurfaceVariant)
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(GRID_COLUMNS),
                        contentPadding = PaddingValues(32.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(items, key = { _, item -> item.ratingKey }) { index, item ->
                            LibraryPoster(
                                server = server,
                                item = item,
                                onClick = { onSelectItem(item) },
                                staggerDelayMs = (index % GRID_COLUMNS) * 120,
                                modifier = if (index == 0) Modifier.focusRequester(firstAllItemFocus) else Modifier,
                            )
                        }
                    }
                    if (items.isEmpty()) {
                        Text("Nothing in this library yet.", modifier = Modifier.padding(32.dp))
                    }
                }
            }

            BrowseTab.GENRE -> {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    GenreFilterPanel(
                        items = items,
                        availableGenres = availableGenres,
                        availableDecades = availableDecades,
                        genreFilter = genreFilter,
                        decadeFilter = decadeFilter,
                        dateAddedFilter = dateAddedFilter,
                        genreFocuses = genreFocuses,
                        decadeFocuses = decadeFocuses,
                        dateAddedFocuses = dateAddedFocuses,
                        clearAllFocus = clearAllFocus,
                        genreTabFocus = genreTabFocus,
                        firstResultFocus = if (genreResults.isEmpty()) null else firstGenreResultFocus,
                        onGenreSelect = { genreFilter = if (genreFilter == it) null else it },
                        onDecadeSelect = { decadeFilter = if (decadeFilter == it) null else it },
                        onDateAddedSelect = { dateAddedFilter = if (dateAddedFilter == it) null else it },
                        onClearAll = { genreFilter = null; decadeFilter = null; dateAddedFilter = null },
                        modifier = Modifier.fillMaxHeight(),
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 32.dp, vertical = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            genreFilter?.let { AppliedFilterChip(formatGenreLabel(it)) }
                            decadeFilter?.let { AppliedFilterChip("${it}s") }
                            dateAddedFilter?.let { AppliedFilterChip(it.label) }
                            Box(modifier = Modifier.weight(1f))
                            Text("${genreResults.size} titles · Sort: Title", color = AppOnSurfaceVariant)
                        }
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                contentPadding = PaddingValues(top = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                itemsIndexed(genreResults, key = { _, item -> item.ratingKey }) { index, item ->
                                    LibraryPoster(
                                        server = server,
                                        item = item,
                                        onClick = { onSelectItem(item) },
                                        modifier = if (index == 0) Modifier.focusRequester(firstGenreResultFocus) else Modifier,
                                    )
                                }
                            }
                            if (genreResults.isEmpty()) {
                                Text("Nothing matches these filters.", modifier = Modifier.padding(top = 24.dp))
                            }
                        }
                    }
                }
            }

            BrowseTab.COLLECTIONS -> {
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
                                    onClick = { onSelectCollection(collection) },
                                    modifier = if (index == 0) Modifier.focusRequester(firstCollectionCardFocus) else Modifier,
                                )
                            }
                        }
                    }
                }
            }

            BrowseTab.SEARCH -> {
                Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp)) {
                    Column(modifier = Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppSurface, RoundedCornerShape(8.dp))
                                .border(BorderStroke(2.dp, AppSurfaceVariant), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = searchQuery.ifEmpty { "Type a title…" },
                                color = if (searchQuery.isEmpty()) AppOnSurfaceVariant else AppWhite,
                            )
                        }
                        SearchKeyboard(
                            keyFocuses = keyFocuses,
                            onChar = { searchQuery += it },
                            onBackspace = { searchQuery = searchQuery.dropLast(1) },
                            onClear = { searchQuery = "" },
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 48.dp)) {
                        Text(
                            if (searchQuery.isBlank()) "Results" else "Results · ${searchResults.size} titles for “$searchQuery”",
                            color = AppOnSurfaceVariant,
                        )
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            if (searchQuery.isNotBlank()) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    contentPadding = PaddingValues(top = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(24.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    itemsIndexed(searchResults, key = { _, item -> item.ratingKey }) { _, item ->
                                        LibraryPoster(server = server, item = item, onClick = { onSelectItem(item) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TabShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
private val tabColors = Colors(
    container = AppSurface,
    content = AppOnSurfaceVariant,
    focusedContainer = NeonPurple,
    focusedContent = AppWhite,
    selectedContainer = AppBackground,
    selectedContent = AppOnSurface,
)
private val tabBorder = Border(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val tabGlow = Glow(focusedColor = NeonPurpleGlow)

@Composable
private fun LibraryTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        selected = selected,
        modifier = modifier.height(if (selected) 60.dp else 48.dp),
        shape = TabShape,
        colors = tabColors,
        border = tabBorder,
        glow = tabGlow,
    ) {
        if (selected) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(listOf(NeonPurpleGlow, NeonPurple)),
                            RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                        ),
                )
            }
        }
        Text(
            text = label,
            style = if (selected) Typography.titleMedium else Typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 26.dp),
        )
    }
}

@Composable
private fun AppliedFilterChip(label: String) {
    Box(
        modifier = Modifier
            .background(NeonPurple, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, color = AppWhite)
    }
}

private val MenuShape = RoundedCornerShape(8.dp)

private val menuRowColors = Colors(
    container = Color.Transparent,
    content = AppWhite,
    focusedContainer = NeonPurple,
    selectedContainer = NeonPurple.copy(alpha = 0.35f),
)
private val menuRowBorder = Border(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val menuRowGlow = Glow(focusedColor = NeonPurpleGlow)

private val missingSpaceAfterAmpersand = Regex("&(?=\\S)")

private fun formatGenreLabel(genre: String): String = genre.replace(missingSpaceAfterAmpersand, "& ")

@Composable
private fun MenuSectionHeader(label: String) {
    Text(
        text = label.uppercase(),
        style = Typography.bodySmall.copy(letterSpacing = 1.5.sp),
        color = AppOnSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
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
        modifier = modifier.height(46.dp),
        selected = applied,
        shape = MenuShape,
        colors = colors,
        border = menuRowBorder,
        glow = menuRowGlow,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (applied) Text("✓")
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GenreFilterPanel(
    items: List<PlexLibraryItem>,
    availableGenres: List<String>,
    availableDecades: List<Int>,
    genreFilter: String?,
    decadeFilter: Int?,
    dateAddedFilter: DateAddedBucket?,
    genreFocuses: Map<String, FocusRequester>,
    decadeFocuses: Map<Int, FocusRequester>,
    dateAddedFocuses: Map<DateAddedBucket, FocusRequester>,
    clearAllFocus: FocusRequester,
    genreTabFocus: FocusRequester,
    firstResultFocus: FocusRequester?,
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
            else -> genreTabFocus
        }
        down = if (index < orderedFocuses.lastIndex) orderedFocuses[index + 1] else FocusRequester.Cancel
        right = firstResultFocus ?: FocusRequester.Cancel
    }

    Row(
        modifier = modifier
            .background(AppSurface)
            .focusGroup()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.verticalScroll(scrollState),
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
                            up = genreTabFocus
                            down = orderedFocuses.firstOrNull() ?: FocusRequester.Cancel
                            right = firstResultFocus ?: FocusRequester.Cancel
                        },
                )
            }
            if (availableGenres.isNotEmpty()) {
                MenuSectionHeader("Genre")
                availableGenres.forEachIndexed { index, genre ->
                    val dimmed = applyLibraryFilters(items, "", SortMode.TITLE, genre, decadeFilter, dateAddedFilter).isEmpty()
                    MenuOptionRow(
                        label = formatGenreLabel(genre),
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
                    val dimmed = applyLibraryFilters(items, "", SortMode.TITLE, genreFilter, decade, dateAddedFilter).isEmpty()
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
                val dimmed = applyLibraryFilters(items, "", SortMode.TITLE, genreFilter, decadeFilter, bucket).isEmpty()
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

@Composable
private fun LibraryPoster(
    server: PlexServer,
    item: PlexLibraryItem,
    onClick: () -> Unit,
    staggerDelayMs: Int = 0,
    modifier: Modifier = Modifier,
) {
    CardContainer(
        modifier = modifier.width(160.dp),
        imageCard = { interactionSource ->
            Card(
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            ) {
                Artwork(
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
    CardContainer(
        modifier = modifier.width(160.dp),
        imageCard = { interactionSource ->
            Card(
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            ) {
                Artwork(
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

private enum class SearchKeyAction { CHAR, DELETE, CLEAR }

private data class SearchKey(
    val label: String,
    val insert: String? = null,
    val action: SearchKeyAction = SearchKeyAction.CHAR,
    val span: Int = 1,
)

private val SEARCH_KEY_ROWS: List<List<SearchKey>> = run {
    val charKeys = (('A'..'Z') + ('0'..'9')).map { c -> SearchKey(label = c.toString(), insert = c.toString()) }
    val rows = charKeys.chunked(6).toMutableList()
    rows += listOf(
        SearchKey(label = "SPACE", insert = " ", span = 2),
        SearchKey(label = "⌫ DELETE", action = SearchKeyAction.DELETE, span = 2),
        SearchKey(label = "CLEAR", action = SearchKeyAction.CLEAR, span = 2),
    )
    rows
}

private val SEARCH_KEY_GRID: List<List<SearchKey>> =
    SEARCH_KEY_ROWS.map { row -> row.flatMap { key -> List(key.span) { key } } }

private val searchKeyShape = RoundedCornerShape(8.dp)
private val searchKeyColors = Colors(container = AppSurface, content = AppWhite, focusedContainer = NeonPurple, focusedContent = AppWhite)
private val searchKeyBorder = Border(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val searchKeyGlow = Glow(focusedColor = NeonPurpleGlow)

@Composable
private fun SearchKeyboard(
    keyFocuses: Map<SearchKey, FocusRequester>,
    onChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SEARCH_KEY_ROWS.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                var col = 0
                row.forEach { key ->
                    val colStart = col
                    val colEnd = col + key.span - 1
                    col += key.span
                    FocusableSurface(
                        onClick = {
                            when (key.action) {
                                SearchKeyAction.CHAR -> key.insert?.let(onChar)
                                SearchKeyAction.DELETE -> onBackspace()
                                SearchKeyAction.CLEAR -> onClear()
                            }
                        },
                        modifier = Modifier
                            .weight(key.span.toFloat())
                            .height(30.dp)
                            .focusRequester(keyFocuses.getValue(key))
                            .focusProperties {
                                up = if (rowIndex > 0) keyFocuses.getValue(SEARCH_KEY_GRID[rowIndex - 1][colStart]) else FocusRequester.Cancel
                                down = if (rowIndex < SEARCH_KEY_ROWS.lastIndex) {
                                    keyFocuses.getValue(SEARCH_KEY_GRID[rowIndex + 1][colStart])
                                } else {
                                    FocusRequester.Cancel
                                }
                                if (colStart > 0) left = keyFocuses.getValue(SEARCH_KEY_GRID[rowIndex][colStart - 1])
                                if (colEnd < 5) right = keyFocuses.getValue(SEARCH_KEY_GRID[rowIndex][colEnd + 1])
                            }
                            .then(
                                if (key.action == SearchKeyAction.DELETE) {
                                    Modifier.onDpadLongPress(Key.DirectionCenter) { onClear() }
                                } else {
                                    Modifier
                                },
                            ),
                        shape = searchKeyShape,
                        colors = searchKeyColors,
                        border = searchKeyBorder,
                        glow = searchKeyGlow,
                    ) {
                        Text(
                            key.label,
                            textAlign = TextAlign.Center,
                            style = Typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
