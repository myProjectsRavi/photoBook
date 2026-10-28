package com.photobook.app.verification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photobook.app.di.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationInfrastructureTest {

    @Test
    fun productionSchema14_canCreateIntegrityCleanDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DATABASE)

        val database = AppModule.providePhotoBookDatabase(context)
        try {
            database.openHelper.writableDatabase

            database.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(CURRENT_VERSION, cursor.getInt(0))
            }
            database.openHelper.readableDatabase.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
        } finally {
            database.close()
            context.deleteDatabase(TEST_DATABASE)
        }
    }

    companion object {
        private const val TEST_DATABASE = "photobook.db"
        private const val CURRENT_VERSION = 14
    }
}
