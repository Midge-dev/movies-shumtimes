package com.moviesshumtimes.tv.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppOnSurfaceVariant
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun DigitalClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }
    var now by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            val millisToNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(millisToNextMinute)
        }
    }

    Text(
        text = formatter.format(now),
        style = ShumTypography.bodyLarge,
        color = AppOnSurfaceVariant,
        modifier = modifier,
    )
}
