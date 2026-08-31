package com.moviesshumtimes.tv.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.moviesshumtimes.tv.R
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppBackground
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow

// Design spec section 09, step 1 "Splash": ~1.4s total, cold-start only —
// MainActivity holds this up for at least that long (see SPLASH_MIN_HOLD_MS)
// while the Plex token check runs behind it, then cross-fades to whatever
// that check resolved to (Home for a warm start, the Link Plex step
// otherwise). A slow check just means a longer hold at this screen's
// settled end frame, not a longer animation.
//
// logo_wordmark.png (the raster fallback) is used instead of live Space
// Grotesk type — the spec calls for live type "so it stays crisp at any
// size" but explicitly keeps this PNG "as the raster fallback for platforms
// without the font", which is exactly this situation: nothing in this app
// bundles Space Grotesk today, and adding a font just for a 1.4s splash
// isn't worth it when the spec already names the correct fallback.
// Sean's feedback on the first pass: barely readable as an animation at
// all, and felt cut off. Two real problems, not one: the motion itself was
// too subtle (small scale deltas, a 10dp rise), and every stage finished by
// ~1080ms — the last ~300ms of the 1.4s hold was just a static frame, so a
// glance from the couch could easily land entirely inside "nothing moving."
// Bigger deltas and stages that overlap across the *whole* 1.4s window (not
// front-loaded and done) fixes both at once.
private const val BLOOM_DURATION_MS = 900
private const val MARK_DURATION_MS = 500
private const val WORDMARK_DURATION_MS = 500
private const val WORDMARK_DELAY_MS = 450
private const val KICKER_DURATION_MS = 700
private const val KICKER_DELAY_MS = 650

// A real Plex token check can easily take longer than 1.4s (DNS, TLS, a
// remote server) — this app's own conventions already have a name for a
// slow, continuous pulse used while something is genuinely still working
// (see PlayerScreen/RelayStatus's Spinner, and the design canvas's own
// shum-breathe/shum-pulse keyframes). Without this, the entrance animation
// finishes and the screen just sits dead still for however much longer the
// real work takes — which is what actually read as "cut off": not the
// animation ending too soon, but a long static hold before an abrupt cut
// to Home with nothing having moved in between.
private const val BREATHE_PERIOD_MS = 1800

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    // Flips true one frame after entering composition so every animateFooAsState
    // below actually animates from its initial value instead of snapping
    // straight to the target on the very first frame.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val bloomScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.35f,
        animationSpec = tween(BLOOM_DURATION_MS),
        label = "splashBloomScale",
    )
    val markScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.6f,
        animationSpec = tween(MARK_DURATION_MS),
        label = "splashMarkScale",
    )
    val markAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(MARK_DURATION_MS),
        label = "splashMarkAlpha",
    )
    val wordmarkOffset by animateDpAsState(
        targetValue = if (started) 0.dp else 24.dp,
        animationSpec = tween(WORDMARK_DURATION_MS, delayMillis = WORDMARK_DELAY_MS),
        label = "splashWordmarkOffset",
    )
    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(WORDMARK_DURATION_MS, delayMillis = WORDMARK_DELAY_MS),
        label = "splashWordmarkAlpha",
    )
    // .06em -> .32em, expressed as a plain float multiplied back into .em
    // below — animateFloatAsState has no unit-aware TextUnit overload. Ends
    // right at the 1.4s ceiling (650 + 700 = 1350ms) instead of coasting the
    // last ~300ms with nothing moving.
    val kickerTracking by animateFloatAsState(
        targetValue = if (started) 0.32f else 0.06f,
        animationSpec = tween(KICKER_DURATION_MS, delayMillis = KICKER_DELAY_MS),
        label = "splashKickerTracking",
    )

    // Runs continuously the entire time this screen is up, independent of
    // the entrance sequence above — this is what keeps the screen visibly
    // alive for however long the real token check takes, not just the
    // first 1.4s.
    val breatheTransition = rememberInfiniteTransition(label = "splashBreathe")
    val breathe by breatheTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATHE_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashBreatheValue",
    )
    val breatheScale = 1f + breathe * 0.08f
    val breatheAlpha = 0.5f + breathe * 0.35f

    Box(modifier = modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // The bloom: a soft radial glow scaling in behind the mark,
                // same drawn-not-elevated approach as every other glow in
                // this app (see FocusableSurface.drawGlow) — no platform
                // blur, just a radial gradient fading to transparent.
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .graphicsLayer {
                            val scale = bloomScale * breatheScale
                            scaleX = scale
                            scaleY = scale
                            alpha = markAlpha * breatheAlpha
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(NeonPurpleGlow.copy(alpha = 0.35f), Color.Transparent),
                                center = Offset.Unspecified,
                            ),
                        ),
                )
                Image(
                    painter = painterResource(R.drawable.logo_mark),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    // logo_mark.png is the full M + shaka-hand lockup, 1181x696
                    // (~1.7:1) — sizing by width + its real aspect ratio (rather
                    // than a square box) is what keeps it from looking squashed
                    // or cropped inside its allotted space.
                    modifier = Modifier
                        .width(260.dp)
                        .aspectRatio(1181f / 696f)
                        .graphicsLayer { scaleX = markScale; scaleY = markScale; alpha = markAlpha },
                )
            }
            Image(
                painter = painterResource(R.drawable.logo_wordmark),
                contentDescription = "Movies Shumtimes",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .size(width = 260.dp, height = 22.dp)
                    .graphicsLayer {
                        alpha = wordmarkAlpha
                        translationY = wordmarkOffset.toPx()
                    },
            )
            Text(
                text = "Watch together",
                color = AppWhite.copy(alpha = 0.7f),
                style = TextStyle(letterSpacing = kickerTracking.em),
                // 24dp, not 14 — the wordmark above rides in on its own
                // translationY slide (up to 24dp, paint-time only, so it
                // doesn't affect this row's layout position). A smaller gap
                // let the sliding wordmark visually clip into this row for
                // the first instant of the entrance.
                modifier = Modifier.padding(top = 24.dp).graphicsLayer { alpha = wordmarkAlpha },
            )
        }
    }
}
