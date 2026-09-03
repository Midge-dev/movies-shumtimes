package com.moviesshumtimes.tv.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.moviesshumtimes.tv.data.plex.PlexAccount
import com.moviesshumtimes.tv.data.plex.PlexSection
import com.moviesshumtimes.tv.ui.common.onDpadLongPress
import com.moviesshumtimes.tv.ui.kit.FocusableSurface
import com.moviesshumtimes.tv.ui.kit.Icon
import com.moviesshumtimes.tv.ui.kit.ShumColors
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppSurface
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple

private const val SECTION_TYPE_SHOW = "show"

private val COLLAPSED_RAIL_WIDTH = 80.dp
private val EXPANDED_RAIL_WIDTH = 236.dp
private val RailItemHeight = 48.dp

private const val RAIL_ANIM_DURATION_MS = 200
private const val LABEL_FADE_DELAY_MS = 80
private const val LABEL_FADE_OUT_MS = 100

@Composable
fun AppNavigationDrawer(
    sections: List<PlexSection>,
    selectedSectionKey: String?,
    isSettingsSelected: Boolean,
    isHomeSelected: Boolean,
    onSelectSection: (PlexSection) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHome: () -> Unit,
    account: PlexAccount?,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (expanded) EXPANDED_RAIL_WIDTH else COLLAPSED_RAIL_WIDTH,
        animationSpec = tween(durationMillis = RAIL_ANIM_DURATION_MS),
        label = "railWidth",
    )
    val homeItemFocus = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = COLLAPSED_RAIL_WIDTH)
                .onDpadLongPress(Key.DirectionLeft) { runCatching { homeItemFocus.requestFocus() } },
        ) {
            content()
        }
        Column(
            modifier = Modifier
                .zIndex(1f)
                .fillMaxHeight()
                .width(railWidth)
                .background(AppSurface)
                .focusGroup()
                .onFocusChanged { expanded = it.hasFocus }
                .padding(vertical = 24.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            UserAvatarItem(account = account, expanded = expanded)
            Spacer(modifier = Modifier.height(8.dp))
            SidebarItem(
                icon = Icons.Default.Home,
                label = "Home",
                selected = isHomeSelected,
                expanded = expanded,
                onClick = onOpenHome,
                modifier = Modifier.focusRequester(homeItemFocus),
            )
            for (section in sections) {
                SidebarItem(
                    icon = if (section.type == SECTION_TYPE_SHOW) Icons.Default.Tv else Icons.Default.Movie,
                    label = section.title,
                    selected = !isSettingsSelected && !isHomeSelected && section.key == selectedSectionKey,
                    expanded = expanded,
                    onClick = { onSelectSection(section) },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            SidebarItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                selected = isSettingsSelected,
                expanded = expanded,
                onClick = onOpenSettings,
            )
        }
    }
}

private val railItemColors = ShumColors(
    container = Color.Transparent,
    content = AppWhite,
    focusedContainer = NeonPurple,
    selectedContainer = NeonPurple.copy(alpha = 0.35f),
)
private val RailItemShape = CircleShape

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val itemFocused by interactionSource.collectIsFocusedAsState()

    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(RailItemHeight),
        selected = selected,
        shape = RailItemShape,
        colors = railItemColors,
        interactionSource = interactionSource,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = if (itemFocused) null else label, tint = AppWhite)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(durationMillis = RAIL_ANIM_DURATION_MS - LABEL_FADE_DELAY_MS, delayMillis = LABEL_FADE_DELAY_MS)),
                exit = fadeOut(tween(durationMillis = LABEL_FADE_OUT_MS)),
            ) {
                Text(label, color = AppWhite, modifier = Modifier.padding(start = 14.dp))
            }
        }
    }
}

@Composable
private fun UserAvatarItem(account: PlexAccount?, expanded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(RailItemHeight).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(NeonPurple.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = account?.thumb
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    text = (account?.username?.take(1) ?: "?").uppercase(),
                    style = ShumTypography.bodyLarge,
                    color = AppWhite,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(durationMillis = RAIL_ANIM_DURATION_MS - LABEL_FADE_DELAY_MS, delayMillis = LABEL_FADE_DELAY_MS)),
            exit = fadeOut(tween(durationMillis = LABEL_FADE_OUT_MS)),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = account?.username ?: "",
                color = AppWhite,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .padding(start = 14.dp)
                    .basicMarquee(),
            )
        }
    }
}
