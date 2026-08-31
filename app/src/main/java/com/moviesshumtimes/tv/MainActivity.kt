package com.moviesshumtimes.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import com.moviesshumtimes.tv.ui.library.EpisodeDetailScreen
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
import com.moviesshumtimes.tv.ui.splash.SplashScreen
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.Job
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
                CompositionLocalProvider(LocalContentColor provides AppOnBackground) {
                    AppRoot()
                }
            }
        }
    }
}

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
    data class RelaySetup(val ctx: LibraryContext) : AppState
    data class Home(
        val server: PlexServer,
        val sections: List<PlexSection>,
        val onDeck: List<PlexOnDeckItem>,
        val recentlyAdded: List<PlexLibraryItem>,
        val suggestions: List<PlexOnDeckItem>,
    ) : AppState
    data class Library(val ctx: LibraryContext) : AppState
    data class LoadingSection(
        val server: PlexServer,
        val sections: List<PlexSection>,
        val label: String,
        val selectedSectionKey: String?,
    ) : AppState
    data class Settings(val ctx: LibraryContext, val returnState: AppState, val relayHint: String? = null) : AppState
    data class MovieDetail(val ctx: LibraryContext, val movie: PlexLibraryItem, val returnState: AppState) : AppState
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
    data class EpisodeDetail(
        val ctx: LibraryContext,
        val show: PlexLibraryItem,
        val episode: PlexEpisode,
        val returnState: AppState,
    ) : AppState
    data class Lobby(
        val server: PlexServer,
        val detail: PlexMovieDetail,
        val returnState: AppState,
        val relay: RelayClient,
        val hostName: String,
        val relayNickname: String,
        val thumb: String? = null,
        val isHost: Boolean = false,
    ) : AppState
    data class Player(
        val server: PlexServer,
        val detail: PlexMovieDetail,
        val returnState: AppState,
        val relay: RelayClient?,
    ) : AppState
}

