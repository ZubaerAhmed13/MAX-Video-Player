package com.zubaer.maxvideoplayer.feature.subtitle

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.Locale

/**
 * Persists subtitle presentation preferences and one user-approved external subtitle association
 * per media stable ID. Media bytes are never copied into the app.
 */
class SubtitleRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _style = MutableStateFlow(loadStyle())
    val style: StateFlow<SubtitleStyleState> = _style.asStateFlow()

    fun describe(uri: Uri): SubtitleFileDescriptor? {
        val displayName = queryDisplayName(uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "External subtitle"
        val mimeType = SubtitleFormatPolicy.resolveMimeType(displayName, resolver.getType(uri)) ?: return null
        return SubtitleFileDescriptor(uri.toString(), displayName, mimeType)
    }

    fun persistReadPermission(uri: Uri): Boolean = runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)

    fun canOpen(uri: Uri): Boolean = runCatching {
        resolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    fun saveExternalAttachment(mediaId: String, descriptor: SubtitleFileDescriptor): ExternalSubtitleAttachment {
        val attachment = ExternalSubtitleAttachment(
            mediaId = mediaId,
            uri = descriptor.uri,
            label = descriptor.displayName,
            language = inferLanguage(descriptor.displayName),
            mimeType = descriptor.mimeType,
        )
        val key = associationPrefix(mediaId)
        preferences.edit()
            .putString("${key}uri", attachment.uri)
            .putString("${key}label", attachment.label)
            .putString("${key}language", attachment.language)
            .putString("${key}mime", attachment.mimeType)
            .apply()
        return attachment
    }

    fun externalAttachmentFor(mediaId: String): ExternalSubtitleAttachment? {
        val key = associationPrefix(mediaId)
        val uri = preferences.getString("${key}uri", null) ?: return null
        val label = preferences.getString("${key}label", null) ?: "External subtitle"
        val mime = preferences.getString("${key}mime", null) ?: return null
        return ExternalSubtitleAttachment(
            mediaId = mediaId,
            uri = uri,
            label = label,
            language = preferences.getString("${key}language", null),
            mimeType = mime,
        )
    }

    fun clearExternalAttachment(mediaId: String) {
        val key = associationPrefix(mediaId)
        preferences.edit()
            .remove("${key}uri")
            .remove("${key}label")
            .remove("${key}language")
            .remove("${key}mime")
            .apply()
    }

    fun setTextScale(value: Float) = updateStyle { it.copy(textScale = value.coerceIn(0.5f, 2f)) }
    fun setBottomPaddingFraction(value: Float) = updateStyle { it.copy(bottomPaddingFraction = value.coerceIn(0f, 0.35f)) }
    fun setForegroundColor(value: Int) = updateStyle { it.copy(foregroundColor = value) }
    fun setBackgroundColor(value: Int) = updateStyle { it.copy(backgroundColor = value) }
    fun setWindowColor(value: Int) = updateStyle { it.copy(windowColor = value) }
    fun setEdgeColor(value: Int) = updateStyle { it.copy(edgeColor = value) }
    fun setEdgeStyle(value: SubtitleEdgeStyle) = updateStyle { it.copy(edgeStyle = value) }
    fun setApplyEmbeddedStyles(value: Boolean) = updateStyle { it.copy(applyEmbeddedStyles = value) }
    fun setApplyEmbeddedFontSizes(value: Boolean) = updateStyle { it.copy(applyEmbeddedFontSizes = value) }

    fun resetStyle() {
        persistStyle(SubtitleStyleState())
    }

    private fun updateStyle(transform: (SubtitleStyleState) -> SubtitleStyleState) {
        persistStyle(transform(_style.value))
    }

    private fun persistStyle(style: SubtitleStyleState) {
        val safe = style.copy(
            textScale = style.textScale.coerceIn(0.5f, 2f),
            bottomPaddingFraction = style.bottomPaddingFraction.coerceIn(0f, 0.35f),
        )
        preferences.edit()
            .putFloat(KEY_TEXT_SCALE, safe.textScale)
            .putInt(KEY_FOREGROUND, safe.foregroundColor)
            .putInt(KEY_BACKGROUND, safe.backgroundColor)
            .putInt(KEY_WINDOW, safe.windowColor)
            .putString(KEY_EDGE_STYLE, safe.edgeStyle.name)
            .putInt(KEY_EDGE_COLOR, safe.edgeColor)
            .putFloat(KEY_BOTTOM_PADDING, safe.bottomPaddingFraction)
            .putBoolean(KEY_EMBEDDED_STYLES, safe.applyEmbeddedStyles)
            .putBoolean(KEY_EMBEDDED_SIZES, safe.applyEmbeddedFontSizes)
            .apply()
        _style.value = safe
    }

    private fun loadStyle(): SubtitleStyleState {
        val defaults = SubtitleStyleState()
        return SubtitleStyleState(
            textScale = preferences.getFloat(KEY_TEXT_SCALE, defaults.textScale).coerceIn(0.5f, 2f),
            foregroundColor = preferences.getInt(KEY_FOREGROUND, defaults.foregroundColor),
            backgroundColor = preferences.getInt(KEY_BACKGROUND, defaults.backgroundColor),
            windowColor = preferences.getInt(KEY_WINDOW, defaults.windowColor),
            edgeStyle = runCatching {
                SubtitleEdgeStyle.valueOf(preferences.getString(KEY_EDGE_STYLE, defaults.edgeStyle.name) ?: defaults.edgeStyle.name)
            }.getOrDefault(defaults.edgeStyle),
            edgeColor = preferences.getInt(KEY_EDGE_COLOR, defaults.edgeColor),
            bottomPaddingFraction = preferences.getFloat(KEY_BOTTOM_PADDING, defaults.bottomPaddingFraction).coerceIn(0f, 0.35f),
            applyEmbeddedStyles = preferences.getBoolean(KEY_EMBEDDED_STYLES, defaults.applyEmbeddedStyles),
            applyEmbeddedFontSizes = preferences.getBoolean(KEY_EMBEDDED_SIZES, defaults.applyEmbeddedFontSizes),
        )
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun inferLanguage(displayName: String): String? {
        val withoutExtension = displayName.substringBeforeLast('.', displayName)
        val token = withoutExtension.split('.', '_', '-', ' ').lastOrNull()?.lowercase(Locale.ROOT) ?: return null
        return token.takeIf { it.length in 2..3 && it.all(Char::isLetter) }
    }

    private fun associationPrefix(mediaId: String): String = "external.${sha256(mediaId)}."

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val PREFS_NAME = "subtitle_preferences_v1"
        const val KEY_TEXT_SCALE = "style.text_scale"
        const val KEY_FOREGROUND = "style.foreground"
        const val KEY_BACKGROUND = "style.background"
        const val KEY_WINDOW = "style.window"
        const val KEY_EDGE_STYLE = "style.edge_style"
        const val KEY_EDGE_COLOR = "style.edge_color"
        const val KEY_BOTTOM_PADDING = "style.bottom_padding"
        const val KEY_EMBEDDED_STYLES = "style.embedded_styles"
        const val KEY_EMBEDDED_SIZES = "style.embedded_sizes"
    }
}
