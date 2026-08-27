package com.moviesshumtimes.tv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val NeonPurple = Color(0xFFAD2BD7)
val NeonPurpleGlow = Color(0xFFE795FC)

// Pressed is its own distinct tone, not just "focused held down" — design
// spec section 04 draws it separately from both idle and focused for every
// filled/icon control (a darker purple, no border/glow).
val NeonPurplePressed = Color(0xFF8F22B3)

// The two-tone focus-border/glow treatment used everywhere (buttons, cards)
// — glow-to-outer-purple radial gradient built from the two colors above.
// Defined once here instead of separately in each *Border() helper so both
// stay visually identical and there's one place to change the gradient
// itself (direction, stops, etc.), not just its two end colors.
val NeonPurpleGradient: Brush = Brush.radialGradient(listOf(NeonPurpleGlow, NeonPurple))

// tv-material3's default dark color tokens are tuned for close-up phone
// viewing and read low-contrast from a couch on a real TV panel — this is
// an explicit, higher-contrast dark palette instead of the library default.
val AppBackground = Color(0xFF0D0D12)
val AppOnBackground = Color(0xFFF2F2F5)
val AppSurface = Color(0xFF17171D)
val AppOnSurface = Color(0xFFF2F2F5)
val AppSurfaceVariant = Color(0xFF2A2A33)
val AppOnSurfaceVariant = Color(0xFFC7C7D1)

// Every other color literal in the app routes through here too, so the
// whole palette can be reskinned from this one file. These two are plain
// white/black rather than the near-white/near-black tokens above — content
// that sits directly on top of photos, video, or a QR code (overlay text,
// scrims, QR backgrounds) wants literal white/black contrast, not the
// app-chrome-tuned off-white used for onBackground/onSurface text.
val AppWhite = Color(0xFFFFFFFF)
val AppScrim = Color(0xFF000000)

// Unfocused/idle border and placeholder gray — pairs with NeonPurple/
// NeonPurpleGlow as the "not focused" state of the gradient focus borders
// (e.g. ClickToTypeTextField's idle outline).
val AppDimBorder = Color(0xFF444444)
