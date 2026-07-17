package com.moviesshumtimes.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.moviesshumtimes.tv.ui.theme.AppBackground
import com.moviesshumtimes.tv.ui.theme.AppOnBackground
import com.moviesshumtimes.tv.ui.theme.AppOnSurface
import com.moviesshumtimes.tv.ui.theme.AppOnSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppSurface
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.data.plex.PlexAccount
import com.moviesshumtimes.tv.data.plex.PlexAuthApi
import com.moviesshumtimes.tv.data.plex.PlexEpisode
import com.moviesshumtimes.tv.data.plex.PlexIdentity
import com.moviesshumtimes.tv.data.plex.PlexLibraryItem
import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.plex.PlexResourcesApi
import com.moviesshumtimes.tv.data.plex.PlexSeason
import com.moviesshumtimes.tv.data.plex.PlexSection
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.data.plex.PlexServerApi
import com.moviesshumtimes.tv.data.plex.TokenStore
import com.moviesshumtimes.tv.ui.auth.AuthScreen
import com.moviesshumtimes.tv.ui.library.LibraryScreen
import com.moviesshumtimes.tv.ui.library.MovieDetailScreen
import com.moviesshumtimes.tv.ui.library.ShowEpisodesScreen
import com.moviesshumtimes.tv.ui.library.ShowSeasonsScreen
import com.moviesshumtimes.tv.ui.lobby.LobbyScreen
import com.moviesshumtimes.tv.ui.player.PlayerScreen
import com.moviesshumtimes.tv.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private const val SECTION_TYPE_SHOW = "show"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge so the player's video surface can claim the entire
        // physical display — otherwise space reserved for system bars shows
        // up as extra black bars on top of whatever letterboxing is already
        // baked into the video itself.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    border = NeonPurple,
                    primary = NeonPurple,
                    background = AppBackground,
                    onBackground = AppOnBackground,
                    surface = AppSurface,
                    onSurface = AppOnSurface,
                    surfaceVariant = AppSurfaceVariant,
                    onSurfaceVariant = AppOnSurfaceVariant,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    // A plain Modifier.background() doesn't establish content
                    // color the way a tv-material3 Surface would — without
                    // this, every bare Text() outside a Card/Button (screen
                    // titles, field labels, etc.) falls back to Compose's
                    // hardcoded default (black), unreadable on this dark theme.
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        AppRoot()
                    }
                }
            }
        }
    }
}

// The four fields every library-browsing state needs — pulled out so each
// AppState variant below doesn't repeat them.
private data class LibraryContext(
    val server: PlexServer,
    val sections: List<PlexSection>,
    val selectedSection: PlexSection,
    val items: List<PlexLibraryItem>,
)

