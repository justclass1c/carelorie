package com.xxx.carelorie.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxx.carelorie.ui.theme.LocalIsDarkTheme

/**
 * A soft, wide shadow — the kind that reads as "this is floating slightly", not as a drawn edge.
 *
 * Compose's default shadow is tuned for Material's paper metaphor and comes out dark and tight.
 * Dropping the spot and ambient alphas well below their defaults and spreading the elevation gives
 * the diffuse look that makes a light-mode card sit above the page rather than being stuck to it.
 *
 * In dark mode this does nothing at all, and that is correct: a shadow is invisible against black.
 * Depth there comes from the surface being lighter than the ground, which [CarelorieCard] handles.
 */
fun Modifier.softShadow(
    elevation: Dp,
    shape: Shape,
    isDark: Boolean
): Modifier = if (isDark) this else this.shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = 0.10f),
    spotColor = Color.Black.copy(alpha = 0.10f)
)

/**
 * The standard container: content on a raised surface over the grouped background.
 *
 * Replaces the `Card(border = BorderStroke(1.dp, outline))` pattern that was repeated across the
 * app. A hairline border around every box is what made the UI look like a form — separation here
 * comes from the surface being a different colour to the page and lifting off it slightly, which
 * is quieter and does more.
 *
 * @param elevated a card that should read as sitting above its neighbours rather than beside them.
 * @param onClick makes the whole card tappable, with the ripple clipped to the rounded shape.
 * @param fillWidth set false when the caller sizes the card itself — a tile in a weighted row that
 * also sets an aspect ratio, for one, where filling the width afterwards would override the ratio
 * and the square would come out a rectangle.
 */
@Composable
fun CarelorieCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    elevated: Boolean = true,
    fillWidth: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current

    Card(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .then(if (elevated) Modifier.softShadow(10.dp, shape, isDark) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(
                // A hairline in dark mode only: on black, surface-versus-ground alone can be too
                // subtle to find a card's edge. In light mode the shadow already does that job and
                // a border on top of it just looks drawn on.
                if (isDark) {
                    Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape
                    )
                } else {
                    Modifier
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Zero regardless of [elevated]: Card's own elevation draws Material's stock shadow,
        // which is what softShadow above exists to replace. Stacking both would double the card
        // up with two different shadows.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/**
 * The large title at the top of a tab.
 *
 * iOS opens a screen by naming it in type big enough to act as the header, instead of a title bar.
 * Doing the same in one shared component is what makes the five tabs feel like one app — four of
 * them were hand-rolling a header and the fifth had a Material `TopAppBar`, so switching tabs
 * shifted the content down and changed the typography.
 */
@Composable
fun LargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    // Large type needs negative tracking or it reads loose and web-like.
                    letterSpacing = (-0.8).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (trailing != null) trailing()
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** A quiet, uppercase label above a group of rows — the iOS grouped-list section header. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}
