package com.zubaer.maxvideoplayer.feature.sleeptimer

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.feature.settings.SleepFadeDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step9SleepTimerInstrumentedTest {
    private class FakeClock(var value: Long = 10_000L) : MonotonicClock {
        override fun nowMs(): Long = value
    }

    @Test
    fun durationTimerUsesMonotonicClockFadesPlayerVolumeAndRestoresOnExpiry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = FakeClock()
        val repository = SleepTimerRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var player: ExoPlayer
        lateinit var controller: SleepTimerController

        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).build()
            player.volume = 0.8f
            player.playWhenReady = true
            controller = SleepTimerController(
                player = player,
                repository = repository,
                scope = scope,
                fadeDuration = { SleepFadeDuration.THIRTY_SECONDS },
                clock = clock,
            )
            assertTrue(controller.start(SleepTimerMode.Duration(60_000L)))
            assertTrue(repository.state.value.active)
            assertEquals(60_000L, repository.state.value.remainingMs)

            clock.value += 45_000L
            controller.evaluateNowForTest()
            assertTrue(repository.state.value.fading)
            assertTrue(player.volume in 0f..<0.8f)

            clock.value += 15_000L
            controller.evaluateNowForTest()
            assertFalse(repository.state.value.active)
            assertEquals(0.8f, player.volume, 0.001f)
            assertFalse(player.playWhenReady)

            controller.release()
            player.release()
        }
        scope.cancel()
    }

    @Test
    fun endOfQueueExpiresOnEndedAndInvalidDurationsAreRejected() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SleepTimerRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var player: ExoPlayer
        lateinit var controller: SleepTimerController

        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).build()
            controller = SleepTimerController(player, repository, scope, { SleepFadeDuration.OFF }, FakeClock())
            assertFalse(repository.startDuration(999L))
            assertFalse(repository.startDuration(SleepTimerRepository.MAX_DURATION_MS + 1L))
            assertTrue(repository.endOfQueue())
            assertTrue(repository.state.value.active)
            controller.onPlaybackStateChanged(Player.STATE_ENDED)
            assertFalse(repository.state.value.active)
            controller.release()
            player.release()
        }
        scope.cancel()
    }
}
