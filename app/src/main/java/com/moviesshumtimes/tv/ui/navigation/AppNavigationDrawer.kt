package com.moviesshumtimes.tv.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moviesshumtimes.tv.data.plex.PlexSection
import com.moviesshumtimes.tv.ui.kit.FocusableSurface
import com.moviesshumtimes.tv.ui.kit.Icon
import com.moviesshumtimes.tv.ui.kit.ShumColors
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppSurface
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple

private const val SECTION_TYPE_SHOW = "show"

// 48dp item + this Column's own 12dp horizontal padding on each side — the
// actual rendered width of the collapsed rail, and how much start-padding
// content needs to clear it (this doesn't inset content itself, same as the
// tv-material3 version it replaced — content supplies its own start padding
// below).
private val COLLAPSED_RAIL_WIDTH = 80.dp
private val EXPANDED_RAIL_WIDTH = 236.dp
private val RailItemHeight = 48.dp

// The width tween and each label's fade must complete at exactly the same
// moment — see the "stretch flash" history below. RAIL_ANIM_DURATION_MS
// drives the rail width; the label fade-in reuses it minus its own delay so
// both finish together, and starts partway in (LABEL_FADE_DELAY_MS) so text
// doesn't render before the rail has grown enough room for it. Collapsing
// fades the label out fast and with no delay instead, so it's gone well
// before the rail visually narrows back down — nothing to clip.
private const val RAIL_ANIM_DURATION_MS = 200
private const val LABEL_FADE_DELAY_MS = 80
private const val LABEL_FADE_OUT_MS = 100

// Persistent side rail replacing the old top-of-screen section-tabs row and
// separate Settings button — a collapsed icon-only rail that expands to show
// labels when it (or a child) has focus, matching the pattern every major TV
// streaming app uses instead of top navigation. Wraps every "browsing"
// screen (Library, MovieDetail, ShowSeasons, ShowEpisodes, Settings) from
// MainActivity's AppRoot; Lobby/Player/Auth/RelaySetup stay outside it since
// they're either full-screen or one-time flows, not destinations you
// navigate between.
//
// A prior hand-rolled version (Box + animateDpAsState) had a real,
// unresolved "stretch" flash on screen entry, traced to two independently-
// timed animations racing each other: an outer Column-width tween plus a
// per-item label AnimatedVisibility fade, each keyed off its own separate
// focus signal. This version drives both from the exact same `expanded`
// boolean — one focusGroup+onFocusChanged at the rail's root, read by every
// item's label fade and the outer width tween alike — so there's structurally
// nothing left to race. The rail overlays on top of content rather than
// pushing it (content keeps a fixed COLLAPSED_RAIL_WIDTH start inset
// regardless of expand state) so an open rail never darkens or reflows
// anything behind it.
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
    var expanded by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (expanded) EXPANDED_RAIL_WIDTH else COLLAPSED_RAIL_WIDTH,
        animationSpec = tween(durationMillis = RAIL_ANIM_DURATION_MS),
        label = "railWidth",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(start = COLLAPSED_RAIL_WIDTH)) {
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
            SidebarItem(
                icon = Icons.Default.Home,
                label = "Home",
                selected = isHomeSelected,
                expanded = expanded,
                onClick = onOpenHome,
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

// Background color alone carries focused/selected/idle state; text and icon
// stay plain white in every state. This used to two-tone the text too
// (NeonPurpleGlow when focused, NeonPurple when selected) matching buttons/
// cards elsewhere, but both combinations turned out low-contrast and hard to
// read against their own NeonPurple-family backgrounds (reported as an
// accessibility issue, confirmed on real hardware/photos — looks fine in a
// quick screenshot glance but reads as genuinely hard to read on an actual
// TV). White stays legible against every background state here.
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val itemFocused by interactionSource.collectIsFocusedAsState()

    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(RailItemHeight),
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
            // Focus already announces this item via the row's own selection/
            // focus semantics — a contentDescription on the icon too would
            // read the label twice.
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
