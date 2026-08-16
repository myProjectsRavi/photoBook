package com.photobook.app.verification

import android.Manifest
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.db.PhotoBookDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineAndDatabaseInvariantTest {

    @Test
    fun installedPackage_doesNotRequestInternetPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requestedPermissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertFalse(
            "PhotoBook must remain fully functional without android.permission.INTERNET",
            requestedPermissions.contains(Manifest.permission.INTERNET),
        )
    }

    @Test
    fun currentRoomSchema_opensAndPassesIntegrityCheck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            PhotoBookDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
        } finally {
            database.close()
        }
    }
}
