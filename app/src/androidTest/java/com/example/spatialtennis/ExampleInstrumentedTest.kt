package com.example.spatialtennis

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.spatialtennis.platform.LaunchActivity

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertNotNull

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun launchActivityStaysAlive() {
        // A Spatial Stage is owned by the PICO runtime and intentionally does not
        // complete ActivityScenario's synchronous DESTROYED transition on close.
        // This smoke test only verifies that the launcher reaches RESUMED and is alive.
        val scenario = ActivityScenario.launch(LaunchActivity::class.java)
        scenario.onActivity { activity -> assertNotNull(activity) }
    }
}
