package com.photobook.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    prefix = [2, 3, 4],
)
@Entity(tableName = "video_frame_fts")
data class VideoFrameFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val searchableText: String,
)

fun VideoFrameEntity.toFtsEntity(): VideoFrameFtsEntity {
    return VideoFrameFtsEntity(
        rowId = id,
        searchableText = searchableText.lowercase().trim(),
    )
}
