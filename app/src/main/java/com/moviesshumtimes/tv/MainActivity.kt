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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.data.plex.PlexAuthApi
import com.moviesshumtimes.tv.data.plex.PlexIdentity
import com.moviesshumtimes.tv.data.plex.TokenStore
import com.moviesshumtimes.tv.ui.auth.AuthScreen

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
                    SessionGate()
                }
            }
        }
    }
}

private sealed interface SessionState {
    data object Checking : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val username: String) : SessionState
}

@Composable
private fun SessionGate() {
    val context = LocalContext.current
    var session by remember { mutableStateOf<SessionState>(SessionState.Checking) }

    LaunchedEffect(Unit) {
        val token = TokenStore.loadToken(context)
        session = if (token == null) {
            SessionState.LoggedOut
        } else {
            val clientIdentifier = PlexIdentity.getOrCreateClientIdentifier(context)
            val username = runCatching { PlexAuthApi(clientIdentifier).fetchUsername(token) }.getOrNull()
            username?.let { SessionState.LoggedIn(it) } ?: SessionState.LoggedOut
        }
    }

    when (val current = session) {
        is SessionState.Checking -> Text("Loading…")
        is SessionState.LoggedOut -> AuthScreen(onLoggedIn = { username -> session = SessionState.LoggedIn(username) })
        is SessionState.LoggedIn -> Text("Logged in as ${current.username}")
    }
}
