package com.xxx.carelorie.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * Values are taken from Apple's system colours rather than invented, because they are already
 * tuned as a set: each one has a light and a dark variant chosen to hold the same contrast against
 * its own background, which is what stops a theme switch from turning one accent muddy and another
 * garish.
 *
 * The app previously used the Material starter template's purples (Purple40 / PurpleGrey40), which
 * are a placeholder rather than a decision. systemIndigo is close enough to keep the app feeling
 * like itself while being an actual, considered colour.
 */

// ---------------------------------------------------------------- brand
val Indigo = Color(0xFF5856D6)      // systemIndigo, light
val IndigoDark = Color(0xFF7D7AFF)  // lifted for dark: 5E5CE6 reads too dim on black as text
val Blue = Color(0xFF007AFF)        // systemBlue
val BlueDark = Color(0xFF0A84FF)

// ---------------------------------------------------------------- light surfaces
/**
 * The page sits a step *below* the cards on it, not level with them.
 *
 * This is the single biggest reason iOS reads as layered rather than flat: content lives on white
 * cards floating over a grey ground. Both being white is what made this app look like a wall of
 * text held together by borders.
 */
val GroupedBackground = Color(0xFFF2F2F7)
val CardLight = Color(0xFFFFFFFF)
val FillLight = Color(0xFFF2F2F7)
val LabelLight = Color(0xFF1C1C1E)
val SecondaryLabelLight = Color(0xFF6C6C70)
val SeparatorLight = Color(0xFFD1D1D6)
val SeparatorSoftLight = Color(0xFFE5E5EA)

// ---------------------------------------------------------------- dark surfaces
/**
 * True black ground, with cards a step lighter.
 *
 * Dark mode inverts how depth is expressed: a shadow is invisible on black, so elevation is
 * carried by the surface getting *lighter* instead. See [CarelorieCard].
 */
val GroupedBackgroundDark = Color(0xFF000000)
val CardDark = Color(0xFF1C1C1E)
val FillDark = Color(0xFF2C2C2E)
val LabelDark = Color(0xFFFFFFFF)
val SecondaryLabelDark = Color(0xFF98989F)
val SeparatorDark = Color(0xFF38383A)
val SeparatorSoftDark = Color(0xFF2C2C2E)

// ---------------------------------------------------------------- status
val RedLight = Color(0xFFFF3B30)
val RedDark = Color(0xFFFF453A)
