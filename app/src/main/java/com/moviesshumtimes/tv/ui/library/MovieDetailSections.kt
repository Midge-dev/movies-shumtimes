package com.moviesshumtimes.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.data.plex.PlexOnDeckItem
import com.moviesshumtimes.tv.data.plex.PlexPerson
import com.moviesshumtimes.tv.data.plex.PlexReview
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.ui.common.ShumArtwork
import com.moviesshumtimes.tv.ui.kit.ShumCard
import com.moviesshumtimes.tv.ui.kit.ShumCardContainer
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppOnSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow

@Composable
fun CastCrewRow(
    server: PlexServer,
    cast: List<PlexPerson>,
    crew: List<PlexPerson>,
    onSelectPerson: (PlexPerson) -> Unit,
) {
    val people = cast + crew
    if (people.isEmpty()) return
    Column {
        Text(
            text = "Cast & Crew",
            style = ShumTypography.titleLarge,
            modifier = Modifier.padding(start = 32.dp, bottom = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(people, key = { _, person -> person.id ?: person.tag }) { _, person ->
                val subtitle = person.role ?: crewTitle(person, crew)
                CastMemberAvatar(
                    server = server,
                    person = person,
                    subtitle = subtitle,
                    onClick = { onSelectPerson(person) },
                )
            }
        }
    }
}

private fun crewTitle(person: PlexPerson, crew: List<PlexPerson>): String? =
    if (person in crew) "Crew" else null

@Composable
private fun CastMemberAvatar(
    server: PlexServer,
    person: PlexPerson,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(112.dp),
    ) {
        ShumCard(onClick = onClick, shape = CircleShape, modifier = Modifier.size(84.dp)) {
            if (person.thumb != null) {
                ShumArtwork(
                    model = PlexImageUrl.of(server, person.thumb),
                    contentDescription = person.tag,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(AppSurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(person.tag.take(1).uppercase(), style = ShumTypography.titleMedium, color = AppWhite)
                }
            }
        }
        Text(
            text = person.tag,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = AppOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
fun RatingsReviewsSection(
    rating: Double?,
    audienceRating: Double?,
    ratingImage: String?,
    audienceRatingImage: String?,
    reviews: List<PlexReview>,
) {
    if (rating == null && audienceRating == null && reviews.isEmpty()) return
    Column {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 32.dp, bottom = 16.dp),
        ) {
            Text(text = "Ratings & Reviews", style = ShumTypography.titleLarge)
            if (reviews.isNotEmpty()) {
                Text(text = "${reviews.size} critic reviews", color = AppOnSurfaceVariant)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            if (rating != null) RatingTile(percent = (rating * 10).toInt(), label = "Critics", image = ratingImage)
            if (audienceRating != null) {
                RatingTile(percent = (audienceRating * 10).toInt(), label = "Audience", image = audienceRatingImage)
            }
        }
        if (reviews.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                itemsIndexed(reviews.take(3), key = { _, review -> review.tag }) { _, review -> ReviewCard(review) }
            }
        }
    }
}

@Composable
private fun RatingTile(percent: Int, label: String, image: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .background(AppWhite.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(ratingBadgeColor(image), RoundedCornerShape(6.dp)),
        )
        Column {
            Text(text = "$percent%", style = ShumTypography.headlineMedium)
            Text(text = label, color = AppOnSurfaceVariant)
        }
    }
}

private fun ratingBadgeColor(image: String?): Color {
    val suffix = image?.substringAfterLast('.')?.lowercase()
    return when (suffix) {
        "fresh", "ripe", "certified" -> Color(0xFF2E7D4F)
        "rotten", "spilled" -> Color(0xFF7D2E2E)
        else -> AppSurfaceVariant
    }
}

@Composable
private fun ReviewCard(review: PlexReview) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .background(AppWhite.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            .padding(18.dp),
    ) {
        val source = review.source
        if (source != null) {
            Text(text = source.uppercase(), color = NeonPurpleGlow)
        }
        Text(
            text = review.text,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
fun PosterRow(title: String, items: List<PlexOnDeckItem>, server: PlexServer, onClick: (PlexOnDeckItem) -> Unit) {
    if (items.isEmpty()) return
    Column {
        Text(
            text = title,
            style = ShumTypography.titleLarge,
            modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.ratingKey }) { _, item ->
                RelatedPoster(server = server, item = item, onClick = { onClick(item) })
            }
        }
    }
}

@Composable
private fun RelatedPoster(server: PlexServer, item: PlexOnDeckItem, onClick: () -> Unit) {
    ShumCardContainer(
        modifier = Modifier.width(132.dp),
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
                )
            }
        },
        title = {
            Text(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        },
    )
}
