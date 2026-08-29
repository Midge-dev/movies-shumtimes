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
import androidx.compose.runtime.collectAsState
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
import com.moviesshumtimes.tv.ui.kit.LocalContentColor
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppBackground
import com.moviesshumtimes.tv.ui.theme.AppOnBackground
import com.moviesshumtimes.tv.data.plex.PlexAccount
import com.moviesshumtimes.tv.data.plex.PlexAuthApi
import com.moviesshumtimes.tv.data.plex.PlexEpisode
import com.moviesshumtimes.tv.data.plex.PlexHub
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexLibraryItem
import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.plex.PlexOnDeckItem
import com.moviesshumtimes.tv.data.plex.PlexPerson
import com.moviesshumtimes.tv.data.plex.PlexResourcesApi
import com.moviesshumtimes.tv.data.plex.PlexSeason
import com.moviesshumtimes.tv.data.plex.PlexSection
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.data.plex.PlexServerApi
import com.moviesshumtimes.tv.data.settings.RelayEntry
import com.moviesshumtimes.tv.data.settings.RelayIdentity
import com.moviesshumtimes.tv.data.settings.appSettingsStore
import com.moviesshumtimes.tv.data.settings.defaultRelay
import com.moviesshumtimes.tv.data.settings.plexIdentityStore
import com.moviesshumtimes.tv.data.settings.relayIdentityStore
import com.moviesshumtimes.tv.data.settings.tokenStore
import com.moviesshumtimes.tv.sync.ConnectionState
import com.moviesshumtimes.tv.sync.RelayClient
import com.moviesshumtimes.tv.sync.RelayDirectoryApi
import com.moviesshumtimes.tv.sync.RelayRoomSummary
import com.moviesshumtimes.tv.sync.RoomIntent
import com.moviesshumtimes.tv.ui.auth.AuthScreen
import com.moviesshumtimes.tv.ui.common.LoadingScreen
import com.moviesshumtimes.tv.ui.library.LibraryScreen
import com.moviesshumtimes.tv.ui.library.MovieDetailScreen
import com.moviesshumtimes.tv.ui.library.PersonFilmographyScreen
import com.moviesshumtimes.tv.ui.library.ShowEpisodesScreen
import com.moviesshumtimes.tv.ui.library.ShowSeasonsScreen
import com.moviesshumtimes.tv.ui.lobby.LobbyScreen
import com.moviesshumtimes.tv.ui.home.HomeScreen
import com.moviesshumtimes.tv.ui.home.MergedRoom
import com.moviesshumtimes.tv.ui.navigation.AppNavigationDrawer
import com.moviesshumtimes.tv.ui.player.PlayerScreen
import com.moviesshumtimes.tv.ui.settings.RelaySetupScreen
import com.moviesshumtimes.tv.ui.settings.SettingsScreen
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBackground),
                contentAlignment = Alignment.Center,
            ) {
                // A plain Modifier.background() doesn't establish content
                // color on its own — without this, every bare Text() outside
                // a themed control (screen titles, field labels, etc.) falls
                // back to Compose's hardcoded default (black), unreadable on
                // this dark theme.
                CompositionLocalProvider(LocalContentColor provides AppOnBackground) {
                    AppRoot()
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
    // Shown while a section switch is in flight — fetchLibraryItems plus
    // preloadPosters (see below) can take a visible moment, and previously
    // nothing changed on screen until both finished, reading as an
    // unresponsive pause rather than a deliberate load. selectedSectionKey
    // is the section being loaded *into*, so the nav drawer highlights the
    // right destination while it waits rather than whatever was selected
    // before.
    data class LoadingSection(
        val server: PlexServer,
        val sections: List<PlexSection>,
        val label: String,
        val selectedSectionKey: String?,
    ) : AppState
    // returnState mirrors Lobby/Player's field below — Settings is reachable
    // from Home now too, and Back needs to land wherever it was opened from
    // rather than always dumping into the Movies library.
    data class Settings(val ctx: LibraryContext, val returnState: AppState, val relayHint: String? = null) : AppState
    // returnState mirrors Settings/Lobby/Player below — MovieDetail is now
    // reachable from Home (Recently Added/Suggestions) as well as Library,
    // so Back needs to resolve to wherever it was actually opened from.
    // ShowSeasons/ShowEpisodes carry it through unchanged (their own Back
    // already steps back one level internally, to MovieDetail/ShowSeasons)
    // so a second Back-press after landing back on MovieDetail still
    // resolves correctly.
    data class MovieDetail(val ctx: LibraryContext, val movie: PlexLibraryItem, val returnState: AppState) : AppState
    // Reached by pressing a cast/crew member on MovieDetail (design spec
    // 09c) — a dedicated lightweight grid rather than reusing LibraryScreen,
    // whose sort/genre/decade/search UI and nav-drawer section highlighting
    // don't fit a person-filtered view. items is fetched once, up front,
    // same as ShowSeasons/ShowEpisodes below.
    data class PersonFilmography(
        val ctx: LibraryContext,
        val person: PlexPerson,
        val items: List<PlexLibraryItem>,
        val returnState: AppState,
    ) : AppState
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
        // The room's host display name — this device's own username when
        // hosting, or the joined room's RelayRoomSummary.hostName when
        // joining someone else's (a guest otherwise has no way to know who
        // opened the room). See LobbyScreen's matching doc comment.
        val hostName: String,
        // Which relay this room is on — design spec 09d shows this next to
        // the title in Lobby's header alongside the connection status dot.
        val relayNickname: String,
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
    // hold the socket open for the whole browsing session too. The relay is
    // now multi-tenant (design spec 09b): every call states whether it's
    // hosting a fresh room or joining a specific one by id, since there's
    // no longer a single implicit room the connection always lands in.
    // relayUrl is now caller-supplied rather than derived from a single
    // setting — hosting uses settings.defaultRelay, joining uses whichever
    // relay the target room actually lives on (design spec 09d: multiple
    // relays, a room's relay isn't necessarily the default).
    suspend fun ensureRelayClient(relayUrl: String, intent: RoomIntent): RelayClient? {
        relayClient?.let { return it }
        if (relayUrl.isBlank()) return null
        val identity = relayIdentity ?: context.relayIdentityStore.load().also { relayIdentity = it }
        val newClient = RelayClient(
            relayUrl = relayUrl,
            identity = identity,
            scope = scope,
            onIdentityUpdated = { updated ->
                relayIdentity = updated
                updated.reconnectToken?.let { context.relayIdentityStore.saveReconnectToken(it) }
            },
            onHostedRoomIdUpdated = { hostedId -> context.relayIdentityStore.saveHostedRoomId(hostedId) },
        )
        newClient.connect(intent)
        relayClient = newClient
        return newClient
    }

    fun releaseRelayClient() {
        relayClient?.disconnect()
        relayClient = null
    }

    // Home's Watch Together row (design spec 09b, extended by 09d for
    // multiple relays) — polled rather than pushed over a live socket,
    // since browsing Home shouldn't require holding a WebSocket open just
    // to see what's live. Runs for the whole app lifetime as one loop
    // (cheap — a no-op check most of the time) rather than restarting on
    // every Home-state rebuild, matching the always-on polling idiom
    // already used elsewhere in this file.
    //
    // Every configured relay is queried in parallel and the merged map is
    // updated as each one resolves — not awaited all together — so one
    // slow/sleeping relay never delays another relay's rooms from
    // appearing (design spec 09d: "a slow or sleeping relay never holds up
    // the row").
    var liveRoomsByRelay by remember { mutableStateOf<Map<String, List<RelayRoomSummary>>>(emptyMap()) }
    var liveRelaysById by remember { mutableStateOf<Map<String, RelayEntry>>(emptyMap()) }
    // The room this device most recently hosted (seat 0), independent of
    // whether a live RelayClient still exists for it — see RelayIdentity's
    // hostedRoomId. Drives Home's "End session" control, which has to work
    // even after Lobby/Player's onBack/onExit has already torn the socket
    // down.
    var hostedRoomId by remember { mutableStateOf<String?>(null) }
    val relayDirectoryApi = remember { RelayDirectoryApi() }
    LaunchedEffect(Unit) {
        while (true) {
            if (state is AppState.Home) {
                // Two entries can point at the same physical relay (e.g. the
                // same relay re-added under a new nickname before the old
                // entry was removed) — polling both would show the exact
                // same room twice under two different names. Collapse to
                // one entry per URL, preferring whichever is default, so
                // each live room is attributed to exactly one nickname.
                val relays = context.appSettingsStore.observe().first().relays
                    .groupBy { it.url }
                    .map { (_, entries) -> entries.firstOrNull { it.isDefault } ?: entries.first() }
                liveRelaysById = relays.associateBy { it.id }
                val stillConfigured = relays.map { it.id }.toSet()
                liveRoomsByRelay = liveRoomsByRelay.filterKeys { it in stillConfigured }
                hostedRoomId = context.relayIdentityStore.load().hostedRoomId
                for (entry in relays) {
                    launch {
                        val rooms = relayDirectoryApi.listRooms(entry.url)
                        liveRoomsByRelay = liveRoomsByRelay + (entry.id to rooms)
                    }
                }
            } else {
                liveRoomsByRelay = emptyMap()
            }
            delay(5_000)
        }
    }
    val liveRooms = remember(liveRoomsByRelay, liveRelaysById) {
        liveRoomsByRelay.flatMap { (relayId, rooms) ->
            val relay = liveRelaysById[relayId] ?: return@flatMap emptyList()
            rooms.map { MergedRoom(relay, it) }
        }
    }

    // Design spec ask: an accidental disconnect (lost power/wifi) should
    // never kill a room out from under a guest — that's what the relay's
    // own reconnect grace already protects. This is the deliberate
    // counterpart: a host explicitly asking, from Home, to end a room right
    // now. Works with or without a live RelayClient for it (see
    // RelayDirectoryApi.closeRoom's server-side host-identity check).
    fun closeHostedRoom(merged: MergedRoom) {
        scope.launch {
            val identity = context.relayIdentityStore.load()
            val token = identity.reconnectToken ?: return@launch
            val ok = relayDirectoryApi.closeRoom(merged.relay.url, merged.room.roomId, identity.peerId, token)
            if (ok) {
                context.relayIdentityStore.saveHostedRoomId(null)
                hostedRoomId = null
                liveRoomsByRelay = liveRoomsByRelay.mapValues { (_, rooms) ->
                    rooms.filterNot { it.roomId == merged.room.roomId }
                }
            }
        }
    }

    // Warms Coil's cache for a section's first screenful of posters before
    // handing control back to the caller, so LibraryScreen's grid appears
    // already populated instead of popping in tile-by-tile while the loading
    // screen is still the thing on-screen. Bounded to a fixed prefix — most
    // libraries run into the hundreds of items, and only the first few rows
    // are visible before any scrolling happens (5-column grid; four rows
    // covers every screen size this app targets). A failed/slow fetch for
    // any one poster just means that tile pops in normally later — this is
    // a head start, not something the transition should ever block on
    // indefinitely.
    val posterPreloadImageLoader = SingletonImageLoader.get(context)
    suspend fun preloadPosters(server: PlexServer, items: List<PlexLibraryItem>) {
        coroutineScope {
            items.take(20)
                .mapNotNull { PlexImageUrl.of(server, it.thumb) }
                .map { url -> async { runCatching { posterPreloadImageLoader.execute(ImageRequest.Builder(context).data(url).build()) } } }
                .awaitAll()
        }
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
        state = AppState.LoadingSection(ctx.server, ctx.sections, section.title, section.key)
        scope.launch {
            val items = runCatching {
                PlexServerApi(ctx.server, clientIdentifier).fetchLibraryItems(section.key)
            }.getOrElse { emptyList() }
            preloadPosters(ctx.server, items)
            state = AppState.Library(ctx.copy(selectedSection = section, items = items))
        }
    }

    // Home has no "currently selected section" to compare against the way
    // selectSection does, so a click there always fetches fresh.
    fun openSection(server: PlexServer, sections: List<PlexSection>, section: PlexSection) {
        state = AppState.LoadingSection(server, sections, section.title, section.key)
        scope.launch {
            val items = runCatching {
                PlexServerApi(server, clientIdentifier).fetchLibraryItems(section.key)
            }.getOrElse { emptyList() }
            preloadPosters(server, items)
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

    // Every `state = current.returnState` restore below redisplays whatever
    // Home/Library snapshot was fetched *before* the detour (Settings,
    // MovieDetail, Lobby, Player…) started — potentially a while ago, and
    // long enough for the cousin to have added something to the library
    // mid-movie. Firing once here, right as that screen is restored, picks
    // up anything new without polling continuously. A no-op for every other
    // AppState (MovieDetail, ShowSeasons, …) — those aren't "the library
    // listing" in the sense meant here, so passing one through unchanged is
    // correct, not just harmless.
    //
    // Applied optimistically (old snapshot shown immediately, replaced once
    // the fetch resolves) rather than blocking the transition on a network
    // call. The `state == target` check guards against a slow refresh
    // landing after the user has already navigated on elsewhere by the time
    // it resolves.
    suspend fun refreshReturnState(target: AppState): AppState = when (target) {
        is AppState.Home -> loadHome(target.server, target.sections)
        is AppState.Library -> {
            val items = runCatching {
                PlexServerApi(target.ctx.server, clientIdentifier).fetchLibraryItems(target.ctx.selectedSection.key)
            }.getOrElse { target.ctx.items }
            target.copy(ctx = target.ctx.copy(items = items))
        }
        else -> target
    }

    fun returnTo(target: AppState) {
        state = target
        scope.launch {
            val refreshed = refreshReturnState(target)
            if (state == target) state = refreshed
        }
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
        clientIdentifier = context.plexIdentityStore.getOrCreateClientIdentifier()
        val authApi = PlexAuthApi(clientIdentifier)
        val account = runCatching { authApi.fetchAccount(token) }.getOrNull()
        localAccount = account
        state = AppState.ConnectingToServer(account?.username)

        state = runCatching {
            val selectedServerId = context.appSettingsStore.observe().first().selectedServerId
            val server = PlexResourcesApi(clientIdentifier).findReachableServer(token, selectedServerId)
                ?: error("No reachable Plex server found — make sure it's online and reachable on this network.")
            val serverApi = PlexServerApi(server, clientIdentifier)
            val sections = serverApi.fetchSections()
            val firstSection = sections.firstOrNull()
                ?: error("No movie or show library found on ${server.name}")
            val items = serverApi.fetchLibraryItems(firstSection.key)
            val ctx = LibraryContext(server, sections, firstSection, items)
            val relayConfigured = context.appSettingsStore.observe().first().relays.isNotEmpty()
            if (relayConfigured) loadHome(server, sections) else AppState.RelaySetup(ctx)
        }.getOrElse { AppState.Error(it.message ?: "Something went wrong connecting to Plex") }
    }

    LaunchedEffect(Unit) {
        val token = context.tokenStore.loadToken()
        if (token == null) {
            state = AppState.LoggedOut
        } else {
            connect(token)
        }
    }

    when (val current = state) {
        is AppState.Checking -> LoadingScreen("Loading…")
        is AppState.ConnectingToServer -> LoadingScreen(
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
                liveRooms = liveRooms,
                myRoomId = relayClient?.roomId?.collectAsState()?.value,
                hostedRoomId = hostedRoomId,
                onEndSession = { merged -> closeHostedRoom(merged) },
                onSelectRoom = { merged ->
                    scope.launch {
                        val ratingKey = merged.room.ratingKey
                        val fetched = if (ratingKey == null) {
                            Result.failure(IllegalStateException("Room has no movie reference"))
                        } else {
                            runCatching { PlexServerApi(current.server, clientIdentifier).fetchMovieDetail(ratingKey) }
                        }
                        val detail = fetched.getOrNull()
                        if (detail == null) {
                            state = AppState.Error(fetched.exceptionOrNull()?.message ?: "Couldn't load that room's movie")
                            return@launch
                        }
                        val relay = ensureRelayClient(merged.relay.url, RoomIntent.Join(merged.room.roomId))
                        if (relay == null) {
                            state = AppState.Error("No relay configured")
                            return@launch
                        }
                        // Wait briefly for the join to actually resolve so a
                        // room that just ended (host left between the last
                        // /rooms poll and this press) shows a clear message
                        // instead of dropping the viewer into a dead Lobby —
                        // times out optimistically into Lobby rather than
                        // blocking indefinitely on a slow relay.
                        val resolved = withTimeoutOrNull(5_000) {
                            relay.connectionState.first {
                                it == ConnectionState.CONNECTED || it == ConnectionState.ROOM_NOT_FOUND
                            }
                        }
                        state = if (resolved == ConnectionState.ROOM_NOT_FOUND) {
                            AppState.Error("That room just ended.")
                        } else {
                            AppState.Lobby(current.server, detail, current, relay, merged.room.hostName, merged.relay.nickname)
                        }
                    }
                },
                // Solo, always — resuming Continue Watching is never an
                // implicit "start a room" action (it isn't Watch Together).
                // Previously this unconditionally called ensureRelayClient
                // whenever a relay was configured at all, which meant every
                // resume created a real, publicly-listed room announcing
                // "hosting" to the whole friend group's Home screen.
                onResume = { item ->
                    scope.launch {
                        val fetched = runCatching {
                            PlexServerApi(current.server, clientIdentifier).fetchMovieDetail(item.ratingKey)
                        }
                        state = fetched.fold(
                            onSuccess = { detail -> AppState.Player(current.server, detail, current, relay = null) },
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
                    val parentRatingKey = item.parentRatingKey
                    val target = if (item.type == "season" && parentRatingKey != null) {
                        PlexLibraryItem(
                            ratingKey = parentRatingKey,
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
        is AppState.LoadingSection -> AppNavigationDrawer(
            sections = current.sections,
            selectedSectionKey = current.selectedSectionKey,
            isSettingsSelected = false,
            isHomeSelected = false,
            onSelectSection = {},
            onOpenSettings = {},
            onOpenHome = {},
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingScreen("Loading ${current.label}…")
            }
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
                hint = current.relayHint,
                onBack = { returnTo(current.returnState) },
                onSaved = {
                    val token = accountToken
                    if (token != null) scope.launch { connect(token) } else returnTo(current.returnState)
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
            val isShow = current.ctx.selectedSection.type == SECTION_TYPE_SHOW
            MovieDetailScreen(
                server = current.ctx.server,
                movie = current.movie,
                isShow = isShow,
                onBack = { returnTo(current.returnState) },
                resolveNextEpisode = {
                    runCatching {
                        PlexServerApi(current.ctx.server, clientIdentifier).fetchNextEpisodeForShow(current.movie.ratingKey)
                    }.getOrNull()
                },
                onSeasons = {
                    scope.launch {
                        val fetched = runCatching {
                            PlexServerApi(current.ctx.server, clientIdentifier).fetchSeasons(current.movie.ratingKey)
                        }
                        state = fetched.fold(
                            onSuccess = { seasons ->
                                AppState.ShowSeasons(current.ctx, current.movie, seasons, current.returnState)
                            },
                            onFailure = { AppState.Error(it.message ?: "Couldn't load seasons") },
                        )
                    }
                },
                // Detail-screen info sections (design spec 09c) — cast/crew/
                // ratings/reviews/related all come off the same
                // fetchMovieDetail/fetchRelatedHubs calls used elsewhere for
                // playback info, just requested here for their display
                // fields instead. Best-effort: a failure here should leave
                // the hero usable rather than erroring the whole screen.
                loadDetail = {
                    runCatching {
                        PlexServerApi(current.ctx.server, clientIdentifier).fetchMovieDetail(current.movie.ratingKey)
                    }.getOrNull()
                },
                loadRelatedHubs = {
                    runCatching {
                        PlexServerApi(current.ctx.server, clientIdentifier).fetchRelatedHubs(current.movie.ratingKey)
                    }.getOrDefault(emptyList())
                },
                loadByActor = { actorId ->
                    runCatching {
                        PlexServerApi(current.ctx.server, clientIdentifier)
                            .fetchLibraryItemsByActor(current.ctx.selectedSection.key, actorId)
                    }.getOrDefault(emptyList())
                },
                // A related/more-with poster only carries the fields Plex's
                // hub response gives it (ratingKey/type/title/thumb/art) —
                // same stub-construction shape as Home's onSelectSuggestion
                // above, staying within the current section's context since
                // a movie's related hubs are themselves other movies.
                onSelectRelated = { item ->
                    val relatedMovie = PlexLibraryItem(
                        ratingKey = item.ratingKey,
                        type = item.type,
                        title = item.title,
                        thumb = item.thumb,
                        art = item.art,
                    )
                    state = AppState.MovieDetail(current.ctx, relatedMovie, returnState = current)
                },
                onSelectPerson = { person ->
                    scope.launch {
                        val actorId = person.id
                        val fetched = if (actorId == null) {
                            Result.success(emptyList())
                        } else {
                            runCatching {
                                PlexServerApi(current.ctx.server, clientIdentifier)
                                    .fetchLibraryItemsByActor(current.ctx.selectedSection.key, actorId)
                            }
                        }
                        state = fetched.fold(
                            onSuccess = { items ->
                                AppState.PersonFilmography(current.ctx, person, items, current)
                            },
                            onFailure = { AppState.Error(it.message ?: "Couldn't load filmography") },
                        )
                    }
                },
                // Play — direct to player, relay untouched. No room, no
                // presence, no sync; this is the solo path and must never
                // require a configured relay.
                onPlay = { targetRatingKey ->
                    scope.launch {
                        val fetched = runCatching {
                            PlexServerApi(current.ctx.server, clientIdentifier).fetchMovieDetail(targetRatingKey)
                        }
                        state = fetched.fold(
                            onSuccess = { detail -> AppState.Player(current.ctx.server, detail, current, relay = null) },
                            onFailure = { AppState.Error(it.message ?: "Couldn't load playback info") },
                        )
                    }
                },
                // Watch Together — opens the Lobby. Stays focusable with no
                // relay configured (never disabled — a disabled control
                // gives a couch user nothing to act on), routing to Settings
                // with an inline hint instead.
                // Design spec 09d: "Always the default. No prompt." — a
                // long-press relay chooser (the documented escape hatch for
                // hosting on a non-default relay) isn't built yet; hosting
                // always targets settings.defaultRelay for now.
                onWatchTogether = { targetRatingKey ->
                    scope.launch {
                        val hostName = localAccount?.username ?: "Host"
                        val defaultRelay = context.appSettingsStore.observe().first().defaultRelay
                        if (defaultRelay == null) {
                            state = AppState.Settings(
                                current.ctx,
                                returnState = current,
                                relayHint = "Add a relay to watch with friends.",
                            )
                            return@launch
                        }
                        val relay = ensureRelayClient(
                            defaultRelay.url,
                            RoomIntent.Create(
                                title = current.movie.title,
                                thumb = current.movie.thumb,
                                ratingKey = targetRatingKey,
                                hostName = hostName,
                            ),
                        )
                        if (relay == null) {
                            state = AppState.Settings(
                                current.ctx,
                                returnState = current,
                                relayHint = "Add a relay to watch with friends.",
                            )
                        } else {
                            val fetched = runCatching {
                                PlexServerApi(current.ctx.server, clientIdentifier).fetchMovieDetail(targetRatingKey)
                            }
                            state = fetched.fold(
                                onSuccess = { detail ->
                                    AppState.Lobby(current.ctx.server, detail, current, relay, hostName, defaultRelay.nickname)
                                },
                                onFailure = { AppState.Error(it.message ?: "Couldn't load playback info") },
                            )
                        }
                    }
                },
            )
        }
        is AppState.PersonFilmography -> AppNavigationDrawer(
            sections = current.ctx.sections,
            selectedSectionKey = current.ctx.selectedSection.key,
            isSettingsSelected = false,
            isHomeSelected = false,
            onSelectSection = { section -> selectSection(current.ctx, section) },
            onOpenSettings = { state = AppState.Settings(current.ctx, returnState = AppState.Library(current.ctx)) },
            onOpenHome = { scope.launch { state = loadHome(current.ctx.server, current.ctx.sections) } },
        ) {
            PersonFilmographyScreen(
                server = current.ctx.server,
                personName = current.person.tag,
                items = current.items,
                onSelectItem = { item ->
                    state = AppState.MovieDetail(current.ctx, item, returnState = current)
                },
                onBack = { returnTo(current.returnState) },
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
                // Solo, always — see onResume's matching comment above.
                // Picking an episode from the list is playback, not Watch
                // Together.
                onSelect = { episode ->
                    scope.launch {
                        val fetched = runCatching {
                            PlexServerApi(current.ctx.server, clientIdentifier).fetchMovieDetail(episode.ratingKey)
                        }
                        state = fetched.fold(
                            onSuccess = { detail -> AppState.Player(current.ctx.server, detail, current, relay = null) },
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
                hostName = current.hostName,
                relayNickname = current.relayNickname,
                relay = current.relay,
                onStart = { state = AppState.Player(current.server, current.detail, current.returnState, current.relay) },
                onBack = {
                    releaseRelayClient()
                    returnTo(current.returnState)
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
                    returnTo(current.returnState)
                },
            )
        }
    }
}
