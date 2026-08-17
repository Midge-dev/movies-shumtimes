package com.moviesshumtimes.tv.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.data.plex.PlexSection
import com.moviesshumtimes.tv.ui.theme.AppSurface
import com.moviesshumtimes.tv.ui.theme.NeonPurple

private const val SECTION_TYPE_SHOW = "show"

// NavigationDrawerItemDefaults.CollapsedDrawerItemWidth (56dp) plus this
// Column's own 12dp horizontal padding on each side — the actual rendered
// width of the collapsed rail, and therefore how much start-padding content
// needs to clear it. The library doesn't inset content itself (see
// ModalNavigationDrawer's own doc comment); this has to be supplied here.
private val COLLAPSED_RAIL_WIDTH = 80.dp

// Persistent side rail replacing the old top-of-screen section-tabs row and
// separate Settings button — a collapsed icon-only rail that expands to
// show labels when it (or a child) has focus, matching the pattern every
// major TV streaming app uses instead of top navigation. Wraps every
// "browsing" screen (Library, MovieDetail, ShowSeasons, ShowEpisodes,
// Settings) from MainActivity's AppRoot; Lobby/Player/Auth/RelaySetup stay
// outside it since they're either full-screen or one-time flows, not
// destinations you navigate between.
//
// Built on tv-material3's own ModalNavigationDrawer/NavigationDrawerItem
// rather than a hand-rolled Box+animateDpAsState version (which this used to
// be, for tighter control over the expand/collapse timing than the
// library's default). That custom version had a real, unresolved visual
// glitch — a "stretch" flash on screen entry — traced to two independently-
// timed animations racing each other (an outer Column-width tween plus a
// per-item label AnimatedVisibility fade, sharing no common driver). The
// library's own item implementation animates both from the same per-item
// hasFocus state in one coordinated place, and separately already guards
// against the exact "spurious initial focus" race that was triggering the
// flash in the first place (DrawerSheet's internal initializationComplete
// check). Trading the custom timing for a known-correct implementation.
// scrimBrush is fully transparent — a dimming scrim over the content behind
// an open drawer isn't how any of the streaming apps this rail is modeled
// on behave; the rail should just overlay without darkening anything else.
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
    ModalNavigationDrawer(
        scrimBrush = SolidColor(Color.Transparent),
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(AppSurface)
                    .padding(vertical = 24.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SidebarItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    selected = isHomeSelected,
                    onClick = onOpenHome,
                )
                for (section in sections) {
                    SidebarItem(
                        icon = if (section.type == SECTION_TYPE_SHOW) Icons.Default.Tv else Icons.Default.Movie,
                        label = section.title,
                        selected = !isSettingsSelected && !isHomeSelected && section.key == selectedSectionKey,
                        onClick = { onSelectSection(section) },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                SidebarItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    selected = isSettingsSelected,
                    onClick = onOpenSettings,
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(start = COLLAPSED_RAIL_WIDTH)) {
            content()
        }
    }
}

// Background color alone carries focused/selected/idle state; text and icon
// stay plain white in every state. This used to two-tone the text too
// (NeonPurpleGlow when focused, NeonPurple when selected) matching buttons/
// cards elsewhere, but both combinations turned out low-contrast and hard
// to read against their own NeonPurple-family backgrounds (reported as an
// accessibility issue, confirmed on real hardware/photos — looks fine in a
// quick screenshot glance but reads as genuinely hard to read on an actual
// TV). White stays legible against every background state here.
@Composable
private fun NavigationDrawerScope.SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        leadingContent = {
            Icon(imageVector = icon, contentDescription = if (hasFocus) null else label, tint = Color.White)
        },
        colors = NavigationDrawerItemDefaults.colors(
            contentColor = Color.White,
            inactiveContentColor = Color.White,
            focusedContainerColor = NeonPurple,
            focusedContentColor = Color.White,
            pressedContainerColor = NeonPurple,
            pressedContentColor = Color.White,
            selectedContainerColor = NeonPurple.copy(alpha = 0.35f),
            selectedContentColor = Color.White,
            focusedSelectedContainerColor = NeonPurple,
            focusedSelectedContentColor = Color.White,
            pressedSelectedContainerColor = NeonPurple,
            pressedSelectedContentColor = Color.White,
        ),
    ) {
        Text(label, color = Color.White)
    }
}
