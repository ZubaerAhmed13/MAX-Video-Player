package com.zubaer.maxvideoplayer.core.model

enum class ResumeAction { START_OVER, OFFER_RESUME, COMPLETED }

data class ResumeDecision(val action: ResumeAction, val positionMs: Long)

object ResumePolicy {
    const val MIN_RESUME_POSITION_MS = 10_000L
    const val END_MARGIN_MS = 30_000L
    const val COMPLETED_FRACTION = 0.97

    fun decide(positionMs: Long, durationMs: Long): ResumeDecision {
        val safePosition = positionMs.coerceAtLeast(0L)
        if (durationMs <= 0L || safePosition < MIN_RESUME_POSITION_MS) {
            return ResumeDecision(ResumeAction.START_OVER, 0L)
        }
        val clamped = safePosition.coerceAtMost(durationMs)
        val remaining = durationMs - clamped
        val fraction = clamped.toDouble() / durationMs.toDouble()
        return if (remaining <= END_MARGIN_MS || fraction >= COMPLETED_FRACTION) {
            ResumeDecision(ResumeAction.COMPLETED, 0L)
        } else {
            ResumeDecision(ResumeAction.OFFER_RESUME, clamped)
        }
    }

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean =
        decide(positionMs, durationMs).action == ResumeAction.COMPLETED
}