private const val SPLASH_MIN_HOLD_MS = 1_400L

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
            onHostedRoomIdUpdated = { hostedId, token ->
                context.relayIdentityStore.addHostedRoom(relayUrl, hostedId, token)
            },
        )
        newClient.connect(intent)
        relayClient = newClient
        return newClient
    }

    fun releaseRelayClient() {
        relayClient?.disconnect()
        relayClient = null
    }

    var liveRoomsByRelay by remember { mutableStateOf<Map<String, List<RelayRoomSummary>>>(emptyMap()) }
    var liveRelaysById by remember { mutableStateOf<Map<String, RelayEntry>>(emptyMap()) }
    var hostedRoomIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val relayDirectoryApi = remember { RelayDirectoryApi() }
    LaunchedEffect(Unit) {
        val pollJobs = mutableMapOf<String, Job>()
        while (true) {
            if (state is AppState.Home) {
                val relays = context.appSettingsStore.observe().first().relays
                    .groupBy { it.url }
                    .map { (_, entries) -> entries.firstOrNull { it.isDefault } ?: entries.first() }
                liveRelaysById = relays.associateBy { it.id }
                val stillConfigured = relays.map { it.id }.toSet()
                liveRoomsByRelay = liveRoomsByRelay.filterKeys { it in stillConfigured }
                hostedRoomIds = context.relayIdentityStore.load().hostedRooms.map { it.roomId }.toSet()
                pollJobs.keys.retainAll(stillConfigured)
                for (entry in relays) {
                    if (pollJobs[entry.id]?.isActive == true) continue
                    pollJobs[entry.id] = launch {
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

    suspend fun closeHostedRoom(merged: MergedRoom): Boolean {
        val identity = context.relayIdentityStore.load()
        val hosted = identity.hostedRooms.firstOrNull { it.roomId == merged.room.roomId } ?: return false
        val ok = relayDirectoryApi.closeRoom(merged.relay.url, merged.room.roomId, identity.peerId, hosted.reconnectToken)
        if (ok) {
            context.relayIdentityStore.removeHostedRoom(merged.room.roomId)
            hostedRoomIds = hostedRoomIds - merged.room.roomId
            liveRoomsByRelay = liveRoomsByRelay.mapValues { (_, rooms) ->
                rooms.filterNot { it.roomId == merged.room.roomId }
            }
        }
        return ok
    }

    suspend fun hostOnAnotherRelay(current: AppState.Lobby) {
        val settings = context.appSettingsStore.observe().first()
        val next = settings.relays.firstOrNull { it.url != current.relay.relayUrl } ?: return
        releaseRelayClient()
        val hostName = localAccount?.username ?: "Host"
        val newClient = ensureRelayClient(
            next.url,
            RoomIntent.Create(
                title = current.detail.title,
                thumb = current.thumb,
                ratingKey = current.detail.ratingKey,
                hostName = hostName,
                maxSeats = settings.maxHostSeats,
            ),
        )
        if (newClient != null) {
            state = AppState.Lobby(
                current.server,
                current.detail,
                current.returnState,
                newClient,
                hostName,
                next.nickname,
                thumb = current.thumb,
                isHost = true,
            )
        }
    }

    val posterPreloadImageLoader = SingletonImageLoader.get(context)
    suspend fun preloadPosters(server: PlexServer, items: List<PlexLibraryItem>) {
        coroutineScope {
            items.take(20)
                .mapNotNull { PlexImageUrl.of(server, it.thumb) }
                .map { url -> async { runCatching { posterPreloadImageLoader.execute(ImageRequest.Builder(context).data(url).build()) } } }
                .awaitAll()
        }
    }

    fun selectSection(ctx: LibraryContext, section: PlexSection) {
        if (section.key == ctx.selectedSection.key) {
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

    var showSplash by remember { mutableStateOf(true) }
    val splashStartMs = remember { System.currentTimeMillis() }
    LaunchedEffect(state) {
        if (showSplash && state !is AppState.Checking && state !is AppState.ConnectingToServer) {
            val elapsed = System.currentTimeMillis() - splashStartMs
            if (elapsed < SPLASH_MIN_HOLD_MS) delay(SPLASH_MIN_HOLD_MS - elapsed)
            showSplash = false
        }
    }

    Crossfade(targetState = showSplash, animationSpec = tween(200), label = "splashCrossfade") { splashing ->
        if (splashing) {
            SplashScreen()
            return@Crossfade
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
            account = localAccount,
        ) {
            HomeScreen(
                server = current.server,
                onDeck = current.onDeck,
                recentlyAdded = current.recentlyAdded,
                suggestions = current.suggestions,
                liveRooms = liveRooms,
                myRoomId = relayClient?.roomId?.collectAsState()?.value,
                hostedRoomIds = hostedRoomIds,
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
            account = localAccount,
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
            account = localAccount,
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
            account = localAccount,
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
            account = localAccount,
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
                onWatchTogether = { targetRatingKey ->
                    scope.launch {
                        val hostName = localAccount?.username ?: "Host"
                        val settings = context.appSettingsStore.observe().first()
                        val defaultRelay = settings.defaultRelay
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
                                maxSeats = settings.maxHostSeats,
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
                                    AppState.Lobby(
                                        current.ctx.server,
                                        detail,
                                        current,
                                        relay,
                                        hostName,
                                        defaultRelay.nickname,
                                        thumb = current.movie.thumb,
                                        isHost = true,
                                    )
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
            account = localAccount,
        ) {
            PersonFilmographyScreen(
                server = current.ctx.server,
                personName = current.person.tag,
                personThumb = current.person.thumb,
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
            account = localAccount,
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
            account = localAccount,
        ) {
            ShowEpisodesScreen(
                server = current.ctx.server,
                showTitle = current.show.title,
                seasonTitle = current.season.title,
                episodes = current.episodes,
                onSelect = { episode ->
                    state = AppState.EpisodeDetail(current.ctx, current.show, episode, current)
                },
                onBack = {
                    state = AppState.ShowSeasons(current.ctx, current.show, current.seasons, current.returnState)
                },
            )
        }
        is AppState.EpisodeDetail -> AppNavigationDrawer(
            sections = current.ctx.sections,
            selectedSectionKey = current.ctx.selectedSection.key,
            isSettingsSelected = false,
            isHomeSelected = false,
            onSelectSection = { section -> selectSection(current.ctx, section) },
            onOpenSettings = { state = AppState.Settings(current.ctx, returnState = AppState.Library(current.ctx)) },
            onOpenHome = { scope.launch { state = loadHome(current.ctx.server, current.ctx.sections) } },
            account = localAccount,
        ) {
            EpisodeDetailScreen(
                server = current.ctx.server,
                showTitle = current.show.title,
                episode = current.episode,
                onBack = { returnTo(current.returnState) },
                onPlay = {
                    scope.launch {
                        val fetched = runCatching {
                            PlexServerApi(current.ctx.server, clientIdentifier).fetchMovieDetail(current.episode.ratingKey)
                        }
                        state = fetched.fold(
                            onSuccess = { detail -> AppState.Player(current.ctx.server, detail, current, relay = null) },
                            onFailure = { AppState.Error(it.message ?: "Couldn't load playback info") },
                        )
                    }
                },
                onPlayFromStart = {
                    scope.launch {
                        val fetched = runCatching {
                            PlexServerApi(current.ctx.server, clientIdentifier).fetchMovieDetail(current.episode.ratingKey)
                        }
                        state = fetched.fold(
                            onSuccess = { detail ->
                                AppState.Player(current.ctx.server, detail.copy(viewOffset = 0L), current, relay = null)
                            },
                            onFailure = { AppState.Error(it.message ?: "Couldn't load playback info") },
                        )
                    }
                },
                onWatchTogether = {
                    scope.launch {
                        val hostName = localAccount?.username ?: "Host"
                        val settings = context.appSettingsStore.observe().first()
                        val defaultRelay = settings.defaultRelay
                        if (defaultRelay == null) {
                            state = AppState.Settings(
                                current.ctx,
                                returnState = current,
                                relayHint = "Add a relay to watch with friends.",
                            )
                            return@launch
                        }
                        val episode = current.episode
                        val roomTitle = buildString {
                            append(current.show.title)
                            episode.parentIndex?.let { season -> episode.index?.let { ep -> append(" · S${season}E$ep") } }
                        }
                        val relay = ensureRelayClient(
                            defaultRelay.url,
                            RoomIntent.Create(
                                title = roomTitle,
                                thumb = episode.thumb,
                                ratingKey = episode.ratingKey,
                                hostName = hostName,
                                maxSeats = settings.maxHostSeats,
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
                                PlexServerApi(current.ctx.server, clientIdentifier).fetchMovieDetail(episode.ratingKey)
                            }
                            state = fetched.fold(
                                onSuccess = { detail ->
                                    AppState.Lobby(
                                        current.ctx.server,
                                        detail,
                                        current,
                                        relay,
                                        hostName,
                                        defaultRelay.nickname,
                                        thumb = episode.thumb,
                                        isHost = true,
                                    )
                                },
                                onFailure = { AppState.Error(it.message ?: "Couldn't load playback info") },
                            )
                        }
                    }
                },
            )
        }
        is AppState.Lobby -> key(current.detail.ratingKey) {
            LobbyScreen(
                server = current.server,
                detail = current.detail,
                localUsername = localAccount?.username ?: "You",
                localAvatarUrl = localAccount?.thumb,
                hostName = current.hostName,
                relayNickname = current.relayNickname,
                relay = current.relay,
                onHostOnAnother = if (current.isHost) {
                    { scope.launch { hostOnAnotherRelay(current) } }
                } else {
                    null
                },
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
}
