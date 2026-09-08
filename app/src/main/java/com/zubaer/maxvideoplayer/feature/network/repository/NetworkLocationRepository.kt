package com.zubaer.maxvideoplayer.feature.network.repository

import com.zubaer.maxvideoplayer.core.database.NetworkLocationDao
import com.zubaer.maxvideoplayer.core.database.NetworkLocationEntity
import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.feature.network.security.CredentialVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NetworkLocationRepository(
    private val dao: NetworkLocationDao,
    private val vault: CredentialVault,
) {
    fun observeAll(): Flow<List<NetworkLocation>> = dao.observeAll().map { rows -> rows.map(NetworkLocationEntity::toModel) }

    suspend fun get(id: String): NetworkLocation? = dao.get(id)?.toModel()
    suspend fun all(): List<NetworkLocation> = dao.all().map(NetworkLocationEntity::toModel)

    suspend fun save(location: NetworkLocation, credential: NetworkCredential?, rememberCredential: Boolean): NetworkLocation {
        val old = dao.get(location.id)?.toModel()
        val previousSecret = old?.let { vault.get(it.credentialRef) }
        val mergedCredential = credential?.let { incoming ->
            if (previousSecret == null) incoming else incoming.copy(
                password = incoming.password.ifBlank { previousSecret.password },
                bearerToken = incoming.bearerToken.ifBlank { previousSecret.bearerToken },
                headers = if (incoming.headers.isEmpty()) previousSecret.headers else incoming.headers,
            )
        }
        val credentialRef = when {
            rememberCredential && mergedCredential != null && !mergedCredential.isEmpty() -> vault.save(mergedCredential, old?.credentialRef)
            rememberCredential -> old?.credentialRef
            else -> null
        }
        if (!rememberCredential && old?.credentialRef != null) vault.delete(old.credentialRef)
        val saved = location.copy(
            credentialRef = credentialRef,
            usernameHint = mergedCredential?.username?.takeIf { it.isNotBlank() } ?: old?.usernameHint,
            updatedAtMs = System.currentTimeMillis(),
        )
        dao.upsert(saved.toEntity())
        return saved
    }

    suspend fun markConnected(id: String) = dao.markConnected(id, System.currentTimeMillis())

    suspend fun forgetCredentials(id: String) {
        val row = dao.get(id) ?: return
        dao.forgetCredential(id, System.currentTimeMillis())
        row.credentialRef?.let { if (dao.countUsingCredential(it) == 0) vault.delete(it) }
    }

    suspend fun remove(id: String) {
        val row = dao.get(id) ?: return
        dao.delete(id)
        row.credentialRef?.let { if (dao.countUsingCredential(it) == 0) vault.delete(it) }
    }

    fun credential(location: NetworkLocation): NetworkCredential? = vault.get(location.credentialRef)

    private fun NetworkLocation.toEntity() = NetworkLocationEntity(
        id, displayName, protocol.name, host, port, basePath, credentialRef, usernameHint,
        useGuest, ftpPassiveMode, ftpSecurityAcknowledged, createdAtMs, updatedAtMs, lastConnectedAtMs,
    )

    private fun NetworkLocationEntity.toModel() = NetworkLocation(
        id = id,
        displayName = displayName,
        protocol = runCatching { NetworkProtocol.valueOf(protocol) }.getOrDefault(NetworkProtocol.HTTPS),
        host = host,
        port = port,
        basePath = basePath,
        credentialRef = credentialRef,
        usernameHint = usernameHint,
        useGuest = useGuest,
        ftpPassiveMode = ftpPassiveMode,
        ftpSecurityAcknowledged = ftpSecurityAcknowledged,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        lastConnectedAtMs = lastConnectedAtMs,
    )
}
