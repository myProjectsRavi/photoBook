package com.photobook.app.verification

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.db.PhotoBookDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationInfrastructureTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PhotoBookDatabase::class.java,
    )

    @Test
    fun exportedSchema12_canCreateIntegrityCleanDatabase() {
        val database = helper.createDatabase(TEST_DATABASE, CURRENT_VERSION)
        try {
            database.query("PRAGMA integrity_check").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
        } finally {
            database.close()
        }
    }

    companion object {
        private const val TEST_DATABASE = "photobook-migration-phase0"
        private const val CURRENT_VERSION = 12
    }
}
