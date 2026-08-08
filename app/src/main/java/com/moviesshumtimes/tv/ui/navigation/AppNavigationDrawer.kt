package com.moviesshumtimes.tv.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.moviesshumtimes.tv.data.plex.PlexSection
import com.moviesshumtimes.tv.ui.theme.AppSurface
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow

private const val SECTION_TYPE_SHOW = "show"

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
    onSelectSection: (PlexSection) -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // Same two-tone treatment as buttons/cards elsewhere: the focused fill
    // is the deeper NeonPurple with the glow tone on top for the icon and
    // label (content color propagates to the leading Icon automatically),
    // and a persistent, subtler purple tint marks whichever section is
    // currently selected even when it doesn't have focus.
    val itemColors = NavigationDrawerItemDefaults.colors(
        focusedContainerColor = NeonPurple,
        focusedContentColor = NeonPurpleGlow,
        selectedContainerColor = NeonPurple.copy(alpha = 0.35f),
        selectedContentColor = NeonPurple,
        focusedSelectedContainerColor = NeonPurple,
        focusedSelectedContentColor = NeonPurpleGlow,
    )

    NavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(AppSurface)
                    .padding(vertical = 24.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (section in sections) {
                    NavigationDrawerItem(
                        selected = !isSettingsSelected && section.key == selectedSectionKey,
                        onClick = { onSelectSection(section) },
                        colors = itemColors,
                        leadingContent = {
                            Icon(
                                imageVector = if (section.type == SECTION_TYPE_SHOW) Icons.Default.Tv else Icons.Default.Movie,
                                contentDescription = null,
                            )
                        },
                    ) {
                        Text(section.title)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                NavigationDrawerItem(
                    selected = isSettingsSelected,
                    onClick = onOpenSettings,
                    colors = itemColors,
                    leadingContent = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                ) {
                    Text("Settings")
                }
            }
        },
    ) {
        content()
    }
}
