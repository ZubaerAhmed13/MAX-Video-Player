package com.zubaer.maxvideoplayer.feature.output

import android.view.Display
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step8ExternalDisplayInstrumentedTest {
    @Test
    fun displayManager_controller_starts_local_and_never_lists_default_display_as_external() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val app = activity.application as MaxVideoPlayerApplication
                val controller = ExternalDisplayController(activity, app.container.playbackConnection)
                controller.start()
                try {
                    assertTrue(controller.state.value.active is OutputDeviceState.Local)
                    assertTrue(controller.state.value.availableExternalDisplays.none { it.displayId == Display.DEFAULT_DISPLAY })
                    controller.returnToPhone()
                    assertTrue(controller.state.value.active is OutputDeviceState.Local)
                } finally {
                    controller.stop()
                }
            }
        } finally {
            scenario.close()
        }
    }
}
