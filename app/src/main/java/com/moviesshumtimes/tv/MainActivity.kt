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
import com.moviesshumtimes.tv.data.plex.PlexOnDeckItem
import com.moviesshumtimes.tv.data.plex.PlexResourcesApi
import com.moviesshumtimes.tv.data.plex.PlexSeason
import com.moviesshumtimes.tv.data.plex.PlexSection
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.data.plex.PlexServerApi
import com.moviesshumtimes.tv.data.plex.TokenStore
import com.moviesshumtimes.tv.data.settings.RelayIdentity
import com.moviesshumtimes.tv.data.settings.RelayIdentityStore
import com.moviesshumtimes.tv.data.settings.SettingsStore
import com.moviesshumtimes.tv.sync.RelayClient
import com.moviesshumtimes.tv.ui.auth.AuthScreen
import com.moviesshumtimes.tv.ui.library.LibraryScreen
import com.moviesshumtimes.tv.ui.library.MovieDetailScreen
import com.moviesshumtimes.tv.ui.library.ShowEpisodesScreen
import com.moviesshumtimes.tv.ui.library.ShowSeasonsScreen
import com.moviesshumtimes.tv.ui.lobby.LobbyScreen
import com.moviesshumtimes.tv.ui.home.HomeScreen
import com.moviesshumtimes.tv.ui.navigation.AppNavigationDrawer
import com.moviesshumtimes.tv.ui.player.PlayerScreen
import com.moviesshumtimes.tv.ui.settings.RelaySetupScreen
import com.moviesshumtimes.tv.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first
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
    // Shown once after first login when no relay URL is saved yet — see
    // RelaySetupScreen. Never revisited once a relay URL exists.
    data class RelaySetup(val ctx: LibraryContext) : AppState
    // Landing screen after login — the only state not scoped to a single
    // library section, so it carries its own row data instead of a
    // LibraryContext. Always refetched on entry (see loadHome) since
    // watch progress / library contents change constantly.
    data class Home(
        val server: PlexServer,
        val sections: List<PlexSection>,
        val onDeck: List<PlexOnDeckItem>,
        val recentlyAdded: List<PlexLibraryItem>,
        val suggestions: List<PlexOnDeckItem>,
    ) : AppState
    data class Library(val ctx: LibraryContext) : AppState
    // returnState mirrors Lobby/Player's field below — Settings is reachable
    // from Home now too, and Back needs to land wherever it was opened from
    // rather than always dumping into the Movies library.
    data class Settings(val ctx: LibraryContext, val returnState: AppState) : AppState
    // returnState mirrors Settings/Lobby/Player below — MovieDetail is now
    // reachable from Home (Recently Added/Suggestions) as well as Library,
    // so Back needs to resolve to wherever it was actually opened from.
    // ShowSeasons/ShowEpisodes carry it through unchanged (their own Back
    // already steps back one level internally, to MovieDetail/ShowSeasons)
    // so a second Back-press after landing back on MovieDetail still
    // resolves correctly.
    data class MovieDetail(val ctx: LibraryContext, val movie: PlexLibraryItem, val returnState: AppState) : AppState
    data class ShowSeasons(
        val ctx: LibraryContext,
        val show: PlexLibraryItem,
        val seasons: List<PlexSeason>,
        val returnState: AppState,
    ) : AppState
    data class ShowEpisodes(
        val ctx: LibraryContext,
        val show: PlexLibraryItem,
        val seasons: List<PlexSeason>,
        val season: PlexSeason,
        val episodes: List<PlexEpisode>,
        val returnState: AppState,
    ) : AppState
    // returnState lets Lobby/Player hand navigation back to wherever the
    // user actually came from — a movie's detail screen, or an episode list.
    // relay is shared across the Lobby -> Player transition (and back) so
    // the connection — and any active chat — doesn't reconnect on every
    // screen change; see AppRoot's ensureRelayClient. Lobby.relay is
    // non-null by construction — see the onPlay/onSelect handlers below,
    // which only ever build a Lobby when a relay actually exists (there'd
    // be nothing to wait for otherwise). Player.relay stays nullable since
    // Player is also reachable directly, skipping the Lobby entirely, when
    // no relay is configured — solo playback has never depended on one.
    data class Lobby(
        val server: PlexServer,
        val detail: PlexMovieDetail,
        val returnState: AppState,
        val relay: RelayClient,
    ) : AppState
    data class Player(
        val server: PlexServer,
        val detail: PlexMovieDetail,
        val returnState: AppState,
        val relay: RelayClient?,
    ) : AppState
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<AppState>(AppState.Checking) }
    var clientIdentifier by remember { mutableStateOf("") }
    var localAccount by remember { mutableStateOf<PlexAccount?>(null) }
    var accountToken by remember { mutableStateOf<String?>(null) }

    var relayIdentity by remember { mutableStateOf<RelayIdentity?>(null) }
    var relayClient by remember { mutableStateOf<RelayClient?>(null) }

    // Lazily creates (or reuses) the shared watch-together connection when
    // entering the Lobby/Player flow, and released explicitly wherever that
    // flow is exited back to browsing (see onBack/onExit below) — reconnect
    // tokens make reconnecting cheap and safe now, so there's no reason to
    // hold the socket open for the whole browsing session too.
    suspend fun ensureRelayClient(): RelayClient? {
        relayClient?.let { return it }
        val relayUrl = SettingsStore.observe(context).first().relayUrl?.takeIf { it.isNotBlank() } ?: return null
        val identity = relayIdentity ?: RelayIdentityStore.load(context).also { relayIdentity = it }
        val newClient = RelayClient(
            relayUrl = relayUrl,
            identity = identity,
            scope = scope,
            onIdentityUpdated = { updated ->
                relayIdentity = updated
                scope.launch { updated.reconnectToken?.let { RelayIdentityStore.saveReconnectToken(context, it) } }
            },
        )
        newClient.connect()
        relayClient = newClient
        return newClient
    }

    fun releaseRelayClient() {
        relayClient?.disconnect()
        relayClient = null
    }

    // Shared by both the nav drawer (any browsing screen) and the old
    // in-Library tab row it replaced — always lands back on Library, since
    // that's the one screen that actually renders a section's items.
    fun selectSection(ctx: LibraryContext, section: PlexSection) {
        if (section.key == ctx.selectedSection.key) {
            // ctx.items is already this section's data — no need to hit the
            // network again just to jump back to Library from wherever the
            // nav drawer was clicked.
            state = AppState.Library(ctx)
            return
        }
        scope.launch {
            val items = runCatching {
                PlexServerApi(ctx.server, clientIdentifier).fetchLibraryItems(section.key)
            }.getOrElse { emptyList() }
            state = AppState.Library(ctx.copy(selectedSection = section, items = items))
        }
    }

    // Home has no "currently selected section" to compare against the way
    // selectSection does, so a click there always fetches fresh.
    fun openSection(server: PlexServer, sections: List<PlexSection>, section: PlexSection) {
        scope.launch {
            val items = runCatching {
                PlexServerApi(server, clientIdentifier).fetchLibraryItems(section.key)
            }.getOrElse { emptyList() }
            state = AppState.Library(LibraryContext(server, sections, section, items))
        }
    }

    suspend fun loadHome(server: PlexServer, sections: List<PlexSection>): AppState.Home {
        val api = PlexServerApi(server, clientIdentifier)
        val onDeck = runCatching { api.fetchOnDeck() }.getOrElse { emptyList() }
        val recentlyAdded = runCatching { api.fetchRecentlyAdded() }.getOrElse { emptyList() }
        val suggestions = runCatching { api.fetchSuggestions() }.getOrElse { emptyList() }
        return AppState.Home(server, sections, onDeck, recentlyAdded, suggestions)
    }

    // Optimistic + fire-and-forget, matching TimelineReporter's established
    // style — no error UI exists anywhere in this app. If the PUT actually
    // fails server-side, the item just reappears next time Home is
    // (re)entered, since loadHome always refetches.
    fun removeFromContinueWatching(home: AppState.Home, item: PlexOnDeckItem) {
        state = home.copy(onDeck = home.onDeck.filterNot { it.ratingKey == item.ratingKey })
        scope.launch {
            runCatching { PlexServerApi(home.server, clientIdentifier).removeFromContinueWatching(item.ratingKey) }
        }
    }

    suspend fun connect(token: String) {
        accountToken = token
        clientIdentifier = PlexIdentity.getOrCreateClientIdentifier(context)
        val authApi = PlexAuthApi(clientIdentifier)
        val account = runCatching { authApi.fetchAccount(token) }.getOrNull()
        localAccount = account
        state = AppState.ConnectingToServer(account?.username)

        state = runCatching {
            val selectedServerId = SettingsStore.observe(context).first().selectedServerId
            val server = PlexResourcesApi(clientIdentifier).findReachableServer(token, selectedServerId)
                ?: error("No reachable Plex server found — is the cousin's server online?")
            val serverApi = PlexServerApi(server, clientIdentifier)
            val sections = serverApi.fetchSections()
            val firstSection = sections.firstOrNull()
                ?: error("No movie or show library found on ${server.name}")
            val items = serverApi.fetchLibraryItems(firstSection.key)
            val ctx = LibraryContext(server, sections, firstSection, items)
            val relayConfigured = !SettingsStore.observe(context).first().relayUrl.isNullOrBlank()
            if (relayConfigured) loadHome(server, sections) else AppState.RelaySetup(ctx)
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
        is AppState.RelaySetup -> RelaySetupScreen(
            onDone = { scope.launch { state = loadHome(current.ctx.server, current.ctx.sections) } },
        )
        is AppState.Home -> AppNavigationDrawer(
            sections = current.sections,
            selectedSectionKey = null,
            isSettingsSelected = false,
            isHomeSelected = true,
            onSelectSection = { section -> openSection(current.server, current.sections, section) },
            onOpenSettings = {
                state = AppState.Settings(
                    LibraryContext(current.server, current.sections, current.sections.first(), emptyList()),
                    returnState = current,
                )
            },
            onOpenHome = {},
        ) {
            HomeScreen(
                server = current.server,
                onDeck = current.onDeck,
                recentlyAdded = current.recentlyAdded,
                suggestions = current.suggestions,
                onResume = { item ->
                    scope.launch {
                        val fetched = runCatching {
                            PlexServerApi(current.server, clientIdentifier).fetchMovieDetail(item.ratingKey)
                        }
                        state = fetched.fold(
                            onSuccess = { detail ->
                                val relay = ensureRelayClient()
                                if (relay != null) {
                                    AppState.Lobby(current.server, detail, current, relay)
                                } else {
                                    AppState.Player(current.server, detail, current, relay)
                                }
                            },
                            onFailure = { AppState.Error(it.message ?: "Couldn't load playback info") },
                        )
                    }
                },
                onRemove = { item -> removeFromContinueWatching(current, item) },
                onSelectRecentlyAdded = { item ->
                    // recentlyAdded surfaces TV content at season
                    // granularity ("Season 14") — there's no season-level
                    // detail screen in this app, so route to the show
                    // itself instead, using the parent fields Plex attaches
                    // to season items (confirmed against a real server).
                    val target = if (item.type == "season" && item.parentRatingKey != null) {
                        PlexLibraryItem(
                            ratingKey = item.parentRatingKey,
                            type = SECTION_TYPE_SHOW,
                            title = item.parentTitle ?: item.title,
                            thumb = item.thumb,
                            art = item.art,
                        )
                    } else {
                        item
                    }
                    val ctx = LibraryContext(
                        current.server,
                        current.sections,
                        current.sections.firstOrNull { it.type == target.type } ?: current.sections.first(),
                        emptyList(),
                    )
                    state = AppState.MovieDetail(ctx, target, returnState = current)
                },
                onSelectSuggestion = { item ->
                    val ctx = LibraryContext(
                        current.server,
                        current.sections,
                        current.sections.firstOrNull { it.type == item.type } ?: current.sections.first(),
                        emptyList(),
                    )
                    val movie = PlexLibraryItem(
                        ratingKey = item.ratingKey,
                        type = item.type,
                        title = item.title,
                        thumb = item.thumb,
                        art = item.art,
                    )
                    state = AppState.MovieDetail(ctx, movie, returnState = current)
                },
            )
        }
        is AppState.Library -> AppNavigationDrawer(
            sections = current.ctx.sections,
            selectedSectionKey = current.ctx.selectedSection.key,
            isSettingsSelected = false,
            isHomeSelected = false,
            onSelectSection = { section -> selectSection(current.ctx, section) },
            onOpenSettings = { state = AppState.Settings(current.ctx, returnState = AppState.Library(current.ctx)) },
            onOpenHome = { scope.launch { state = loadHome(current.ctx.server, current.ctx.sections) } },
        ) {
            LibraryScreen(
                server = current.ctx.server,
                selectedSection = current.ctx.selectedSection,
                items = current.ctx.items,
                onSelectItem = { item ->
                    state = AppState.MovieDetail(current.ctx, item, returnState = AppState.Library(current.ctx))
                },
            )
        }
        is AppState.Settings -> AppNavigationDrawer(
            sections = current.ctx.sections,
            selectedSectionKey = current.ctx.selectedSection.key,
            isSettingsSelected = true,
            isHomeSelected = false,
            onSelectSection = { section -> selectSection(current.ctx, section) },
            onOpenSettings = {},
            onOpenHome = { scope.launch { state = loadHome(current.ctx.server, current.ctx.sections) } },
        ) {
            SettingsScreen(
                accountToken = accountToken ?: "",
                clientIdentifier = clientIdentifier,
                onBack = { state = current.returnState },
                onSaved = {
                    val token = accountToken
                    if (token != null) scope.launch { connect(token) } else state = current.returnState
                },
            )
        }
        is AppState.MovieDetail -> AppNavigationDrawer(
            sections = current.ctx.sections,
            selectedSectionKey = current.ctx.selectedSection.key,
            isSettingsSelected = false,
            isHomeSelected = false,
            onSelectSection = { section -> selectSection(current.ctx, section) },
            onOpenSettings = { state = AppState.Settings(current.ctx, returnState = AppState.Library(current.ctx)) },
            onOpenHome = { scope.launch { state = loadHome(current.ctx.server, current.ctx.sections) } },
        ) {
            MovieDetailScreen(
                server = current.ctx.server,
                movie = current.movie,
                isShow = current.ctx.selectedSection.type == SECTION_TYPE_SHOW,
                onBack = { state = current.returnState },
                onPlay = {
                    scope.launch {
                        val serverApi = PlexServerApi(current.ctx.server, clientIdentifier)
                        if (current.ctx.selectedSection.type == SECTION_TYPE_SHOW) {
                            val fetched = runCatching { serverApi.fetchSeasons(current.movie.ratingKey) }
                            state = fetched.fold(
                                onSuccess = { seasons ->
                                    AppState.ShowSeasons(current.ctx, current.movie, seasons, current.returnState)
                                },
                                onFailure = { AppState.Error(it.message ?: "Couldn't load seasons") },
                            )
                        } else {
                            val fetched = runCatching { serverApi.fetchMovieDetail(current.movie.ratingKey) }
                            state = fetched.fold(
                                onSuccess = { detail ->
                                    val relay = ensureRelayClient()
                                    // No relay configured means no one to wait for —
                                    // skip the Lobby's waiting room and go straight
                                    // to solo playback.
                                    if (relay != null) {
                                        AppState.Lobby(current.ctx.server, detail, current, relay)
                                    } else {
                                        AppState.Player(current.ctx.server, detail, current, relay)
                                    }
                                },
                                onFailure = { AppState.Error(it.message ?: "Couldn't load playback info") },
                            )
                        }
                    }
                },
            )
        }
        is AppState.ShowSeasons -> AppNavigationDrawer(
            sections = current.ctx.sections,
            selectedSectionKey = current.ctx.selectedSection.key,
            isSettingsSelected = false,
            isHomeSelected = false,
            onSelectSection = { section -> selectSection(current.ctx, section) },
            onOpenSettings = { state = AppState.Settings(current.ctx, returnState = AppState.Library(current.ctx)) },
            onOpenHome = { scope.launch { state = loadHome(current.ctx.server, current.ctx.sections) } },
        ) {
            ShowSeasonsScreen(
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
                                AppState.ShowEpisodes(
                                    current.ctx,
                                    current.show,
                                    current.seasons,
                                    season,
                                    episodes,
                                    current.returnState,
                                )
                            },
                            onFailure = { AppState.Error(it.message ?: "Couldn't load episodes") },
                        )
                    }
                },
                onBack = {
                    state = AppState.MovieDetail(current.ctx, current.show, current.returnState)
                },
            )
        }
        is AppState.ShowEpisodes -> AppNavigationDrawer(
            sections = current.ctx.sections,
            selectedSectionKey = current.ctx.selectedSection.key,
            isSettingsSelected = false,
            isHomeSelected = false,
            onSelectSection = { section -> selectSection(current.ctx, section) },
            onOpenSettings = { state = AppState.Settings(current.ctx, returnState = AppState.Library(current.ctx)) },
            onOpenHome = { scope.launch { state = loadHome(current.ctx.server, current.ctx.sections) } },
        ) {
            ShowEpisodesScreen(
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
                            onSuccess = { detail ->
                                val relay = ensureRelayClient()
                                if (relay != null) {
                                    AppState.Lobby(current.ctx.server, detail, current, relay)
                                } else {
                                    AppState.Player(current.ctx.server, detail, current, relay)
                                }
                            },
                            onFailure = { AppState.Error(it.message ?: "Couldn't load playback info") },
                        )
                    }
                },
                onBack = {
                    state = AppState.ShowSeasons(current.ctx, current.show, current.seasons, current.returnState)
                },
            )
        }
        is AppState.Lobby -> key(current.detail.ratingKey) {
            LobbyScreen(
                detail = current.detail,
                localUsername = localAccount?.username ?: "You",
                localAvatarUrl = localAccount?.thumb,
                relay = current.relay,
                onStart = { state = AppState.Player(current.server, current.detail, current.returnState, current.relay) },
                onBack = {
                    releaseRelayClient()
                    state = current.returnState
                },
            )
        }
        is AppState.Player -> key(current.detail.ratingKey) {
            PlayerScreen(
                server = current.server,
                detail = current.detail,
                clientIdentifier = clientIdentifier,
                relay = current.relay,
                onExit = {
                    releaseRelayClient()
                    state = current.returnState
                },
            )
        }
    }
}
