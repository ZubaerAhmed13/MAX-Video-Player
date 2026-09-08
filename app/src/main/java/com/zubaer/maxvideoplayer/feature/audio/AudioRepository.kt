package com.zubaer.maxvideoplayer.feature.audio

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.zubaer.maxvideoplayer.core.database.AudioAssociationEntity
import com.zubaer.maxvideoplayer.core.database.AudioMediaStateEntity
import com.zubaer.maxvideoplayer.core.database.MaxDatabase
import com.zubaer.maxvideoplayer.feature.network.model.NetworkUriPolicy
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
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class AudioRepository(
    context: Context,
    private val database: MaxDatabase? = null,
) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistenceMutex = Mutex()

    private val associations = ConcurrentHashMap<String, List<ExternalAudioInfo>>()
    private val mediaState = ConcurrentHashMap<String, AudioMediaStateEntity>()
    @Volatile private var activeMediaId: String? = null

    private val _state = MutableStateFlow(loadGlobalState())
    val state: StateFlow<AudioEngineState> = _state.asStateFlow()
    val realtimeParameters = AtomicReference(toDspParameters(_state.value, _state.value.effectiveAudioDelayMs))

    init { hydrateRoomCache() }

    fun activateMedia(mediaId: String?) {
        activeMediaId = mediaId
        val current = _state.value
        val durable = mediaId?.let(mediaState::get)
        val external = mediaId?.let { externalFor(it) }.orEmpty()
        _state.value = current.copy(
            selectionMode = durable?.selectionMode?.let { runCatching { AudioSelectionMode.valueOf(it) }.getOrNull() } ?: AudioSelectionMode.AUTO,
            selectedExternalId = durable?.selectedExternalId,
            externalAudio = external,
            audioDelayMs = AudioPolicy.clampDelay(durable?.delayMs ?: 0L),
            recoverableError = external.firstOrNull { it.preferred && it.availability != AudioAvailability.AVAILABLE }?.let(::availabilityMessage),
        )
        publishRealtime()
    }

    fun updateTracks(tracks: List<AudioTrackInfo>) {
        val selected = tracks.firstOrNull { it.selected }?.key
        _state.value = _state.value.copy(tracks = tracks, selectedTrackKey = selected)
    }

    fun preferredLanguages(): List<String> = _state.value.preferredLanguages

    fun setPreferredLanguages(values: List<String>) {
        val normalized = values.mapNotNull(::canonicalLanguage).distinct().take(8)
        val safe = normalized.ifEmpty { listOf("en") }
        prefs.edit().putString(KEY_LANGUAGES, safe.joinToString(",")).apply()
        _state.value = _state.value.copy(preferredLanguages = safe)
    }

    fun selectedTrackDescriptor(mediaId: String): AudioTrackDescriptor? {
        val row = mediaState[mediaId] ?: return null
        if (row.selectionMode != AudioSelectionMode.MANUAL.name || row.selectedExternalId != null) return null
        return AudioTrackDescriptor(row.selectedLanguage, row.selectedLabel, row.selectedMimeType, row.selectedChannelCount)
    }

    fun rememberManualTrack(mediaId: String, descriptor: AudioTrackDescriptor) {
        val previous = mediaState[mediaId]
        val next = AudioMediaStateEntity(
            stableMediaId = mediaId,
            selectedExternalId = null,
            selectionMode = AudioSelectionMode.MANUAL.name,
            selectedLanguage = descriptor.language,
            selectedLabel = descriptor.label,
            selectedMimeType = descriptor.mimeType,
            selectedChannelCount = descriptor.channelCount,
            delayMs = previous?.delayMs ?: 0L,
            updatedAtMs = System.currentTimeMillis(),
        )
        mediaState[mediaId] = next
        _state.value = _state.value.copy(selectionMode = AudioSelectionMode.MANUAL, selectedExternalId = null)
        persistMediaAsync(mediaId)
    }

    fun rememberAuto(mediaId: String) {
        val previous = mediaState[mediaId]
        val next = AudioMediaStateEntity(
            stableMediaId = mediaId,
            selectedExternalId = null,
            selectionMode = AudioSelectionMode.AUTO.name,
            selectedLanguage = null,
            selectedLabel = null,
            selectedMimeType = null,
            selectedChannelCount = null,
            delayMs = previous?.delayMs ?: 0L,
            updatedAtMs = System.currentTimeMillis(),
        )
        mediaState[mediaId] = next
        _state.value = _state.value.copy(selectionMode = AudioSelectionMode.AUTO, selectedExternalId = null)
        persistMediaAsync(mediaId)
    }

    fun describe(uri: Uri): ExternalAudioDescriptor? {
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "External audio"
        val mime = resolver.getType(uri)
        if (!isSupportedDescriptor(displayName, mime)) return null
        return ExternalAudioDescriptor(uri.toString(), displayName, mime)
    }

    fun describeNetworkUrl(rawUrl: String): ExternalAudioDescriptor? {
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        if (uri.host.isNullOrBlank()) return null
        if (NetworkUriPolicy.containsSensitiveMaterial(uri.toString())) return null
        val displayName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Network audio"
        if (!isSupportedDescriptor(displayName, null)) return null
        return ExternalAudioDescriptor(uri.toString(), displayName, mimeFromName(displayName))
    }

    fun persistReadPermission(uri: Uri): Boolean = runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)

    fun saveExternal(mediaId: String, descriptor: ExternalAudioDescriptor, preferred: Boolean = true): ExternalAudioInfo {
        val availability = probe(Uri.parse(descriptor.uri))
        val id = stableAssociationId(mediaId, descriptor.uri)
        val item = ExternalAudioInfo(
            id = id,
            stableMediaId = mediaId,
            uri = descriptor.uri,
            displayName = descriptor.displayName,
            language = inferLanguage(descriptor.displayName),
            mimeType = descriptor.mimeType,
            addedAtMs = System.currentTimeMillis(),
            preferred = preferred,
            availability = availability,
        )
        val current = associations[mediaId].orEmpty().filterNot { it.id == id }
        associations[mediaId] = if (preferred) current.map { it.copy(preferred = false) } + item else current + item
        if (preferred) selectExternal(mediaId, id) else persistMediaAsync(mediaId)
        if (activeMediaId == mediaId) activateMedia(mediaId)
        return item
    }

    fun relinkExternal(mediaId: String, oldId: String, descriptor: ExternalAudioDescriptor): ExternalAudioInfo? {
        val old = associations[mediaId].orEmpty().firstOrNull { it.id == oldId } ?: return null
        val wasSelected = mediaState[mediaId]?.selectedExternalId == oldId
        associations[mediaId] = associations[mediaId].orEmpty().filterNot { it.id == oldId }
        val replacement = saveExternal(mediaId, descriptor, preferred = old.preferred || wasSelected)
        if (wasSelected) selectExternal(mediaId, replacement.id)
        persistMediaAsync(mediaId)
        return replacement
    }

    fun externalFor(mediaId: String): List<ExternalAudioInfo> = associations[mediaId].orEmpty()
        .sortedWith(compareByDescending<ExternalAudioInfo> { it.preferred }.thenBy { it.displayName.lowercase() })

    fun selectedExternalFor(mediaId: String): ExternalAudioInfo? {
        val id = mediaState[mediaId]?.selectedExternalId
        return externalFor(mediaId).firstOrNull { it.id == id && it.availability == AudioAvailability.AVAILABLE }
    }

    fun selectExternal(mediaId: String, id: String) {
        val item = associations[mediaId].orEmpty().firstOrNull { it.id == id } ?: return
        associations[mediaId] = associations[mediaId].orEmpty().map { it.copy(preferred = it.id == id) }
        val previous = mediaState[mediaId]
        mediaState[mediaId] = AudioMediaStateEntity(
            stableMediaId = mediaId,
            selectedExternalId = id,
            selectionMode = AudioSelectionMode.MANUAL.name,
            selectedLanguage = item.language,
            selectedLabel = item.displayName,
            selectedMimeType = item.mimeType,
            selectedChannelCount = null,
            delayMs = previous?.delayMs ?: 0L,
            updatedAtMs = System.currentTimeMillis(),
        )
        if (activeMediaId == mediaId) activateMedia(mediaId)
        persistMediaAsync(mediaId)
    }

    fun removeExternal(mediaId: String, id: String) {
        val current = associations[mediaId].orEmpty()
        val wasSelected = mediaState[mediaId]?.selectedExternalId == id
        val remaining = current.filterNot { it.id == id }
        if (remaining.isEmpty()) associations.remove(mediaId) else associations[mediaId] = remaining
        if (wasSelected) rememberAuto(mediaId) else persistMediaAsync(mediaId)
        if (activeMediaId == mediaId) activateMedia(mediaId)
    }

    fun refreshExternalAvailability(mediaId: String) {
        ioScope.launch {
            val refreshed = associations[mediaId].orEmpty().map { it.copy(availability = probe(Uri.parse(it.uri))) }
            if (refreshed.isEmpty()) associations.remove(mediaId) else associations[mediaId] = refreshed
            persistMediaNow(mediaId)
            if (activeMediaId == mediaId) activateMedia(mediaId)
        }
    }

    fun setAudioDelay(mediaId: String, valueMs: Long): Long {
        val safe = AudioPolicy.clampDelay(valueMs)
        val old = mediaState[mediaId]
        mediaState[mediaId] = (old ?: emptyMediaState(mediaId)).copy(delayMs = safe, updatedAtMs = System.currentTimeMillis())
        if (activeMediaId == mediaId) {
            _state.value = _state.value.copy(audioDelayMs = safe)
            publishRealtime()
        }
        persistMediaAsync(mediaId)
        return safe
    }

    fun setEqualizerEnabled(enabled: Boolean) = updateGlobal(KEY_EQ_ENABLED, enabled) { it.copy(equalizerEnabled = enabled) }

    fun setPreset(preset: EqualizerPreset) {
        if (preset == EqualizerPreset.CUSTOM) return
        val bands = AudioPolicy.preset(preset)
        prefs.edit().putString(KEY_PRESET, preset.name).putString(KEY_BANDS, bands.joinToString(",")).apply()
        _state.value = _state.value.copy(equalizerPreset = preset, equalizerBandsDb = bands)
        publishRealtime()
    }

    fun setEqBand(index: Int, db: Float) {
        if (index !in EQ_FREQUENCIES_HZ.indices) return
        val bands = _state.value.equalizerBandsDb.toMutableList()
        bands[index] = AudioPolicy.clampBand(db)
        prefs.edit().putString(KEY_PRESET, EqualizerPreset.CUSTOM.name).putString(KEY_BANDS, bands.joinToString(",")).apply()
        _state.value = _state.value.copy(equalizerPreset = EqualizerPreset.CUSTOM, equalizerBandsDb = bands)
        publishRealtime()
    }

    fun setPreamp(db: Float) = updateGlobal(KEY_PREAMP, AudioPolicy.clampPreamp(db)) { it.copy(preampDb = AudioPolicy.clampPreamp(db)) }
    fun setBoost(db: Float) = updateGlobal(KEY_BOOST, AudioPolicy.clampBoost(db)) { it.copy(boostDb = AudioPolicy.clampBoost(db)) }
    fun setBalance(value: Float) = updateGlobal(KEY_BALANCE, AudioPolicy.clampBalance(value)) { it.copy(balance = AudioPolicy.clampBalance(value)) }
    fun setChannelMode(mode: AudioChannelMode) = updateGlobal(KEY_CHANNEL_MODE, mode.name) { it.copy(channelMode = mode) }

    fun setPitch(value: Float) {
        val safe = AudioPolicy.clampPitch(value)
        prefs.edit().putFloat(KEY_PITCH, safe).apply()
        _state.value = _state.value.copy(pitch = safe)
    }

    fun setAudioOnly(enabled: Boolean) { _state.value = _state.value.copy(audioOnlyMode = enabled) }

    fun setBackgroundMode(mode: BackgroundPlaybackMode) =
        updateGlobal(KEY_BACKGROUND_MODE, mode.name) { it.copy(backgroundMode = mode) }

    fun setDisableVideoInBackground(enabled: Boolean) =
        updateGlobal(KEY_DISABLE_VIDEO_BACKGROUND, enabled) { it.copy(disableVideoInBackground = enabled) }

    fun routeCompensationFor(type: AudioRouteType): Long = AudioPolicy.clampDelay(
        prefs.getLong(routeCompensationKey(type), 0L),
    )

    fun setRouteCompensation(type: AudioRouteType, valueMs: Long): Long {
        val safe = AudioPolicy.clampDelay(valueMs)
        prefs.edit().putLong(routeCompensationKey(type), safe).apply()
        if (_state.value.currentRoute.type == type) {
            _state.value = _state.value.copy(routeCompensationMs = safe)
            publishRealtime()
        }
        return safe
    }

    fun setRoute(route: AudioRouteInfo) {
        val compensation = routeCompensationFor(route.type)
        _state.value = _state.value.copy(currentRoute = route, routeCompensationMs = compensation)
        publishRealtime()
    }

    fun setDspPipelineInstalled(installed: Boolean) {
        _state.value = _state.value.copy(dspPipelineInstalled = installed)
    }

    fun setDspAvailability(available: Boolean, reason: String? = null) {
        _state.value = _state.value.copy(dspAvailable = available, dspBypassReason = if (available) null else reason)
    }

    private fun updateGlobal(key: String, value: Any, transform: (AudioEngineState) -> AudioEngineState) {
        val editor = prefs.edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Long -> editor.putLong(key, value)
            is String -> editor.putString(key, value)
            else -> return
        }
        editor.apply()
        _state.value = transform(_state.value)
        publishRealtime()
    }

    private fun publishRealtime() {
        val state = _state.value
        realtimeParameters.set(toDspParameters(state, state.effectiveAudioDelayMs))
    }

    private fun toDspParameters(state: AudioEngineState, delayMs: Long) = AudioDspParameters(
        equalizerEnabled = state.equalizerEnabled,
        equalizerBandsDb = state.equalizerBandsDb.map(AudioPolicy::clampBand),
        preampDb = AudioPolicy.clampPreamp(state.preampDb),
        boostDb = AudioPolicy.clampBoost(state.boostDb),
        channelMode = state.channelMode,
        balance = AudioPolicy.clampBalance(state.balance),
        delayMs = AudioPolicy.clampDelay(delayMs),
        revision = System.nanoTime(),
    )

    private fun loadGlobalState(): AudioEngineState {
        val bands = prefs.getString(KEY_BANDS, null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 10 }?.map(AudioPolicy::clampBand) ?: List(10) { 0f }
        val preset = runCatching { EqualizerPreset.valueOf(prefs.getString(KEY_PRESET, EqualizerPreset.FLAT.name)!!) }
            .getOrDefault(EqualizerPreset.FLAT)
        val channelMode = runCatching { AudioChannelMode.valueOf(prefs.getString(KEY_CHANNEL_MODE, AudioChannelMode.STEREO.name)!!) }
            .getOrDefault(AudioChannelMode.STEREO)
        val backgroundMode = runCatching {
            BackgroundPlaybackMode.valueOf(prefs.getString(KEY_BACKGROUND_MODE, BackgroundPlaybackMode.CONTINUE_AUDIO.name)!!)
        }.getOrDefault(BackgroundPlaybackMode.CONTINUE_AUDIO)
        val languages = prefs.getString(KEY_LANGUAGES, "en")!!.split(',').mapNotNull(::canonicalLanguage).distinct().ifEmpty { listOf("en") }
        val initialRoute = AudioRouteInfo()
        return AudioEngineState(
            preferredLanguages = languages,
            equalizerEnabled = prefs.getBoolean(KEY_EQ_ENABLED, false),
            equalizerBandsDb = bands,
            equalizerPreset = preset,
            preampDb = AudioPolicy.clampPreamp(prefs.getFloat(KEY_PREAMP, 0f)),
            boostDb = AudioPolicy.clampBoost(prefs.getFloat(KEY_BOOST, 0f)),
            channelMode = channelMode,
            balance = AudioPolicy.clampBalance(prefs.getFloat(KEY_BALANCE, 0f)),
            pitch = AudioPolicy.clampPitch(prefs.getFloat(KEY_PITCH, 1f)),
            backgroundMode = backgroundMode,
            disableVideoInBackground = prefs.getBoolean(KEY_DISABLE_VIDEO_BACKGROUND, false),
            currentRoute = initialRoute,
            routeCompensationMs = routeCompensationForInitial(initialRoute.type),
        )
    }

    private fun routeCompensationForInitial(type: AudioRouteType): Long =
        AudioPolicy.clampDelay(prefs.getLong(routeCompensationKey(type), 0L))

    private fun hydrateRoomCache() {
        val db = database ?: return
        runBlocking(Dispatchers.IO) {
            db.audioDao().allAssociations().groupBy { it.stableMediaId }.forEach { (mediaId, rows) ->
                associations[mediaId] = rows.map { it.toModel() }
            }
            db.audioDao().allMediaState().forEach { mediaState[it.stableMediaId] = it }
        }
    }

    private fun persistMediaAsync(mediaId: String) {
        val db = database ?: return
        ioScope.launch { persistMedia(db, mediaId) }
    }

    private suspend fun persistMediaNow(mediaId: String) {
        val db = database ?: return
        persistMedia(db, mediaId)
    }

    private suspend fun persistMedia(db: MaxDatabase, mediaId: String) = persistenceMutex.withLock {
        db.withTransaction {
            val live = associations[mediaId].orEmpty()
            val ids = live.mapTo(hashSetOf()) { it.id }
            db.audioDao().associationsForMedia(mediaId).forEach { if (it.id !in ids) db.audioDao().deleteAssociation(it.id) }
            live.forEach { db.audioDao().upsertAssociation(it.toEntity()) }
            db.audioDao().upsertMediaState(mediaState[mediaId] ?: emptyMediaState(mediaId))
        }
    }

    private fun emptyMediaState(mediaId: String) = AudioMediaStateEntity(
        stableMediaId = mediaId,
        selectedExternalId = null,
        selectionMode = AudioSelectionMode.AUTO.name,
        selectedLanguage = null,
        selectedLabel = null,
        selectedMimeType = null,
        selectedChannelCount = null,
        delayMs = 0L,
        updatedAtMs = System.currentTimeMillis(),
    )

    private fun AudioAssociationEntity.toModel() = ExternalAudioInfo(
        id, stableMediaId, audioUri, displayName, language, mimeType, addedAtMs, isPreferred,
        runCatching { AudioAvailability.valueOf(availability) }.getOrDefault(AudioAvailability.UNKNOWN),
    )

    private fun ExternalAudioInfo.toEntity() = AudioAssociationEntity(
        id, stableMediaId, uri, displayName, language, mimeType, addedAtMs, preferred, availability.name,
    )

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun probe(uri: Uri): AudioAvailability = try {
        if (uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https")) return AudioAvailability.AVAILABLE
        resolver.openAssetFileDescriptor(uri, "r")?.use { }
        AudioAvailability.AVAILABLE
    } catch (_: SecurityException) {
        AudioAvailability.PERMISSION_LOST
    } catch (_: java.io.FileNotFoundException) {
        AudioAvailability.MISSING
    } catch (_: Exception) {
        AudioAvailability.UNKNOWN
    }

    private fun isSupportedDescriptor(name: String, mime: String?): Boolean {
        if (mime?.startsWith("audio/") == true) return true
        return name.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("aac", "m4a", "mp3", "flac", "wav", "ogg", "opus")
    }

    private fun mimeFromName(name: String): String? = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "aac" -> "audio/aac"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        else -> null
    }

    private fun inferLanguage(name: String): String? {
        val stem = name.substringBeforeLast('.')
        val token = stem.substringAfterLast('.', "").substringAfterLast('_', "").substringAfterLast('-', "")
        return canonicalLanguage(token)
    }

    private fun canonicalLanguage(value: String?): String? {
        val raw = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        val locale = runCatching { Locale.forLanguageTag(raw) }.getOrNull()
        return locale?.language?.takeIf { it.isNotBlank() && it != "und" } ?: raw.takeIf { it.length in 2..3 }
    }

    private fun stableAssociationId(mediaId: String, uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$mediaId\u0000$uri".toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun availabilityMessage(info: ExternalAudioInfo): String = when (info.availability) {
        AudioAvailability.MISSING -> "External audio is missing. Relink or choose embedded audio."
        AudioAvailability.PERMISSION_LOST -> "External audio permission was lost. Relink or choose embedded audio."
        AudioAvailability.UNSUPPORTED -> "External audio format is unsupported by this device/player path."
        AudioAvailability.MALFORMED -> "External audio could not be parsed. Choose another track."
        else -> "External audio is currently unavailable."
    }

    private fun routeCompensationKey(type: AudioRouteType): String = "$KEY_ROUTE_COMPENSATION_PREFIX${type.name}"

    companion object {
        private const val PREFS_NAME = "professional_audio_v1"
        private const val KEY_LANGUAGES = "preferred_languages"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_BANDS = "eq_bands"
        private const val KEY_PRESET = "eq_preset"
        private const val KEY_PREAMP = "preamp_db"
        private const val KEY_BOOST = "boost_db"
        private const val KEY_CHANNEL_MODE = "channel_mode"
        private const val KEY_BALANCE = "balance"
        private const val KEY_PITCH = "pitch"
        private const val KEY_BACKGROUND_MODE = "background_mode"
        private const val KEY_DISABLE_VIDEO_BACKGROUND = "disable_video_background"
        private const val KEY_ROUTE_COMPENSATION_PREFIX = "route_compensation_ms_"
    }
}

data class ExternalAudioDescriptor(
    val uri: String,
    val displayName: String,
    val mimeType: String?,
)
