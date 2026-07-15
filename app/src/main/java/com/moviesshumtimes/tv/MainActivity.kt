package com.moviesshumtimes.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.data.plex.PlexAuthApi
import com.moviesshumtimes.tv.data.plex.PlexIdentity
import com.moviesshumtimes.tv.data.plex.PlexMovie
import com.moviesshumtimes.tv.data.plex.PlexResourcesApi
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.data.plex.PlexServerApi
import com.moviesshumtimes.tv.data.plex.TokenStore
import com.moviesshumtimes.tv.ui.auth.AuthScreen
import com.moviesshumtimes.tv.ui.library.LibraryScreen
import com.moviesshumtimes.tv.ui.library.MovieDetailScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    AppRoot()
                }
            }
        }
    }
}

private sealed interface AppState {
    data object Checking : AppState
    data object LoggedOut : AppState
    data class ConnectingToServer(val username: String?) : AppState
    data class Error(val message: String) : AppState
    data class Library(val server: PlexServer, val movies: List<PlexMovie>) : AppState
    data class MovieDetail(val server: PlexServer, val movie: PlexMovie, val movies: List<PlexMovie>) : AppState
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<AppState>(AppState.Checking) }

    suspend fun connect(token: String) {
        val clientIdentifier = PlexIdentity.getOrCreateClientIdentifier(context)
        val authApi = PlexAuthApi(clientIdentifier)
        val username = runCatching { authApi.fetchUsername(token) }.getOrNull()
        state = AppState.ConnectingToServer(username)

        state = runCatching {
            val server = PlexResourcesApi(clientIdentifier).findReachableServer(token)
                ?: error("No reachable Plex server found — is the cousin's server online?")
            val serverApi = PlexServerApi(server, clientIdentifier)
            val section = serverApi.fetchMovieSections().firstOrNull()
                ?: error("No movie library found on ${server.name}")
            val movies = serverApi.fetchMovies(section.key)
            AppState.Library(server, movies)
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
            server = current.server,
            movies = current.movies,
            onSelect = { movie -> state = AppState.MovieDetail(current.server, movie, current.movies) },
        )
        is AppState.MovieDetail -> MovieDetailScreen(
            server = current.server,
            movie = current.movie,
            onBack = { state = AppState.Library(current.server, current.movies) },
        )
    }
}
