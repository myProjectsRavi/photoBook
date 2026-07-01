package com.photobook.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_items",
    indices = [
        Index(value = ["sourcePhotoId"], unique = true),
        Index(value = ["addedAtMs"]),
    ],
)
data class VaultEntity(
    @PrimaryKey
    val id: String,
    val sourcePhotoId: Long,
    val originalFileName: String,
    val mimeType: String,
    val encryptedFileName: String,
    val addedAtMs: Long,
)
