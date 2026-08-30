package com.xxx.carelorie.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The current window width class, provided once by MainActivity.
 *
 * Passing this down as a parameter meant every screen that wanted it needed a new argument on
 * itself, on the navigation graph and on the app shell — which is why six screens never got it
 * and FoodSearchScreen ended up with an `isWideScreen` parameter it never read. A composition
 * local means any screen can ask for the width it has without anything in between having to
 * care.
 */
val LocalWindowWidthSizeClass: ProvidableCompositionLocal<WindowWidthSizeClass> =
    compositionLocalOf { WindowWidthSizeClass.Compact }

/** True on tablets and on phones in landscape — anything that is not a single narrow column. */
val isWideScreen: Boolean
    @Composable get() = LocalWindowWidthSizeClass.current != WindowWidthSizeClass.Compact

/** True only on the widest windows, where two full panes fit side by side. */
val isExpandedScreen: Boolean
    @Composable get() = LocalWindowWidthSizeClass.current == WindowWidthSizeClass.Expanded

/**
 * Widths past which a single column of content stops being readable.
 *
 * A text field or a paragraph stretched across a 10" tablet is harder to use than the same
 * content at phone width, so beyond these limits content is centred rather than stretched.
 */
object ContentWidth {
    /** Forms: login, register, profile, the food editor. */
    val Form: Dp = 640.dp

    /** Longer reading and list content: chat transcripts, review lists. */
    val Reading: Dp = 840.dp
}

/**
 * Caps this element's width at [maxWidth] once the window is wider than a phone.
 *
 * A no-op on compact widths, so phone layouts are completely unaffected. Written as a modifier
 * rather than a wrapper composable because the screens that need it already have long
 * `fillMaxSize().verticalScroll(...)` chains that a wrapper would fight with — this slots into
 * the chain instead. Pair it with a parent that centres horizontally.
 */
@Composable
fun Modifier.constrainedWidth(maxWidth: Dp = ContentWidth.Form): Modifier =
    if (isWideScreen) this.widthIn(max = maxWidth) else this

/**
 * Constrains [content] to [maxWidth] and centres it once the window is wider than a phone.
 *
 * For content that is not already inside its own scrolling column; otherwise reach for
 * [constrainedWidth].
 */
@Composable
fun ResponsiveContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = ContentWidth.Form,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier.constrainedWidth(maxWidth).fillMaxWidth(),
            content = content
        )
    }
}
