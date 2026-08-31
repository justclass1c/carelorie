package com.xxx.carelorie.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One corner-radius scale for the whole app.
 *
 * Radii were previously picked per call site and ran 4, 6, 8, 10, 12, 16, 20 and 24 — which reads
 * as carelessness even when no single value is wrong, because two cards sitting next to each other
 * round differently. Material components read these from the theme, so most of the app becomes
 * consistent without being touched.
 *
 * The scale is deliberately rounder than Material's default. A radius has to grow with the element
 * to look like the same radius: 12dp on a chip and 12dp on a full-width card do not read as
 * related, which is why the large sizes step up faster.
 */
val CarelorieShapes = Shapes(
    /** Chips, tags, small indicators. */
    extraSmall = RoundedCornerShape(8.dp),
    /** Buttons, text fields, list rows. */
    small = RoundedCornerShape(12.dp),
    /** The default card. */
    medium = RoundedCornerShape(18.dp),
    /** Big feature cards and dialogs. */
    large = RoundedCornerShape(24.dp),
    /** Sheets and anything that meets a screen edge. */
    extraLarge = RoundedCornerShape(32.dp)
)
