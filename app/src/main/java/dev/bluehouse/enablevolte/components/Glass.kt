package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.dp

val GlassShape = RoundedCornerShape(24.dp)

@Composable
fun GlassBackdrop(content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    colors.surface,
                    colors.surfaceContainerLow,
                    colors.primary.copy(alpha = 0.08f),
                    colors.surface,
                ),
            ),
        ),
    ) {
        Box(
            Modifier
                .size(320.dp)
                .offset(x = (-90).dp, y = (-70).dp)
                .background(
                    Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.20f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(300.dp)
                .align(androidx.compose.ui.Alignment.CenterEnd)
                .offset(x = 110.dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(listOf(colors.tertiary.copy(alpha = 0.13f), Color.Transparent)),
                    CircleShape,
                ),
        )
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            content()
        }
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val colors = MaterialTheme.colorScheme
    val border = if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.92f)
    val container = colors.surfaceContainer.copy(alpha = if (dark) 0.76f else 0.72f)
    val glassModifier =
        modifier
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.055f else 0.46f),
                        colors.primary.copy(alpha = if (dark) 0.035f else 0.025f),
                        Color.Transparent,
                    ),
                ),
                GlassShape,
            )
            .border(BorderStroke(1.dp, border), GlassShape)
    if (onClick == null) {
        Surface(
            modifier = glassModifier,
            shape = GlassShape,
            color = container,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = glassModifier,
            shape = GlassShape,
            color = container,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            content = content,
        )
    }
}
