package com.moviesshumtimes.tv.ui.library

import com.moviesshumtimes.tv.data.plex.PlexLibraryItem

enum class SortMode(val label: String) {
    TITLE("Title"),
    RELEASE_DATE("Release Date"),
    DATE_ADDED("Date Added"),
}

enum class DateAddedBucket(val label: String) {
    LAST_30_DAYS("Last 30 days"),
    LAST_6_MONTHS("Last 6 months"),
    THIS_YEAR("This year"),
    OLDER("Older"),
}

private const val SECONDS_PER_DAY = 86_400L

private fun releaseYear(item: PlexLibraryItem): Int? =
    item.originallyAvailableAt?.take(4)?.toIntOrNull() ?: item.year

fun decadeOf(item: PlexLibraryItem): Int? = releaseYear(item)?.let { (it / 10) * 10 }

// Cumulative, not partitioned — an item added 10 days ago should still
// match "Last 6 months", matching how Plex's own recency filters read.
fun matchesDateAddedBucket(item: PlexLibraryItem, bucket: DateAddedBucket, nowEpochSeconds: Long): Boolean {
    val addedAt = item.addedAt ?: return false
    val ageDays = (nowEpochSeconds - addedAt) / SECONDS_PER_DAY
    return when (bucket) {
        DateAddedBucket.LAST_30_DAYS -> ageDays <= 30
        DateAddedBucket.LAST_6_MONTHS -> ageDays <= 182
        DateAddedBucket.THIS_YEAR -> ageDays <= 365
        DateAddedBucket.OLDER -> ageDays > 365
    }
}

fun applyLibraryFilters(
    items: List<PlexLibraryItem>,
    query: String,
    sortMode: SortMode,
    genre: String?,
    decade: Int?,
    dateAddedBucket: DateAddedBucket?,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
): List<PlexLibraryItem> {
    var result = items.asSequence()
    if (query.isNotBlank()) {
        result = result.filter { it.title.contains(query, ignoreCase = true) }
    }
    if (genre != null) {
        result = result.filter { item -> item.genres.any { it.tag == genre } }
    }
    if (decade != null) {
        result = result.filter { decadeOf(it) == decade }
    }
    if (dateAddedBucket != null) {
        result = result.filter { matchesDateAddedBucket(it, dateAddedBucket, nowEpochSeconds) }
    }
    val sorted = when (sortMode) {
        SortMode.TITLE -> result.sortedBy { it.title.lowercase() }
        SortMode.RELEASE_DATE -> result.sortedByDescending { releaseYear(it) ?: Int.MIN_VALUE }
        SortMode.DATE_ADDED -> result.sortedByDescending { it.addedAt ?: 0L }
    }
    return sorted.toList()
}
