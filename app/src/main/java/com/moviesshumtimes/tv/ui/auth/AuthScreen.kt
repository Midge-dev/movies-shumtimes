package com.moviesshumtimes.tv.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.data.plex.PlexAuthApi
import com.moviesshumtimes.tv.data.plex.PlexIdentity
import com.moviesshumtimes.tv.data.plex.TokenStore
import com.moviesshumtimes.tv.ui.common.QrCodeImage
import kotlinx.coroutines.delay

private sealed interface AuthState {
    data object Loading : AuthState
    data class AwaitingLink(val code: String) : AuthState
    data class Error(val message: String) : AuthState
}

private const val POLL_INTERVAL_MS = 2_000L

@Composable
fun AuthScreen(onLoggedIn: (token: String) -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<AuthState>(AuthState.Loading) }

    LaunchedEffect(Unit) {
        try {
            val clientIdentifier = PlexIdentity.getOrCreateClientIdentifier(context)
            val api = PlexAuthApi(clientIdentifier)

            val pin = api.createPin()
            state = AuthState.AwaitingLink(pin.code)

            val deadline = System.currentTimeMillis() + pin.expiresIn * 1_000L
            var authToken: String? = null
            while (authToken == null && System.currentTimeMillis() < deadline) {
                delay(POLL_INTERVAL_MS)
                authToken = api.pollPin(pin.id).authToken
            }

            val token = authToken ?: run {
                state = AuthState.Error("Code expired — restart the app to get a new one")
                return@LaunchedEffect
            }

            TokenStore.saveToken(context, token)
            onLoggedIn(token)
        } catch (e: Exception) {
            state = AuthState.Error(e.message ?: "Something went wrong")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (val current = state) {
                is AuthState.Loading -> Text("Connecting to Plex…")
                is AuthState.AwaitingLink -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QrCodeImage(
                            content = "https://www.plex.tv/link/",
                            modifier = Modifier
                                .size(220.dp)
                                .background(Color.White)
                                .padding(16.dp),
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            Text("Scan with your phone, or on any device visit plex.tv/link, then enter:")
                            Text(current.code, style = MaterialTheme.typography.displayMedium)
                        }
                    }
                }
                is AuthState.Error -> Text("Error: ${current.message}")
            }
        }
    }
}
