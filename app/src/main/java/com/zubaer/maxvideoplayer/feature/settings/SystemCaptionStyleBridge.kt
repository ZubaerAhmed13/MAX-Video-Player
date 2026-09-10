package com.zubaer.maxvideoplayer.feature.settings

import android.content.Context
import android.view.accessibility.CaptioningManager
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleEdgeStyle
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleStyleState

/**
 * Applies Android's current caption appearance to the existing MAX subtitle renderer while keeping
 * the user's MAX custom style in a separate backup. Disabling the setting restores that backup.
 * No subtitle engine or Media3 playback path is replaced.
 */
object SystemCaptionStyleBridge {
    private const val PREFS = "step9_system_caption_bridge_v1"
    private const val BACKUP_VALID = "backup_valid"

    fun apply(context: Context, repository: SubtitleRepository, enabled: Boolean): Boolean {
        if (!enabled) {
            restoreCustomStyle(context, repository)
            repository.setUseSystemCaptionStyle(false)
            return true
        }
        val manager = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager ?: return false
        backupCustomStyleIfNeeded(context, repository.style.value)
        val current = repository.style.value
        val style = manager.userStyle
        repository.setForegroundColor(if (style.hasForegroundColor()) style.foregroundColor else current.foregroundColor)
        repository.setBackgroundColor(if (style.hasBackgroundColor()) style.backgroundColor else current.backgroundColor)
        repository.setWindowColor(if (style.hasWindowColor()) style.windowColor else current.windowColor)
        repository.setEdgeColor(if (style.hasEdgeColor()) style.edgeColor else current.edgeColor)
        repository.setEdgeStyle(
            when (style.edgeType) {
                1 -> SubtitleEdgeStyle.OUTLINE
                2 -> SubtitleEdgeStyle.DROP_SHADOW
                3 -> SubtitleEdgeStyle.RAISED
                4 -> SubtitleEdgeStyle.DEPRESSED
                else -> SubtitleEdgeStyle.NONE
            },
        )
        repository.setTextScale((backupStyle(context)?.textScale ?: current.textScale) * manager.fontScale.coerceIn(0.5f, 2f))
        repository.setUseSystemCaptionStyle(true)
        return true
    }

    private fun backupCustomStyleIfNeeded(context: Context, style: SubtitleStyleState) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(BACKUP_VALID, false)) return
        prefs.edit()
            .putBoolean(BACKUP_VALID, true)
            .putFloat("textScale", style.textScale)
            .putInt("foreground", style.foregroundColor)
            .putInt("background", style.backgroundColor)
            .putInt("window", style.windowColor)
            .putString("edgeStyle", style.edgeStyle.name)
            .putInt("edgeColor", style.edgeColor)
            .putFloat("bottomPadding", style.bottomPaddingFraction)
            .putBoolean("embeddedStyles", style.applyEmbeddedStyles)
            .putBoolean("embeddedSizes", style.applyEmbeddedFontSizes)
            .commit()
    }

    private fun restoreCustomStyle(context: Context, repository: SubtitleRepository) {
        val style = backupStyle(context) ?: return
        repository.setTextScale(style.textScale)
        repository.setForegroundColor(style.foregroundColor)
        repository.setBackgroundColor(style.backgroundColor)
        repository.setWindowColor(style.windowColor)
        repository.setEdgeStyle(style.edgeStyle)
        repository.setEdgeColor(style.edgeColor)
        repository.setBottomPaddingFraction(style.bottomPaddingFraction)
        repository.setApplyEmbeddedStyles(style.applyEmbeddedStyles)
        repository.setApplyEmbeddedFontSizes(style.applyEmbeddedFontSizes)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun backupStyle(context: Context): SubtitleStyleState? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(BACKUP_VALID, false)) return null
        val defaults = SubtitleStyleState()
        return SubtitleStyleState(
            textScale = prefs.getFloat("textScale", defaults.textScale),
            foregroundColor = prefs.getInt("foreground", defaults.foregroundColor),
            backgroundColor = prefs.getInt("background", defaults.backgroundColor),
            windowColor = prefs.getInt("window", defaults.windowColor),
            edgeStyle = runCatching {
                SubtitleEdgeStyle.valueOf(prefs.getString("edgeStyle", defaults.edgeStyle.name) ?: defaults.edgeStyle.name)
            }.getOrDefault(defaults.edgeStyle),
            edgeColor = prefs.getInt("edgeColor", defaults.edgeColor),
            bottomPaddingFraction = prefs.getFloat("bottomPadding", defaults.bottomPaddingFraction),
            applyEmbeddedStyles = prefs.getBoolean("embeddedStyles", defaults.applyEmbeddedStyles),
            applyEmbeddedFontSizes = prefs.getBoolean("embeddedSizes", defaults.applyEmbeddedFontSizes),
            useSystemCaptionStyle = false,
        )
    }
}
