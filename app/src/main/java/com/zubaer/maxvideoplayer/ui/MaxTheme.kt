package com.zubaer.maxvideoplayer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun MaxTheme(
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val normal = if (dark) darkColorScheme() else lightColorScheme()
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
