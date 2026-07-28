package dev.bluehouse.enablevolte.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
    darkColorScheme(
        primary = PremiumViolet,
        secondary = PremiumBlue,
        tertiary = PremiumCyan,
        background = Color(0xFF0D0F14),
        surface = Color(0xFF11141B),
        surfaceContainer = Color(0xFF191D27),
        surfaceContainerLow = Color(0xFF141820),
        surfaceContainerHigh = Color(0xFF202532),
        onBackground = Color(0xFFF3F3FA),
        onSurface = Color(0xFFF3F3FA),
        onSurfaceVariant = Color(0xFFB9BDCA),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PremiumVioletDark,
        secondary = PremiumBlueDark,
        tertiary = PremiumCyanDark,
        background = Color(0xFFF6F7FC),
        surface = Color(0xFFFBFBFF),
        surfaceContainer = Color(0xFFEFF0F8),
        surfaceContainerLow = Color(0xFFF5F5FC),
        surfaceContainerHigh = Color(0xFFE8E9F3),
        onBackground = Color(0xFF171820),
        onSurface = Color(0xFF171820),
        onSurfaceVariant = Color(0xFF5E606C),
    )

@Suppress("ktlint:standard:function-naming")
@Composable
fun EnableVoLTETheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            window.isNavigationBarContrastEnforced = false
            ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars = !darkTheme
            ViewCompat.getWindowInsetsController(view)?.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp),
        ),
        content = content,
    )
}
