package com.moviesshumtimes.tv.ui.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexLibraryItem
import com.moviesshumtimes.tv.data.plex.PlexSection
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.ui.theme.NeonPurple

@Composable
fun LibraryScreen(
    server: PlexServer,
    sections: List<PlexSection>,
    selectedSection: PlexSection,
    items: List<PlexLibraryItem>,
    onSelectSection: (PlexSection) -> Unit,
    onSelectItem: (PlexLibraryItem) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var query by remember(selectedSection.key) { mutableStateOf("") }
    var sortMode by remember(selectedSection.key) { mutableStateOf(SortMode.TITLE) }
    var genreFilter by remember(selectedSection.key) { mutableStateOf<String?>(null) }
    var decadeFilter by remember(selectedSection.key) { mutableStateOf<Int?>(null) }
    var dateAddedFilter by remember(selectedSection.key) { mutableStateOf<DateAddedBucket?>(null) }
    var filtersExpanded by remember(selectedSection.key) { mutableStateOf(false) }

    val availableGenres = remember(items) { items.flatMap { item -> item.genres.map { it.tag } }.distinct().sorted() }
    val availableDecades = remember(items) { items.mapNotNull { decadeOf(it) }.distinct().sortedDescending() }
    val displayedItems = remember(items, query, sortMode, genreFilter, decadeFilter, dateAddedFilter) {
        applyLibraryFilters(items, query, sortMode, genreFilter, decadeFilter, dateAddedFilter)
    }

    val searchFocus = remember { FocusRequester() }
    val sortButtonFocus = remember { FocusRequester() }
    val filterPanelFirstFocus = remember { FocusRequester() }

    BackHandler(enabled = filtersExpanded) { filtersExpanded = false }

    LaunchedEffect(filtersExpanded) {
        if (filtersExpanded) runCatching { filterPanelFirstFocus.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                    items(sections, key = { it.key }) { section ->
                        val selected = section.key == selectedSection.key
                        Button(
                            onClick = { onSelectSection(section) },
                            colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                        ) {
                            Text(if (selected) "[${section.title}]" else section.title)
                        }
                    }
                }
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                ) { Text("Settings") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Search ${selectedSection.title}…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                        .width(320.dp)
                        .focusRequester(searchFocus)
                        .focusProperties { down = sortButtonFocus },
                )
                Button(
                    onClick = {
                        sortMode = when (sortMode) {
                            SortMode.TITLE -> SortMode.RELEASE_DATE
                            SortMode.RELEASE_DATE -> SortMode.DATE_ADDED
                            SortMode.DATE_ADDED -> SortMode.TITLE
                        }
                    },
                    colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                    modifier = Modifier.focusRequester(sortButtonFocus).focusProperties { up = searchFocus },
                ) { Text("Sort: ${sortMode.label}") }
                Button(
                    onClick = { filtersExpanded = true },
                    colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                ) { Text(if (filtersExpanded) "[Filter]" else "Filter") }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(displayedItems, key = { it.ratingKey }) { item ->
                    LibraryPoster(server = server, item = item, onClick = { onSelectItem(item) })
                }
            }
        }

        if (filtersExpanded) {
            FilterDropdown(
                availableGenres = availableGenres,
                availableDecades = availableDecades,
                genreFilter = genreFilter,
                decadeFilter = decadeFilter,
                dateAddedFilter = dateAddedFilter,
                onGenreSelect = { genreFilter = if (genreFilter == it) null else it },
                onDecadeSelect = { decadeFilter = if (decadeFilter == it) null else it },
                onDateAddedSelect = { dateAddedFilter = if (dateAddedFilter == it) null else it },
                firstItemFocusRequester = filterPanelFirstFocus,
                onDismiss = { filtersExpanded = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 32.dp),
            )
        }
    }
}

// A floating, scrollable panel rather than the previous always-inline chip
// rows — dismisses itself as soon as focus leaves it for any other control,
// so there's no separate "close" step.
@Composable
private fun FilterDropdown(
    availableGenres: List<String>,
    availableDecades: List<Int>,
    genreFilter: String?,
    decadeFilter: Int?,
    dateAddedFilter: DateAddedBucket?,
    onGenreSelect: (String) -> Unit,
    onDecadeSelect: (Int) -> Unit,
    onDateAddedSelect: (DateAddedBucket) -> Unit,
    firstItemFocusRequester: FocusRequester,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The panel composes with hasFocus=false for an instant before the
    // requested initial focus actually lands inside it — without this
    // guard, that first callback would dismiss the panel before it's even
    // visible. Only treat a hasFocus=false callback as "closed" once focus
    // has genuinely been inside the panel at least once.
    var hasBeenFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .width(320.dp)
            .heightIn(max = 480.dp)
            .background(MaterialTheme.colorScheme.surface)
            .focusGroup()
            .onFocusChanged { state ->
                if (state.hasFocus) {
                    hasBeenFocused = true
                } else if (hasBeenFocused) {
                    onDismiss()
                }
            },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (availableGenres.isNotEmpty()) {
                item { FilterSectionHeader("Genre") }
                itemsIndexed(availableGenres) { index, genre ->
                    FilterOptionRow(
                        label = genre,
                        selected = genre == genreFilter,
                        onClick = { onGenreSelect(genre) },
                        modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    )
                }
            }
            if (availableDecades.isNotEmpty()) {
                item { FilterSectionHeader("Release Date") }
                items(availableDecades) { decade ->
                    FilterOptionRow(
                        label = "${decade}s",
                        selected = decade == decadeFilter,
                        onClick = { onDecadeSelect(decade) },
                        modifier = if (availableGenres.isEmpty() && decade == availableDecades.first()) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                }
            }
            item { FilterSectionHeader("Date Added") }
            itemsIndexed(DateAddedBucket.entries) { index, bucket ->
                FilterOptionRow(
                    label = bucket.label,
                    selected = bucket == dateAddedFilter,
                    onClick = { onDateAddedSelect(bucket) },
                    modifier = if (availableGenres.isEmpty() && availableDecades.isEmpty() && index == 0) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterSectionHeader(label: String) {
    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun FilterOptionRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(if (selected) "[$label]" else label)
    }
}

@Composable
private fun LibraryPoster(server: PlexServer, item: PlexLibraryItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(160.dp)) {
        Column {
            AsyncImage(
                model = PlexImageUrl.of(server, item.thumb),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            Text(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
