package com.photobook.app.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable CI-only state assertion used by permission-volatility certification. */
@RunWith(AndroidJUnit4::class)
class PermissionUtilsInstrumentedTest {
    @Test
    fun reportsExpectedPhotoAccessMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expectedName = requireNotNull(instrumentation.arguments.getString("expectedMode")) {
            "expectedMode instrumentation argument is required"
        }
        val expected = PermissionUtils.PhotoAccessMode.valueOf(expectedName)
        val actual = PermissionUtils.photoAccessMode(instrumentation.targetContext)
        assertEquals("Photo access mode mismatch", expected, actual)
    }
}
