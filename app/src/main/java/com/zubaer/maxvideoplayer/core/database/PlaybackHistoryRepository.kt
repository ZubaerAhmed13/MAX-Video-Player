package com.zubaer.maxvideoplayer.core.database

import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.ResumeDecision
import com.zubaer.maxvideoplayer.core.model.ResumePolicy
import kotlinx.coroutines.flow.Flow

class PlaybackHistoryRepository(private val dao: MediaHistoryDao) {
    suspend fun get(stableId: String): MediaHistoryEntity? = dao.get(stableId)

    fun recent(limit: Int = 50): Flow<List<MediaHistoryEntity>> = dao.recent(limit)

    suspend fun record(media: AppMedia, positionMs: Long, durationMs: Long, completed: Boolean) {
        dao.upsert(
            MediaHistoryEntity(
                stableMediaId = media.stableId,
                uri = media.uri,
                title = media.title,
                mimeType = media.mimeType,
                sizeBytes = media.sizeBytes,
                width = media.width,
                height = media.height,
                lastPositionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                lastPlayedAtMs = System.currentTimeMillis(),
                completed = completed,
            )
        )
    }

    suspend fun recordSnapshot(
        stableId: String,
        uri: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        mimeType: String? = null,
    ) {
        val safeDuration = durationMs.coerceAtLeast(0L)
        val completed = safeDuration > 0 && ResumePolicy.isCompleted(positionMs, safeDuration)
        val existing = dao.get(stableId)
        dao.upsert(
            MediaHistoryEntity(
                stableMediaId = stableId,
                uri = uri,
                title = title,
                mimeType = mimeType ?: existing?.mimeType,
                sizeBytes = existing?.sizeBytes,
                width = existing?.width,
                height = existing?.height,
                lastPositionMs = if (completed) 0L else positionMs.coerceAtLeast(0L),
                durationMs = safeDuration,
                lastPlayedAtMs = System.currentTimeMillis(),
                completed = completed,
            )
        )
    }

    fun resumeDecision(entity: MediaHistoryEntity?): ResumeDecision =
        if (entity == null || entity.completed) ResumeDecision(com.zubaer.maxvideoplayer.core.model.ResumeAction.START_OVER, 0L)
        else ResumePolicy.decide(entity.lastPositionMs, entity.durationMs)
}
