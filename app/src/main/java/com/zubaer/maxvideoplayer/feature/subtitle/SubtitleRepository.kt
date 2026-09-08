package com.zubaer.maxvideoplayer.feature.subtitle

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.zubaer.maxvideoplayer.core.database.MaxDatabase
import com.zubaer.maxvideoplayer.core.database.SubtitleAssociationEntity
import com.zubaer.maxvideoplayer.core.database.SubtitleMediaStateEntity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Subtitle preferences + durable media/subtitle relationships.
 *
 * Global visual preferences remain lightweight SharedPreferences state. Production relational
 * associations live in Room v3 and are mirrored into a small in-memory cache because MediaItem
 * construction and Media3 track publication are synchronous. Actual subtitle file probing and SAF
 * sidecar traversal are always dispatched off the main thread.
 */
class SubtitleRepository(
    context: Context,
    private val database: MaxDatabase? = null,
) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistenceMutex = Mutex()

    private val _style = MutableStateFlow(loadStyle())
    val style: StateFlow<SubtitleStyleState> = _style.asStateFlow()

    private val _preferences = MutableStateFlow(loadPreferences())
    val subtitlePreferences: StateFlow<SubtitlePreferenceState> = _preferences.asStateFlow()

    private val associations = ConcurrentHashMap<String, List<ExternalSubtitleAttachment>>()
    private val mediaDelayMs = ConcurrentHashMap<String, Long>()
    private val selectedExternalIds = ConcurrentHashMap<String, String?>()
    private val recoverableErrors = ConcurrentHashMap<String, String>()

    init {
        hydrateRoomCache()
    }

    fun describe(uri: Uri): SubtitleFileDescriptor? {
        val displayName = queryDisplayName(uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "External subtitle"
        val providerMime = resolver.getType(uri)
        val mimeType = SubtitleFormatPolicy.resolveMimeType(displayName, providerMime) ?: return null
        val format = SubtitleFormatPolicy.resolveFormat(displayName, providerMime) ?: return null
        return SubtitleFileDescriptor(
            uri = uri.toString(),
            displayName = displayName,
            mimeType = mimeType,
            format = format,
            encoding = SubtitleEncoding.AUTO,
            sizeBytes = querySize(uri),
        )
    }

    suspend fun describeAsync(
        uri: Uri,
        sourceType: SubtitleSourceType = SubtitleSourceType.SIDELOADED_FILE,
    ): SubtitleFileDescriptor? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "External subtitle"
        val providerMime = resolver.getType(uri)
        val size = querySize(uri)
        if (size != null && size > MAX_SUBTITLE_BYTES) return@withContext null
        val prefix = readPrefix(uri, MAX_PREFIX_BYTES)
        val mimeType = SubtitleFormatPolicy.resolveMimeType(displayName, providerMime, prefix) ?: return@withContext null
        val format = SubtitleFormatPolicy.resolveFormat(displayName, providerMime, prefix) ?: return@withContext null
        SubtitleFileDescriptor(
            uri = uri.toString(),
            displayName = displayName,
            mimeType = mimeType,
            format = format,
            encoding = prefix?.let(SubtitleEncodingPolicy::detect) ?: SubtitleEncoding.AUTO,
            sizeBytes = size,
            sourceType = sourceType,
        )
    }

    fun describeNetworkUrl(rawUrl: String): SubtitleFileDescriptor? {
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        if (uri.host.isNullOrBlank()) return null
        val displayName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Network subtitle"
        val mimeType = SubtitleFormatPolicy.resolveMimeType(displayName, null) ?: return null
        val format = SubtitleFormatPolicy.resolveFormat(displayName, null) ?: return null
        return SubtitleFileDescriptor(
            uri = uri.toString(),
            displayName = displayName,
            mimeType = mimeType,
            format = format,
            encoding = SubtitleEncoding.AUTO,
            sourceType = SubtitleSourceType.NETWORK_URL,
        )
    }

    fun persistReadPermission(uri: Uri): Boolean = runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)

    fun canOpen(uri: Uri): Boolean = probeAvailability(uri) == SubtitleAvailability.AVAILABLE

    suspend fun probeDescriptor(uri: Uri): SubtitleAvailability = withContext(Dispatchers.IO) {
        probeAvailability(uri)
    }

    fun saveExternalAttachment(
        mediaId: String,
        descriptor: SubtitleFileDescriptor,
        preferred: Boolean = true,
    ): ExternalSubtitleAttachment {
        val id = associationId(mediaId, descriptor.uri)
        val inferred = SubtitleMatcher.detectLanguageSuffix(descriptor.displayName.substringBeforeLast('.', descriptor.displayName))
            ?: inferLanguage(descriptor.displayName)
        val attachment = ExternalSubtitleAttachment(
            id = id,
            mediaId = mediaId,
            uri = descriptor.uri,
            label = descriptor.displayName,
            language = inferred,
            mimeType = descriptor.mimeType,
            format = descriptor.format,
            encoding = descriptor.encoding,
            sourceType = descriptor.sourceType,
            isPreferred = preferred,
            availability = probeAvailability(Uri.parse(descriptor.uri)),
            delayMs = if (preferred) subtitleDelayFor(mediaId) else 0L,
        )

        val current = associations[mediaId].orEmpty().filterNot { it.id == id }
        associations[mediaId] = if (preferred) {
            current.map { it.copy(isPreferred = false) } + attachment
        } else {
            current + attachment
        }
        if (preferred) selectedExternalIds[mediaId] = id
        recoverableErrors.remove(mediaId)
        persistLegacyAttachment(mediaId, attachment)
        persistMediaSnapshotAsync(mediaId)
        return attachment
    }

    fun externalAttachmentsFor(mediaId: String): List<ExternalSubtitleAttachment> =
        associations[mediaId].orEmpty().ifEmpty {
            legacyAttachmentFor(mediaId)?.let(::listOf).orEmpty()
        }.sortedWith(compareByDescending<ExternalSubtitleAttachment> { it.isPreferred }.thenBy { it.label.lowercase() })

    fun externalAttachmentFor(mediaId: String): ExternalSubtitleAttachment? =
        externalAttachmentsFor(mediaId).firstOrNull { it.isPreferred }
            ?: externalAttachmentsFor(mediaId).firstOrNull()

    fun externalAttachmentById(mediaId: String, attachmentId: String): ExternalSubtitleAttachment? =
        externalAttachmentsFor(mediaId).firstOrNull { it.id == attachmentId }

    fun selectedExternalAttachmentId(mediaId: String): String? =
        selectedExternalIds[mediaId] ?: externalAttachmentFor(mediaId)?.takeIf { it.isPreferred }?.id

    fun selectExternalAttachment(mediaId: String, attachmentId: String?) {
        val existing = associations[mediaId].orEmpty()
        if (attachmentId == null) {
            selectedExternalIds.remove(mediaId)
            associations[mediaId] = existing.map { it.copy(isPreferred = false) }
            persistMediaSnapshotAsync(mediaId)
            return
        }
        val selected = existing.firstOrNull { it.id == attachmentId } ?: return
        associations[mediaId] = existing.map { it.copy(isPreferred = it.id == attachmentId) }
        selectedExternalIds[mediaId] = attachmentId
        mediaDelayMs[mediaId] = SubtitleTimingPolicy.clamp(selected.delayMs)
        persistMediaSnapshotAsync(mediaId)
        persistLegacyAttachment(mediaId, selected.copy(isPreferred = true))
    }

    fun setExternalEncoding(mediaId: String, attachmentId: String, encoding: SubtitleEncoding): Boolean {
        val existing = associations[mediaId].orEmpty()
        if (existing.none { it.id == attachmentId }) return false
        associations[mediaId] = existing.map {
            if (it.id == attachmentId) it.copy(encoding = encoding, availability = SubtitleAvailability.UNKNOWN) else it
        }
        recoverableErrors.remove(mediaId)
        persistMediaSnapshotAsync(mediaId)
        return true
    }

    suspend fun relinkExternalAttachment(
        mediaId: String,
        attachmentId: String,
        descriptor: SubtitleFileDescriptor,
    ): ExternalSubtitleAttachment? = withContext(Dispatchers.IO) {
        val old = externalAttachmentById(mediaId, attachmentId) ?: return@withContext null
        val wasSelected = selectedExternalAttachmentId(mediaId) == attachmentId
        removeExternalAttachment(mediaId, attachmentId)
        val saved = saveExternalAttachment(mediaId, descriptor, preferred = old.isPreferred || wasSelected)
        val restored = saved.copy(delayMs = old.delayMs, encoding = old.encoding)
        associations[mediaId] = associations[mediaId].orEmpty().map { if (it.id == saved.id) restored else it }
        if (wasSelected) selectExternalAttachment(mediaId, restored.id)
        recoverableErrors.remove(mediaId)
        persistMediaSnapshotNow(mediaId)
        restored
    }

    fun removeExternalAttachment(mediaId: String, attachmentId: String) {
        val remaining = associations[mediaId].orEmpty().filterNot { it.id == attachmentId }
        val wasSelected = selectedExternalIds[mediaId] == attachmentId
        val promoted = if (wasSelected && remaining.isNotEmpty()) {
            val first = remaining.first()
            remaining.map { it.copy(isPreferred = it.id == first.id) }
        } else remaining
        if (promoted.isEmpty()) associations.remove(mediaId) else associations[mediaId] = promoted
        if (wasSelected) {
            val next = promoted.firstOrNull { it.isPreferred }
            if (next == null) selectedExternalIds.remove(mediaId) else selectedExternalIds[mediaId] = next.id
        }
        persistMediaSnapshotAsync(mediaId)
        if (promoted.isEmpty()) clearLegacyAttachment(mediaId) else promoted.firstOrNull { it.isPreferred }?.let { persistLegacyAttachment(mediaId, it) }
        if (promoted.isEmpty()) recoverableErrors.remove(mediaId)
    }

    fun clearExternalAttachment(mediaId: String) {
        associations.remove(mediaId)
        selectedExternalIds.remove(mediaId)
        recoverableErrors.remove(mediaId)
        clearLegacyAttachment(mediaId)
        persistMediaSnapshotAsync(mediaId)
    }

    suspend fun refreshAvailability(mediaId: String): Boolean = withContext(Dispatchers.IO) {
        val existing = externalAttachmentsFor(mediaId)
        if (existing.isEmpty()) return@withContext false
        var changed = false
        val refreshed = existing.map { attachment ->
            val next = when (attachment.availability) {
                SubtitleAvailability.UNSUPPORTED, SubtitleAvailability.MALFORMED -> attachment.availability
                else -> probeAvailability(Uri.parse(attachment.uri))
            }
            if (next != attachment.availability) changed = true
            attachment.copy(availability = next)
        }
        associations[mediaId] = refreshed
        val unavailable = refreshed.firstOrNull { it.availability != SubtitleAvailability.AVAILABLE }
        when {
            unavailable == null -> recoverableErrors.remove(mediaId)
            unavailable.availability == SubtitleAvailability.PERMISSION_LOST -> recoverableErrors[mediaId] =
                "Subtitle permission was lost. Relink the subtitle file or remove the association."
            unavailable.availability == SubtitleAvailability.MISSING -> recoverableErrors[mediaId] =
                "Subtitle file is missing. Video playback can continue; relink or remove the subtitle."
        }
        if (changed) persistMediaSnapshotNow(mediaId)
        changed
    }

    fun reportExternalParseFailure(mediaId: String, attachmentId: String?, message: String?) {
        val safeMessage = "Subtitle could not be parsed${message?.takeIf { it.isNotBlank() }?.let { ": ${it.take(160)}" }.orEmpty()}. Change encoding, relink, remove it, or try another subtitle."
        recoverableErrors[mediaId] = safeMessage
        if (attachmentId == null) return
        associations[mediaId] = associations[mediaId].orEmpty().map {
            if (it.id == attachmentId) it.copy(availability = SubtitleAvailability.MALFORMED) else it
        }
        persistMediaSnapshotAsync(mediaId)
    }

    fun recoverableErrorFor(mediaId: String): String? = recoverableErrors[mediaId]

    fun subtitleDelayFor(mediaId: String): Long = SubtitleTimingPolicy.clamp(mediaDelayMs[mediaId] ?: 0L)

    fun setSubtitleDelay(mediaId: String, delayMs: Long): Long {
        val safe = SubtitleTimingPolicy.clamp(delayMs)
        mediaDelayMs[mediaId] = safe
        val selectedId = selectedExternalAttachmentId(mediaId)
        if (selectedId != null) {
            associations[mediaId] = associations[mediaId].orEmpty().map {
                if (it.id == selectedId) it.copy(delayMs = safe) else it
            }
        }
        persistMediaSnapshotAsync(mediaId)
        return safe
    }

    fun setAutoLoadMatching(enabled: Boolean) = updatePreferences { it.copy(autoLoadMatching = enabled) }

    fun setPreferredLanguages(languages: List<String>) {
        val normalized = languages.mapNotNull(SubtitleMatcher::canonicalLanguage).distinct().take(MAX_PREFERRED_LANGUAGES)
        updatePreferences { it.copy(preferredLanguages = normalized) }
    }

    fun setDefaultEncoding(encoding: SubtitleEncoding) = updatePreferences { it.copy(defaultEncoding = encoding) }

    suspend fun discoverMatchingSidecars(media: AppMedia): List<ExternalSubtitleAttachment> = withContext(Dispatchers.IO) {
        if (!_preferences.value.autoLoadMatching || media.sourceType != MediaSourceType.SAF) return@withContext emptyList()
        val db = database ?: return@withContext emptyList()
        val sourceId = media.sourceId ?: return@withContext emptyList()
        val folderKey = media.folderKey ?: return@withContext emptyList()
        val fileName = media.fileName ?: return@withContext emptyList()
        val source = db.librarySourceDao().get(sourceId) ?: return@withContext emptyList()
        val parentDocumentId = folderKey.removePrefix("saf:$sourceId:").takeIf { it != folderKey && it.isNotBlank() }
            ?: return@withContext emptyList()
        val treeUri = runCatching { Uri.parse(source.uri) }.getOrNull() ?: return@withContext emptyList()
        val childrenUri = runCatching { DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId) }.getOrNull()
            ?: return@withContext emptyList()

        data class Candidate(val documentId: String, val name: String, val mime: String?, val size: Long?)
        val candidates = mutableListOf<Candidate>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                val match = SubtitleMatcher.score(fileName, name) ?: continue
                if (match.score < AUTOLOAD_SCORE_THRESHOLD) continue
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                if (size != null && size > MAX_SUBTITLE_BYTES) continue
                candidates += Candidate(
                    documentId = cursor.getString(idIndex),
                    name = name,
                    mime = cursor.getString(mimeIndex),
                    size = size,
                )
            }
        }

        val existingUris = externalAttachmentsFor(media.stableId).mapTo(mutableSetOf()) { it.uri }
        val preferredLanguages = _preferences.value.preferredLanguages
        val prepared = candidates.mapNotNull { candidate ->
            val documentUri = runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, candidate.documentId) }.getOrNull()
                ?: return@mapNotNull null
            if (documentUri.toString() in existingUris) return@mapNotNull null
            val descriptor = describeAsync(documentUri, SubtitleSourceType.AUTO_DISCOVERED) ?: return@mapNotNull null
            val language = SubtitleMatcher.detectLanguageSuffix(candidate.name.substringBeforeLast('.', candidate.name))
            Triple(descriptor, language, SubtitleMatcher.score(fileName, candidate.name)?.score ?: 0)
        }.sortedWith(
            compareBy<Triple<SubtitleFileDescriptor, String?, Int>> { entry ->
                val rank = preferredLanguages.indexOf(SubtitleMatcher.canonicalLanguage(entry.second))
                if (rank < 0) Int.MAX_VALUE else rank
            }.thenByDescending { it.third }.thenBy { it.first.displayName.lowercase() },
        )

        val created = mutableListOf<ExternalSubtitleAttachment>()
        prepared.forEachIndexed { index, entry ->
            val shouldPrefer = externalAttachmentsFor(media.stableId).isEmpty() && index == 0
            created += saveExternalAttachment(media.stableId, entry.first, preferred = shouldPrefer)
        }
        created
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
    fun setUseSystemCaptionStyle(value: Boolean) = updateStyle { it.copy(useSystemCaptionStyle = value) }

    fun resetStyle() = persistStyle(SubtitleStyleState())

    private fun hydrateRoomCache() {
        val db = database ?: return
        runCatching {
            runBlocking(Dispatchers.IO) {
                db.subtitleDao().allAssociations().groupBy { it.stableMediaId }.forEach { (mediaId, rows) ->
                    associations[mediaId] = rows.map(::fromEntity)
                }
                db.subtitleDao().allMediaState().forEach { row ->
                    mediaDelayMs[row.stableMediaId] = SubtitleTimingPolicy.clamp(row.delayMs)
                    row.selectedExternalId?.let { selectedExternalIds[row.stableMediaId] = it }
                }
            }
        }
    }

    /**
     * Reconciles one media item's complete subtitle state from the in-memory source of truth.
     * Every asynchronous persistence request reads the latest snapshot only after acquiring the
     * mutex, so an older coroutine that happens to finish later cannot overwrite a newer selection.
     */
    private fun persistMediaSnapshotAsync(mediaId: String) {
        val db = database ?: return
        ioScope.launch { persistMediaSnapshot(db, mediaId) }
    }

    private suspend fun persistMediaSnapshotNow(mediaId: String) {
        val db = database ?: return
        persistMediaSnapshot(db, mediaId)
    }

    private suspend fun persistMediaSnapshot(db: MaxDatabase, mediaId: String) {
        persistenceMutex.withLock {
            db.withTransaction {
                val current = associations[mediaId].orEmpty()
                val selectedId = selectedExternalIds[mediaId]
                    ?.takeIf { candidate -> current.any { it.id == candidate } }
                    ?: current.firstOrNull { it.isPreferred }?.id
                val normalized = current.map { attachment ->
                    attachment.copy(isPreferred = attachment.id == selectedId)
                }

                if (selectedId == null) {
                    selectedExternalIds.remove(mediaId)
                } else {
                    selectedExternalIds[mediaId] = selectedId
                }
                if (normalized.isEmpty()) {
                    associations.remove(mediaId)
                } else {
                    associations[mediaId] = normalized
                }

                val liveIds = normalized.mapTo(hashSetOf()) { it.id }
                db.subtitleDao().associationsForMedia(mediaId).forEach { persisted ->
                    if (persisted.id !in liveIds) db.subtitleDao().deleteAssociation(persisted.id)
                }
                normalized.forEach { attachment ->
                    db.subtitleDao().upsertAssociation(attachment.toEntity())
                }
                if (selectedId != null) db.subtitleDao().setPreferred(mediaId, selectedId)
                db.subtitleDao().upsertMediaState(
                    SubtitleMediaStateEntity(
                        stableMediaId = mediaId,
                        selectedExternalId = selectedId,
                        delayMs = subtitleDelayFor(mediaId),
                        updatedAtMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun fromEntity(entity: SubtitleAssociationEntity): ExternalSubtitleAttachment {
        val storedAvailability = enumOrDefault(entity.availability, SubtitleAvailability.UNKNOWN)
        return ExternalSubtitleAttachment(
            id = entity.id,
            mediaId = entity.stableMediaId,
            uri = entity.subtitleUri,
            label = entity.displayName,
            language = entity.language,
            mimeType = entity.mimeType,
            format = enumOrDefault(entity.format, SubtitleFormat.UNKNOWN),
            encoding = enumOrDefault(entity.encoding, SubtitleEncoding.AUTO),
            sourceType = SubtitleSourceType.SIDELOADED_FILE,
            isPreferred = entity.isPreferred,
            // A persisted AVAILABLE result is only a previous-session observation. Re-probe it
            // before Media3 attaches the source so stale SAF permissions never break the video.
            availability = if (storedAvailability == SubtitleAvailability.AVAILABLE) SubtitleAvailability.UNKNOWN else storedAvailability,
            delayMs = SubtitleTimingPolicy.clamp(entity.delayMs),
        )
    }

    private fun ExternalSubtitleAttachment.toEntity(): SubtitleAssociationEntity = SubtitleAssociationEntity(
        id = id,
        stableMediaId = mediaId,
        subtitleUri = uri,
        displayName = label,
        language = language,
        mimeType = mimeType,
        format = format.name,
        encoding = encoding.name,
        addedAtMs = System.currentTimeMillis(),
        isPreferred = isPreferred,
        availability = availability.name,
        delayMs = delayMs,
    )

    private fun updateStyle(transform: (SubtitleStyleState) -> SubtitleStyleState) = persistStyle(transform(_style.value))

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
            .putBoolean(KEY_SYSTEM_STYLE, safe.useSystemCaptionStyle)
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
            edgeStyle = enumOrDefault(preferences.getString(KEY_EDGE_STYLE, null), defaults.edgeStyle),
            edgeColor = preferences.getInt(KEY_EDGE_COLOR, defaults.edgeColor),
            bottomPaddingFraction = preferences.getFloat(KEY_BOTTOM_PADDING, defaults.bottomPaddingFraction).coerceIn(0f, 0.35f),
            applyEmbeddedStyles = preferences.getBoolean(KEY_EMBEDDED_STYLES, defaults.applyEmbeddedStyles),
            applyEmbeddedFontSizes = preferences.getBoolean(KEY_EMBEDDED_SIZES, defaults.applyEmbeddedFontSizes),
            useSystemCaptionStyle = preferences.getBoolean(KEY_SYSTEM_STYLE, defaults.useSystemCaptionStyle),
        )
    }

    private fun updatePreferences(transform: (SubtitlePreferenceState) -> SubtitlePreferenceState) {
        val safe = transform(_preferences.value)
        preferences.edit()
            .putBoolean(KEY_AUTOLOAD, safe.autoLoadMatching)
            .putString(KEY_LANGUAGES, safe.preferredLanguages.joinToString(","))
            .putString(KEY_DEFAULT_ENCODING, safe.defaultEncoding.name)
            .apply()
        _preferences.value = safe
    }

    private fun loadPreferences(): SubtitlePreferenceState {
        val defaults = SubtitlePreferenceState()
        val languages = preferences.getString(KEY_LANGUAGES, null)
            ?.split(',')
            ?.mapNotNull(SubtitleMatcher::canonicalLanguage)
            ?.distinct()
            ?.take(MAX_PREFERRED_LANGUAGES)
            .orEmpty()
        return SubtitlePreferenceState(
            autoLoadMatching = preferences.getBoolean(KEY_AUTOLOAD, defaults.autoLoadMatching),
            preferredLanguages = languages.ifEmpty { defaults.preferredLanguages },
            defaultEncoding = enumOrDefault(preferences.getString(KEY_DEFAULT_ENCODING, null), defaults.defaultEncoding),
        )
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun querySize(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }.getOrNull()

    private fun readPrefix(uri: Uri, maxBytes: Int): ByteArray? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(maxBytes)
            val count = input.read(buffer)
            if (count <= 0) ByteArray(0) else buffer.copyOf(count)
        }
    }.getOrNull()

    private fun probeAvailability(uri: Uri): SubtitleAvailability = try {
        if (uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https")) return SubtitleAvailability.AVAILABLE
        resolver.openAssetFileDescriptor(uri, "r")?.use { SubtitleAvailability.AVAILABLE }
            ?: SubtitleAvailability.MISSING
    } catch (_: SecurityException) {
        SubtitleAvailability.PERMISSION_LOST
    } catch (_: FileNotFoundException) {
        SubtitleAvailability.MISSING
    } catch (_: Exception) {
        SubtitleAvailability.UNKNOWN
    }

    private fun inferLanguage(displayName: String): String? {
        val withoutExtension = displayName.substringBeforeLast('.', displayName)
        val token = withoutExtension.split('.', '_', '-', ' ').lastOrNull()?.lowercase(Locale.ROOT) ?: return null
        return SubtitleMatcher.canonicalLanguage(token)
    }

    private fun associationId(mediaId: String, uri: String): String = "sub_${sha256("$mediaId|$uri").take(24)}"

    private fun persistLegacyAttachment(mediaId: String, attachment: ExternalSubtitleAttachment) {
        val key = associationPrefix(mediaId)
        preferences.edit()
            .putString("${key}uri", attachment.uri)
            .putString("${key}label", attachment.label)
            .putString("${key}language", attachment.language)
            .putString("${key}mime", attachment.mimeType)
            .apply()
    }

    private fun legacyAttachmentFor(mediaId: String): ExternalSubtitleAttachment? {
        val key = associationPrefix(mediaId)
        val uri = preferences.getString("${key}uri", null) ?: return null
        val label = preferences.getString("${key}label", null) ?: "External subtitle"
        val mime = preferences.getString("${key}mime", null) ?: return null
        return ExternalSubtitleAttachment(
            id = associationId(mediaId, uri),
            mediaId = mediaId,
            uri = uri,
            label = label,
            language = preferences.getString("${key}language", null),
            mimeType = mime,
            format = SubtitleFormatPolicy.resolveFormat(label, mime) ?: SubtitleFormat.UNKNOWN,
            isPreferred = true,
            availability = SubtitleAvailability.UNKNOWN,
        )
    }

    private fun clearLegacyAttachment(mediaId: String) {
        val key = associationPrefix(mediaId)
        preferences.edit()
            .remove("${key}uri")
            .remove("${key}label")
            .remove("${key}language")
            .remove("${key}mime")
            .apply()
    }

    private fun associationPrefix(mediaId: String): String = "external.${sha256(mediaId)}."

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        runCatching { value?.let { enumValueOf<T>(it) } }.getOrNull() ?: default

    private companion object {
        const val PREFS_NAME = "subtitle_preferences_v1"
        const val MAX_SUBTITLE_BYTES = 16L * 1024L * 1024L
        const val MAX_PREFIX_BYTES = 64 * 1024
        const val MAX_PREFERRED_LANGUAGES = 8
        const val AUTOLOAD_SCORE_THRESHOLD = 80
        const val KEY_TEXT_SCALE = "style.text_scale"
        const val KEY_FOREGROUND = "style.foreground"
        const val KEY_BACKGROUND = "style.background"
        const val KEY_WINDOW = "style.window"
        const val KEY_EDGE_STYLE = "style.edge_style"
        const val KEY_EDGE_COLOR = "style.edge_color"
        const val KEY_BOTTOM_PADDING = "style.bottom_padding"
        const val KEY_EMBEDDED_STYLES = "style.embedded_styles"
        const val KEY_EMBEDDED_SIZES = "style.embedded_sizes"
        const val KEY_SYSTEM_STYLE = "style.system_caption"
        const val KEY_AUTOLOAD = "autoload.enabled"
        const val KEY_LANGUAGES = "languages.preferred"
        const val KEY_DEFAULT_ENCODING = "encoding.default"
    }
}
