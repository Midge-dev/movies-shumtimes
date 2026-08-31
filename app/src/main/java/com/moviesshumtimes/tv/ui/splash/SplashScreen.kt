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

private const val BLOOM_DURATION_MS = 900
private const val MARK_DURATION_MS = 500
private const val WORDMARK_DURATION_MS = 500
private const val WORDMARK_DELAY_MS = 450
private const val KICKER_DURATION_MS = 700
private const val KICKER_DELAY_MS = 650

private const val BREATHE_PERIOD_MS = 1800

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
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
    val kickerTracking by animateFloatAsState(
        targetValue = if (started) 0.32f else 0.06f,
        animationSpec = tween(KICKER_DURATION_MS, delayMillis = KICKER_DELAY_MS),
        label = "splashKickerTracking",
    )

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
                modifier = Modifier.padding(top = 24.dp).graphicsLayer { alpha = wordmarkAlpha },
            )
        }
    }
}
