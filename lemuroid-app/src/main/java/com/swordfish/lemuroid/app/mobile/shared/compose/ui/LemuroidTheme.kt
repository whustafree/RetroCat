package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightColorScheme =
    lightColorScheme(
        primary = md_theme_light_primary,
        onPrimary = md_theme_light_onPrimary,
        primaryContainer = md_theme_light_primaryContainer,
        onPrimaryContainer = md_theme_light_onPrimaryContainer,
        secondary = md_theme_light_secondary,
        onSecondary = md_theme_light_onSecondary,
        secondaryContainer = md_theme_light_secondaryContainer,
        onSecondaryContainer = md_theme_light_onSecondaryContainer,
        tertiary = md_theme_light_tertiary,
        onTertiary = md_theme_light_onTertiary,
        tertiaryContainer = md_theme_light_tertiaryContainer,
        onTertiaryContainer = md_theme_light_onTertiaryContainer,
        error = md_theme_light_error,
        errorContainer = md_theme_light_errorContainer,
        onError = md_theme_light_onError,
        onErrorContainer = md_theme_light_onErrorContainer,
        background = md_theme_light_background,
        onBackground = md_theme_light_onBackground,
        surface = md_theme_light_surface,
        onSurface = md_theme_light_onSurface,
        surfaceVariant = md_theme_light_surfaceVariant,
        onSurfaceVariant = md_theme_light_onSurfaceVariant,
        outline = md_theme_light_outline,
        inverseOnSurface = md_theme_light_inverseOnSurface,
        inverseSurface = md_theme_light_inverseSurface,
        inversePrimary = md_theme_light_inversePrimary,
        surfaceTint = md_theme_light_surfaceTint,
        outlineVariant = md_theme_light_outlineVariant,
        scrim = md_theme_light_scrim,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        primaryContainer = md_theme_dark_primaryContainer,
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        secondary = md_theme_dark_secondary,
        onSecondary = md_theme_dark_onSecondary,
        secondaryContainer = md_theme_dark_secondaryContainer,
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        tertiary = md_theme_dark_tertiary,
        onTertiary = md_theme_dark_onTertiary,
        tertiaryContainer = md_theme_dark_tertiaryContainer,
        onTertiaryContainer = md_theme_dark_onTertiaryContainer,
        error = md_theme_dark_error,
        errorContainer = md_theme_dark_errorContainer,
        onError = md_theme_dark_onError,
        onErrorContainer = md_theme_dark_onErrorContainer,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        outline = md_theme_dark_outline,
        inverseOnSurface = md_theme_dark_inverseOnSurface,
        inverseSurface = md_theme_dark_inverseSurface,
        inversePrimary = md_theme_dark_inversePrimary,
        surfaceTint = md_theme_dark_surfaceTint,
        outlineVariant = md_theme_dark_outlineVariant,
        scrim = md_theme_dark_scrim,
    )

/**
 * Pixel art means hard edges: nothing in the UI gets rounded, the way sprites are drawn on a
 * block grid. Only the largest surfaces keep a 4dp corner.
 */
private val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(4.dp),
    )

/**
 * Monospaced type throughout, which is the other half of the pixel art look, plus bolder titles
 * so game names stay readable over artwork.
 */
private val AppTypography =
    Typography().run {
        copy(
            displayLarge = displayLarge.withPixelFont(FontWeight.Bold),
            displayMedium = displayMedium.withPixelFont(FontWeight.Bold),
            displaySmall = displaySmall.withPixelFont(FontWeight.Bold),
            headlineLarge = headlineLarge.withPixelFont(FontWeight.Bold),
            headlineMedium = headlineMedium.withPixelFont(FontWeight.Bold),
            headlineSmall = headlineSmall.withPixelFont(FontWeight.Bold),
            titleLarge = titleLarge.withPixelFont(FontWeight.Bold),
            titleMedium = titleMedium.withPixelFont(FontWeight.Bold),
            titleSmall = titleSmall.withPixelFont(FontWeight.Bold),
            bodyLarge = bodyLarge.withPixelFont(FontWeight.Normal),
            bodyMedium = bodyMedium.withPixelFont(FontWeight.Normal),
            bodySmall = bodySmall.withPixelFont(FontWeight.Normal),
            labelLarge = labelLarge.withPixelFont(FontWeight.SemiBold),
            labelMedium = labelMedium.withPixelFont(FontWeight.SemiBold),
            labelSmall = labelSmall.withPixelFont(FontWeight.SemiBold),
        )
    }

private fun TextStyle.withPixelFont(weight: FontWeight) = copy(fontFamily = FontFamily.Monospace, fontWeight = weight)

/**
 * Material You is deliberately off. It rebuilds the scheme from the user's wallpaper, which
 * replaced the retro palette with whatever colours the wallpaper happened to have, so the pixel
 * art look was never actually applied. Flip this to true if the palette is ever dropped.
 */
private const val USE_DYNAMIC_COLOR = false

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors =
        when {
            USE_DYNAMIC_COLOR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
                dynamicDarkColorScheme(LocalContext.current)
            USE_DYNAMIC_COLOR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                dynamicLightColorScheme(LocalContext.current)
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        content()
    }
}
