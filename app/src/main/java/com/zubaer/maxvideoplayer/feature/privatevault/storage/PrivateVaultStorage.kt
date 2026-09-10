package com.zubaer.maxvideoplayer.feature.privatevault.storage

import android.content.Context
import android.os.StatFs
import java.io.File

/** App-owned, backup-excluded storage for opaque encrypted containers. */
class PrivateVaultStorage(context: Context) {
    val directory: File = File(context.applicationContext.noBackupFilesDir, "private_vault").apply {
        if (!exists() && !mkdirs()) throw IllegalStateException("Private Vault storage could not be created")
        File(this, ".nomedia").let { marker -> if (!marker.exists()) runCatching { marker.createNewFile() } }
    }

    fun partialFile(vaultId: String): File = File(directory, "$vaultId.partial")
    fun containerFile(vaultId: String): File = File(directory, "$vaultId.maxvault")

    fun availableBytes(): Long = StatFs(directory.absolutePath).availableBytes

    fun commitPartial(vaultId: String): File {
        val partial = partialFile(vaultId)
        val final = containerFile(vaultId)
        check(partial.isFile) { "Partial vault container is missing" }
        check(!final.exists()) { "Vault destination already exists" }
        if (!partial.renameTo(final)) throw java.io.IOException("Could not atomically commit the vault container")
        return final
    }

    fun delete(vaultId: String): Boolean {
        var ok = true
        listOf(containerFile(vaultId), partialFile(vaultId)).forEach { file ->
            if (file.exists() && !file.delete()) ok = false
        }
        return ok
    }

    fun cleanupPartials() {
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(".partial") }?.forEach { runCatching { it.delete() } }
    }

    fun opaqueContainerIds(): Set<String> = directory.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.name.endsWith(".maxvault") }
        ?.map { it.name.removeSuffix(".maxvault") }
        ?.toSet()
        .orEmpty()
}