private sealed interface AppState {
    data object Checking : AppState
    data object LoggedOut : AppState
    data class ConnectingToServer(val username: String?) : AppState
    data class Error(val message: String) : AppState
    data class Library(val ctx: LibraryContext) : AppState
    data class Settings(val ctx: LibraryContext) : AppState
    data class MovieDetail(val ctx: LibraryContext, val movie: PlexLibraryItem) : AppState
    data class ShowSeasons(val ctx: LibraryContext, val show: PlexLibraryItem, val seasons: List<PlexSeason>) : AppState
    data class ShowEpisodes(
        val ctx: LibraryContext,
        val show: PlexLibraryItem,
        val seasons: List<PlexSeason>,
        val season: PlexSeason,
        val episodes: List<PlexEpisode>,
    ) : AppState
    // returnState lets Lobby/Player hand navigation back to wherever the
    // user actually came from — a movie's detail screen, or an episode list.
    data class Lobby(val server: PlexServer, val detail: PlexMovieDetail, val returnState: AppState) : AppState
    data class Player(val server: PlexServer, val detail: PlexMovieDetail, val returnState: AppState) : AppState
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<AppState>(AppState.Checking) }
    var clientIdentifier by remember { mutableStateOf("") }
    var localAccount by remember { mutableStateOf<PlexAccount?>(null) }

    suspend fun connect(token: String) {
        clientIdentifier = PlexIdentity.getOrCreateClientIdentifier(context)
        val authApi = PlexAuthApi(clientIdentifier)
        val account = runCatching { authApi.fetchAccount(token) }.getOrNull()
        localAccount = account
        state = AppState.ConnectingToServer(account?.username)

        state = runCatching {
            val server = PlexResourcesApi(clientIdentifier).findReachableServer(token)
                ?: error("No reachable Plex server found — is the cousin's server online?")
            val serverApi = PlexServerApi(server, clientIdentifier)
            val sections = serverApi.fetchSections()
            val firstSection = sections.firstOrNull()
                ?: error("No movie or show library found on ${server.name}")
            val items = serverApi.fetchLibraryItems(firstSection.key)
            AppState.Library(LibraryContext(server, sections, firstSection, items))
        }.getOrElse { AppState.Error(it.message ?: "Something went wrong connecting to Plex") }
    }

    LaunchedEffect(Unit) {
        val token = TokenStore.loadToken(context)
        if (token == null) {
            state = AppState.LoggedOut
        } else {
            connect(token)
        }
    }

    when (val current = state) {
        is AppState.Checking -> Text("Loading…")
        is AppState.ConnectingToServer -> Text(
            current.username?.let { "Logged in as $it — connecting to library…" } ?: "Connecting to library…",
        )
        is AppState.LoggedOut -> AuthScreen(onLoggedIn = { token -> scope.launch { connect(token) } })
        is AppState.Error -> Text("Error: ${current.message}")
        is AppState.Library -> LibraryScreen(
            server = current.ctx.server,
            sections = current.ctx.sections,
            selectedSection = current.ctx.selectedSection,
            items = current.ctx.items,
            onSelectSection = { section ->
                scope.launch {
                    val items = runCatching {
                        PlexServerApi(current.ctx.server, clientIdentifier).fetchLibraryItems(section.key)
                    }.getOrElse { emptyList() }
                    state = AppState.Library(current.ctx.copy(selectedSection = section, items = items))
                }
            },
            onSelectItem = { item -> state = AppState.MovieDetail(current.ctx, item) },
            onOpenSettings = { state = AppState.Settings(current.ctx) },
        )
        is AppState.Settings -> SettingsScreen(
            onBack = { state = AppState.Library(current.ctx) },
        )
        is AppState.MovieDetail -> MovieDetailScreen(
            server = current.ctx.server,
            movie = current.movie,
            isShow = current.ctx.selectedSection.type == SECTION_TYPE_SHOW,
            onBack = { state = AppState.Library(current.ctx) },
            onPlay = {
                scope.launch {
                    val serverApi = PlexServerApi(current.ctx.server, clientIdentifier)
                    if (current.ctx.selectedSection.type == SECTION_TYPE_SHOW) {
                        val fetched = runCatching { serverApi.fetchSeasons(current.movie.ratingKey) }
                        state = fetched.fold(
                            onSuccess = { seasons -> AppState.ShowSeasons(current.ctx, current.movie, seasons) },
                            onFailure = { AppState.Error(it.message ?: "Couldn't load seasons") },
                        )
                    } else {
                        val fetched = runCatching { serverApi.fetchMovieDetail(current.movie.ratingKey) }
                        state = fetched.fold(
                            onSuccess = { detail -> AppState.Lobby(current.ctx.server, detail, current) },
                            onFailure = { AppState.Error(it.message ?: "Couldn't load playback info") },
                        )
                    }
                }
            },
        )
        is AppState.ShowSeasons -> ShowSeasonsScreen(
            server = current.ctx.server,
            showTitle = current.show.title,
            seasons = current.seasons,
            onSelect = { season ->
                scope.launch {
                    val fetched = runCatching {
                        PlexServerApi(current.ctx.server, clientIdentifier).fetchEpisodes(season.ratingKey)
                    }
                    state = fetched.fold(
                        onSuccess = { episodes ->
                            AppState.ShowEpisodes(current.ctx, current.show, current.seasons, season, episodes)
                        },
                        onFailure = { AppState.Error(it.message ?: "Couldn't load episodes") },
                    )
                }
            },
            onBack = { state = AppState.MovieDetail(current.ctx, current.show) },
        )
        is AppState.ShowEpisodes -> ShowEpisodesScreen(
            server = current.ctx.server,
            showTitle = current.show.title,
            seasonTitle = current.season.title,
            episodes = current.episodes,
            onSelect = { episode ->
                scope.launch {
                    val fetched = runCatching {
                        PlexServerApi(current.ctx.server, clientIdentifier).fetchMovieDetail(episode.ratingKey)
                    }
                    state = fetched.fold(
                        onSuccess = { detail -> AppState.Lobby(current.ctx.server, detail, current) },
                        onFailure = { AppState.Error(it.message ?: "Couldn't load playback info") },
                    )
                }
            },
            onBack = { state = AppState.ShowSeasons(current.ctx, current.show, current.seasons) },
        )
        is AppState.Lobby -> key(current.detail.ratingKey) {
            LobbyScreen(
                detail = current.detail,
                localUsername = localAccount?.username ?: "You",
                localAvatarUrl = localAccount?.thumb,
                onStart = { state = AppState.Player(current.server, current.detail, current.returnState) },
                onBack = { state = current.returnState },
            )
        }
        is AppState.Player -> key(current.detail.ratingKey) {
            PlayerScreen(
                server = current.server,
                detail = current.detail,
                clientIdentifier = clientIdentifier,
                onExit = { state = current.returnState },
            )
        }
    }
}
