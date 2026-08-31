package com.moviesshumtimes.tv.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.maxDimension
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.moviesshumtimes.tv.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private const val FRAME_HOLD_MS = 333L
private const val ROLL_DURATION_MS = 2800
private const val ROLL_BAR_HEIGHT_FRACTION = 0.34f
private const val BREATHE_HALF_CYCLE_MS = 1200
private const val CROSSFADE_MS = 220
private const val SCANLINE_PERIOD_PX = 3f

private val PlaceholderBase = Color(0xFF101015)
private val VignetteColor = Color(0xFFAD2BD7).copy(alpha = 0.22f)
private val RollBarColor = Color(0xFFE795FC).copy(alpha = 0.11f)
private val ScanlineColor = Color.Black.copy(alpha = 0.22f)

private object StaticNoiseFrames {
    private val resIds = intArrayOf(
        R.drawable.static_noise_1,
        R.drawable.static_noise_2,
        R.drawable.static_noise_3,
        R.drawable.static_noise_4,
        R.drawable.static_noise_5,
        R.drawable.static_noise_6,
    )
    private var cached: List<ImageBitmap>? = null

    fun get(context: Context): List<ImageBitmap> {
        cached?.let { return it }
        val decoded = resIds.map { resId ->
            BitmapFactory.decodeResource(context.resources, resId).asImageBitmap()
        }
        cached = decoded
        return decoded
    }
}

private enum class ArtworkPhase { LOADING, LOADED, FAILED }

/**
 * Drop-in replacement for Coil's AsyncImage that shows the design system's
 * "lost signal" placeholder while the image is in flight, and cross-fades
 * to the real artwork over [CROSSFADE_MS] once it lands. On failure the
 * grain simply stops — no error glyph, no retry affordance; whatever the
 * card's own idle background is shows through with just its title.
 *
 * @param noiseOpacity ceiling for the placeholder's grain layer — 0.5 for a
 *   single poster/still, 0.4 in a 5-across grid (per spec, so a whole grid
 *   never strobes together), 0.3 for a full-bleed backdrop that sits under
 *   its own text scrim.
 * @param staggerDelayMs offsets the roll-bar's phase — pass `index * 120` in
 *   a grid/row so cards don't pulse in unison; 0 elsewhere.
 */
@Composable
fun ShumArtwork(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    noiseOpacity: Float = 0.5f,
    staggerDelayMs: Int = 0,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    ) {
        val phase = when (painter.state.collectAsState().value) {
            is AsyncImagePainter.State.Success -> ArtworkPhase.LOADED
            is AsyncImagePainter.State.Error -> ArtworkPhase.FAILED
            else -> ArtworkPhase.LOADING
        }
        Crossfade(
            targetState = phase,
            animationSpec = tween(CROSSFADE_MS, easing = EaseOut),
            modifier = Modifier.matchParentSize(),
            label = "artworkCrossfade",
        ) { current ->
            when (current) {
                ArtworkPhase.LOADED -> SubcomposeAsyncImageContent()
                ArtworkPhase.FAILED -> Box(Modifier.fillMaxSize())
                ArtworkPhase.LOADING -> PosterPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    noiseOpacity = noiseOpacity,
                    staggerDelayMs = staggerDelayMs,
                )
            }
        }
    }
}

@Composable
private fun PosterPlaceholder(modifier: Modifier, noiseOpacity: Float, staggerDelayMs: Int) {
    val context = LocalContext.current
    val frames = remember(context) { StaticNoiseFrames.get(context) }
    var frameIndex by remember { mutableIntStateOf(Random.nextInt(frames.size)) }
    LaunchedEffect(frames) {
        while (true) {
            delay(FRAME_HOLD_MS)
            frameIndex = Random.nextInt(frames.size)
        }
    }
    val frame = frames[frameIndex]

    val transition = rememberInfiniteTransition(label = "posterPlaceholder")
    val breathe by transition.animateFloat(
        initialValue = noiseOpacity * 0.6f,
        targetValue = noiseOpacity,
        animationSpec = infiniteRepeatable(
            tween(BREATHE_HALF_CYCLE_MS, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    val rollProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(ROLL_DURATION_MS, easing = LinearEasing),
            initialStartOffset = StartOffset(staggerDelayMs, StartOffsetType.FastForward),
        ),
        label = "roll",
    )

    Box(
        modifier = modifier
            .background(PlaceholderBase)
            .drawWithCache {
                val barHeight = size.height * ROLL_BAR_HEIGHT_FRACTION
                val rollBarBrush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to RollBarColor,
                    1f to Color.Transparent,
                )
                val vignetteBrush = Brush.radialGradient(
                    colors = listOf(VignetteColor, Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.maxDimension * 0.75f,
                )
                val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                onDrawBehind {
                    drawImage(
                        image = frame,
                        dstSize = dstSize,
                        alpha = breathe,
                        blendMode = BlendMode.Screen,
                    )
                    drawRect(brush = vignetteBrush)
                    val barTop = rollProgress * (size.height + barHeight * 1.8f) - barHeight * 0.4f
                    drawRect(brush = rollBarBrush, topLeft = Offset(0f, barTop), size = Size(size.width, barHeight))
                    var y = 0f
                    while (y < size.height) {
                        drawRect(color = ScanlineColor, topLeft = Offset(0f, y), size = Size(size.width, 1f))
                        y += SCANLINE_PERIOD_PX
                    }
                }
            },
    )
}
