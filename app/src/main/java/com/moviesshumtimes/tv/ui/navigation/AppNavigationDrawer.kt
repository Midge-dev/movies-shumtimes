package com.moviesshumtimes.tv.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.data.plex.PlexSection
import com.moviesshumtimes.tv.ui.theme.AppSurface
import com.moviesshumtimes.tv.ui.theme.NeonPurple

private const val SECTION_TYPE_SHOW = "show"
private val COLLAPSED_WIDTH = 72.dp
private val EXPANDED_WIDTH = 220.dp

// Half of tv-material3's built-in NavigationDrawer timing (its width
// animation and per-item label reveal both use Compose's ~300ms defaults
// internally — fadeIn()/slideIn() with no explicit spec, animateDpAsState
// with no explicit spec — and neither is exposed as a parameter on the
// public NavigationDrawer/NavigationDrawerItem API in this library
// version, confirmed by reading the actual source). Getting real control
// over the speed meant dropping the library component for a custom rail
// built on plain Compose primitives with an explicit spec instead.
private const val ANIMATION_MS = 150

// Persistent side rail replacing the old top-of-screen section-tabs row and
// separate Settings button — a collapsed icon-only rail that expands to
// show labels when it (or a child) has focus, matching the pattern every
// major TV streaming app uses instead of top navigation. Wraps every
// "browsing" screen (Library, MovieDetail, ShowSeasons, ShowEpisodes,
// Settings) from MainActivity's AppRoot; Lobby/Player/Auth/RelaySetup stay
// outside it since they're either full-screen or one-time flows, not
// destinations you navigate between.
@Composable
fun AppNavigationDrawer(
    sections: List<PlexSection>,
    selectedSectionKey: String?,
    isSettingsSelected: Boolean,
    isHomeSelected: Boolean,
    onSelectSection: (PlexSection) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHome: () -> Unit,
    content: @Composable () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (hasFocus) EXPANDED_WIDTH else COLLAPSED_WIDTH,
        animationSpec = tween(ANIMATION_MS),
        label = "sidebarWidth",
    )

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .background(AppSurface)
                .onFocusChanged { hasFocus = it.hasFocus }
                .focusGroup()
                .padding(vertical = 24.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SidebarItem(
                icon = Icons.Default.Home,
                label = "Home",
                selected = isHomeSelected,
                labelVisible = hasFocus,
                onClick = onOpenHome,
            )
            for (section in sections) {
                SidebarItem(
                    icon = if (section.type == SECTION_TYPE_SHOW) Icons.Default.Tv else Icons.Default.Movie,
                    label = section.title,
                    selected = !isSettingsSelected && !isHomeSelected && section.key == selectedSectionKey,
                    labelVisible = hasFocus,
                    onClick = { onSelectSection(section) },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            SidebarItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                selected = isSettingsSelected,
                labelVisible = hasFocus,
                onClick = onOpenSettings,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

// Background color alone carries focused/selected/idle state; text and
// icon stay plain white in every state. This used to two-tone the text too
// (NeonPurpleGlow when focused, NeonPurple when selected) matching buttons/
// cards elsewhere, but both combinations turned out low-contrast and hard
// to read against their own NeonPurple-family backgrounds (reported as an
// accessibility issue, confirmed on real hardware/photos — looks fine in a
// quick screenshot glance but reads as genuinely hard to read on an actual
// TV). White stays legible against every background state here.
@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    labelVisible: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val backgroundColor = when {
        focused -> NeonPurple
        selected -> NeonPurple.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val contentColor = Color.White

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
    ) {
        // Compose Foundation's clickable() already makes this focusable and
        // handles DPAD_CENTER/Enter -> onClick — no separate .focusable()
        // needed. contentDescription carries the label for accessibility
        // when it's not visibly shown (collapsed state).
        Icon(imageVector = icon, contentDescription = if (labelVisible) null else label, tint = contentColor)
        AnimatedVisibility(
            visible = labelVisible,
            enter = fadeIn(tween(ANIMATION_MS)),
            exit = fadeOut(tween(ANIMATION_MS)),
        ) {
            Text(text = label, color = contentColor, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
