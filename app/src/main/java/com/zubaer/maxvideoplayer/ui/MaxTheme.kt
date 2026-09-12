package com.zubaer.maxvideoplayer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalReduceMotion = compositionLocalOf { false }

/** Centralized Step-10 release-hardening design tokens. */
object MaxDesignTokens {
    val Accent = Color(0xFF315F8A)
    val AccentStrong = Color(0xFF214B72)
    val SurfaceLight = Color(0xFFFCFCFD)
    val SurfaceVariantLight = Color(0xFFF1F3F5)
    val TextPrimaryLight = Color(0xFF17202A)
    val TextSecondaryLight = Color(0xFF66717D)
    val DividerLight = Color(0xFFE2E6EA)

    val SurfaceDark = Color(0xFF111417)
    val SurfaceVariantDark = Color(0xFF20252A)
    val TextPrimaryDark = Color(0xFFF4F6F8)
    val TextSecondaryDark = Color(0xFFB5BDC6)
    val DividerDark = Color(0xFF32383F)

    val PlayerOverlay = Color(0xB30B0D10)
    val PlayerOverlaySoft = Color(0x800B0D10)
    val PlayerControl = Color(0xA61B1F24)
    val PlayerControlPressed = Color(0xCC252B31)
    val PlayerText = Color.White
    val PlayerTextSecondary = Color(0xFFD6DBE0)
    val SelectedBackground = Color(0x22315F8A)
}

@Composable
fun MaxTheme(
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val normal = if (dark) {
        darkColorScheme(
            primary = Color(0xFF9DC8EE),
            onPrimary = Color(0xFF072A45),
            secondary = Color(0xFFB9C8D6),
            background = MaxDesignTokens.SurfaceDark,
            onBackground = MaxDesignTokens.TextPrimaryDark,
            surface = MaxDesignTokens.SurfaceDark,
            onSurface = MaxDesignTokens.TextPrimaryDark,
            surfaceVariant = MaxDesignTokens.SurfaceVariantDark,
            onSurfaceVariant = MaxDesignTokens.TextSecondaryDark,
            outline = MaxDesignTokens.DividerDark,
        )
    } else {
        lightColorScheme(
            primary = MaxDesignTokens.Accent,
            onPrimary = Color.White,
            secondary = MaxDesignTokens.AccentStrong,
            background = MaxDesignTokens.SurfaceLight,
            onBackground = MaxDesignTokens.TextPrimaryLight,
            surface = Color.White,
            onSurface = MaxDesignTokens.TextPrimaryLight,
            surfaceVariant = MaxDesignTokens.SurfaceVariantLight,
            onSurfaceVariant = MaxDesignTokens.TextSecondaryLight,
            outline = MaxDesignTokens.DividerLight,
        )
    }

    val scheme = if (!highContrast) normal else if (dark) {
        normal.copy(
            primary = Color.White,
            onPrimary = Color.Black,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            outline = Color.White,
        )
    } else {
        normal.copy(
            primary = Color.Black,
            onPrimary = Color.White,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            outline = Color.Black,
        )
    }

    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
