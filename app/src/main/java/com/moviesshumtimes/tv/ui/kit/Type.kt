package com.moviesshumtimes.tv.ui.kit

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The app's type scale — design spec section 03 ("Type, space, shape"):
// role hierarchy is normative, absolute sizes are per-platform. These are
// the Android/Compose sizes; a tvOS/Roku port maps the same six roles to its
// own platform sizes rather than reusing these numbers. No custom font: the
// spec's mockup uses "the platform default sans... only the role hierarchy
// is normative", i.e. Android's system default (Roboto) is correct as-is.
//
// The spec's "headlineMedium / Small" and "bodyLarge / bodyMedium" rows are
// each drawn as one size — collapsed to a single style here rather than two
// identical ones, matching how call sites already use them interchangeably.
object ShumTypography {
    val displaySmall = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Normal)
    val displayMedium = TextStyle(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal)
    val headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal)
    val titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
    val titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
}
