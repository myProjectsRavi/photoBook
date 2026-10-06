package com.photobook.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object VaultOperationTypes {
    const val ADD = "add"
    const val MOVE_OUT = "move_out"
}

object VaultOperationStates {
    const val PREPARED = "prepared"
    const val CIPHERTEXT_COMMITTED = "ciphertext_committed"
    const val EXPORT_VERIFIED = "export_verified"
}

@Entity(
    tableName = "vault_operations",
    indices = [
        Index(value = ["type"]),
        Index(value = ["state"]),
        Index(value = ["vaultItemId"]),
        Index(value = ["sourcePhotoId"]),
        Index(value = ["updatedAtMs"]),
    ],
)
data class VaultOperationEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val state: String,
    val vaultItemId: String?,
    val sourcePhotoId: Long?,
    val encryptedFileName: String?,
    val outputUriString: String?,
    val expectedSha256Hex: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
