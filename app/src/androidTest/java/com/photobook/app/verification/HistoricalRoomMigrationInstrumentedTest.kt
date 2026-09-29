package com.photobook.app.verification

/**
 * S04 historical migration evidence.
 *
 * Historical schema provenance: commit 09c35afc15f3bfa087b0b78ab826f04598555c9f
 * declared PhotoBookDatabase version 1 with PhotoEntity and PhotoFtsEntity and
 * exportSchema=false. Historical Room JSON therefore does not exist and must
 * not be fabricated.
 */
internal object HistoricalRoomMigrationProvenance {
    const val VERSION_ONE_SOURCE_COMMIT =
        "09c35afc15f3bfa087b0b78ab826f04598555c9f"
}
