package com.photobook.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object ArchiveDecisionStates {
    const val CANDIDATE = "candidate"
    const val KEPT = "kept"
    const val TRASHED = "trashed"
    const val DELETE_DUE = "delete_due"
    const val STALE = "stale"
}

@Entity(
    tableName = "archive_decisions",
    indices = [
        Index(value = ["state"]),
        Index(value = ["lastDetectedAtMs"]),
        Index(value = ["trashedAtMs"]),
        Index(value = ["retentionDays"]),
    ],
)
data class ArchiveDecisionEntity(
    @PrimaryKey
    val photoId: Long,
    val uriString: String,
    val state: String,
    val confidence: Double,
    val reasons: String,
    val firstDetectedAtMs: Long,
    val lastDetectedAtMs: Long,
    val trashedAtMs: Long?,
    val retentionDays: Int,
)
