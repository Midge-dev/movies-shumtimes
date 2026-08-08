package com.moviesshumtimes.tv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonGlow
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.Glow

private val NeonPurpleButtonShape = RoundedCornerShape(50)
private val NeonPurpleCardShape = RoundedCornerShape(8.dp)

// A flat NeonPurple container fill alone reads as one-tone; layering a
// glow-to-outer-purple gradient stroke plus a matching elevation glow gives
// every focusable button the same two-tone treatment used on the player's
// native transport buttons (see exo_control_button_focus.xml).
@Composable
fun neonPurpleButtonBorder(): ButtonBorder = ButtonDefaults.border(
    focusedBorder = Border(
        border = BorderStroke(2.dp, Brush.radialGradient(listOf(NeonPurpleGlow, NeonPurple))),
        shape = NeonPurpleButtonShape,
    ),
)

@Composable
fun neonPurpleButtonGlow(): ButtonGlow = ButtonDefaults.glow(
    focusedGlow = Glow(elevationColor = NeonPurpleGlow, elevation = 12.dp),
)

// Same two-tone treatment as the buttons above, for Card — movie/season/
// episode posters were still falling back to tv-material3's default focus
// border instead of matching the rest of the app's focus styling.
@Composable
fun neonPurpleCardBorder(): CardBorder = CardDefaults.border(
    focusedBorder = Border(
        border = BorderStroke(2.dp, Brush.radialGradient(listOf(NeonPurpleGlow, NeonPurple))),
        shape = NeonPurpleCardShape,
    ),
)

@Composable
fun neonPurpleCardGlow(): CardGlow = CardDefaults.glow(
    focusedGlow = Glow(elevationColor = NeonPurpleGlow, elevation = 12.dp),
)
