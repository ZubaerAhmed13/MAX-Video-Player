package com.zubaer.maxvideoplayer.feature.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemovableVolumeState(
    val stableId: String,
    val description: String,
    val uuid: String?,
    val mounted: Boolean,
    val readOnly: Boolean,
)

/**
 * Observes removable/OTG volume state without requesting broad filesystem access. Actual browsing
 * and playback continue to use the Storage Access Framework and persisted content:// grants.
 */
class RemovableStorageController(context: Context) {
    private val appContext = context.applicationContext
    private val storageManager = appContext.getSystemService(StorageManager::class.java)
    private val _volumes = MutableStateFlow<List<RemovableVolumeState>>(emptyList())
    val volumes: StateFlow<List<RemovableVolumeState>> = _volumes.asStateFlow()
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    private val callback: StorageManager.StorageVolumeCallback? =
        if (Build.VERSION.SDK_INT >= 30) {
            object : StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: StorageVolume) = refresh()
            }
        } else {
            null
        }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        if (Build.VERSION.SDK_INT >= 30 && storageManager != null) {
            callback?.let { volumeCallback ->
                storageManager.registerStorageVolumeCallback(appContext.mainExecutor, volumeCallback)
            }
        } else {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                addDataScheme("file")
            }
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        refresh()
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        if (Build.VERSION.SDK_INT >= 30 && storageManager != null) {
            callback?.let { volumeCallback ->
                runCatching { storageManager.unregisterStorageVolumeCallback(volumeCallback) }
            }
        } else {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }

    fun refresh() {
        _volumes.value = if (Build.VERSION.SDK_INT >= 24) {
            storageManager?.storageVolumes.orEmpty()
                .asSequence()
                .filter { it.isRemovable && !it.isEmulated }
                .map { volume ->
                    val state = volume.state
                    RemovableVolumeState(
                        stableId = volume.uuid?.let { "usb:$it" }
                            ?: "usb:${volume.getDescription(appContext).lowercase().replace(Regex("[^a-z0-9]+"), "-")}",
                        description = volume.getDescription(appContext),
                        uuid = volume.uuid,
                        mounted = state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY,
                        readOnly = state == Environment.MEDIA_MOUNTED_READ_ONLY,
                    )
                }
                .sortedBy { it.description.lowercase() }
                .toList()
        } else emptyList()
    }
}

/** Pure policy used by tests and UI to identify non-primary ExternalStorageProvider tree grants. */
object RemovableTreePolicy {
    fun isLikelyRemovableTree(uriString: String): Boolean {
        if (!uriString.startsWith("content://com.android.externalstorage.documents/tree/", ignoreCase = true)) return false
        val encodedId = uriString.substringAfter("/tree/").substringBefore('/')
        val decoded = runCatching { java.net.URLDecoder.decode(encodedId, "UTF-8") }.getOrDefault(encodedId)
        val root = decoded.substringBefore(':').lowercase()
        return root.isNotBlank() && root != "primary" && root != "home"
    }
}
