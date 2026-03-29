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
@Entity(tableName = "photo_fts")
data class PhotoFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val searchableText: String,
)

fun PhotoEntity.toFtsEntity(): PhotoFtsEntity {
    val textParts = listOfNotNull(
        fileName,
        folderName,
        folderPath,
        city,
        state,
        country,
        ocrText,
        PhotoTagCodec.toSearchableText(PhotoTagCodec.decode(mlTagsPayload)),
    )
    return PhotoFtsEntity(
        rowId = id,
        searchableText = textParts
            .joinToString(" ")
            .lowercase()
            .trim(),
    )
}
