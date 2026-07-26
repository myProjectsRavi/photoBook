package com.photobook.app.feature.vault

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.data.db.VaultDao
import com.photobook.app.data.db.VaultEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class VaultItem(
    val id: String,
    val sourcePhotoId: Long,
    val originalFileName: String,
    val mimeType: String,
    val encryptedFileName: String,
    val addedAtMs: Long,
    val previewUri: Uri? = null,
)

sealed interface VaultSaveResult {
    data class Success(
        val addedCount: Int,
        val skippedCount: Int,
        val addedPhotoIds: Set<Long>,
        val protectedPhotoIds: Set<Long>,
    ) : VaultSaveResult

    data class Error(val throwable: Throwable? = null) : VaultSaveResult
}

sealed interface VaultExportResult {
    data class Success(
        val uri: Uri,
        val fileName: String,
    ) : VaultExportResult

    data class Error(val throwable: Throwable? = null) : VaultExportResult
}

class VaultService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDao: VaultDao,
) {
    constructor(context: Context) : this(
        context,
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            VaultServiceEntryPoint::class.java,
        ).vaultDao(),
    )

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val vaultDir: File by lazy {
        File(context.filesDir, VAULT_DIR).apply { if (!exists()) mkdirs() }
    }

    suspend fun listItems(includePreviews: Boolean = false): List<VaultItem> = withContext(Dispatchers.IO) {
        migrateLegacyItemsIfNeeded()
        vaultDao.getAllVaultItems().map { entity ->
            val item = entity.toVaultItem()
            if (includePreviews) {
                item.copy(previewUri = createPreviewUri(entity))
            } else {
                item
            }
        }
    }

    suspend fun addPhotos(photos: List<PhotoRecord>): VaultSaveResult = withContext(Dispatchers.IO) {
        if (photos.isEmpty()) {
            return@withContext VaultSaveResult.Success(
                addedCount = 0,
                skippedCount = 0,
                addedPhotoIds = emptySet(),
                protectedPhotoIds = emptySet(),
            )
        }
        runCatching {
            migrateLegacyItemsIfNeeded()
            val photoIds = photos.map { photo -> photo.id }
            val existingByPhotoId = vaultDao.getProtectedPhotoIds(photoIds).toMutableSet()

            var added = 0
            var skipped = 0
            val addedPhotoIds = linkedSetOf<Long>()
            val protectedPhotoIds = linkedSetOf<Long>()
            photos.forEach { photo ->
                if (photo.id in existingByPhotoId) {
                    skipped += 1
                    protectedPhotoIds += photo.id
                    return@forEach
                }
                val sourceUri = runCatching { Uri.parse(photo.uriString) }.getOrNull()
                    ?: return@forEach
                val encryptedName = buildEncryptedFileName(photo.fileName)
                val targetFile = File(vaultDir, encryptedName)
                val encryptedFile = buildEncryptedFile(targetFile)
                val copied = copyIntoEncryptedFile(sourceUri, encryptedFile)
                if (!copied) {
                    runCatching { targetFile.delete() }
                    return@forEach
                }

                val entity = VaultEntity(
                    id = UUID.randomUUID().toString(),
                    sourcePhotoId = photo.id,
                    originalFileName = photo.fileName.ifBlank { "PhotoBook_${photo.id}.jpg" },
                    mimeType = photo.mimeType.ifBlank { "image/jpeg" },
                    encryptedFileName = encryptedName,
                    addedAtMs = System.currentTimeMillis(),
                )

                val inserted = runCatching { vaultDao.insertVaultItem(entity) }
                    .getOrElse {
                        runCatching { targetFile.delete() }
                        throw it
                    }
                if (inserted == INSERT_CONFLICT) {
                    runCatching { targetFile.delete() }
                    existingByPhotoId += photo.id
                    skipped += 1
                    protectedPhotoIds += photo.id
                } else {
                    existingByPhotoId += photo.id
                    added += 1
                    addedPhotoIds += photo.id
                    protectedPhotoIds += photo.id
                }
            }

            if (photos.isNotEmpty() && protectedPhotoIds.isEmpty()) {
                return@runCatching VaultSaveResult.Error()
            }

            VaultSaveResult.Success(
                addedCount = added,
                skippedCount = skipped,
                addedPhotoIds = addedPhotoIds,
                protectedPhotoIds = protectedPhotoIds,
            )
        }.getOrElse { error ->
            VaultSaveResult.Error(error)
        }
    }

    suspend fun exportToDevice(itemId: String): VaultExportResult = withContext(Dispatchers.IO) {
        runCatching {
            migrateLegacyItemsIfNeeded()
            val item = vaultDao.getVaultItemById(itemId)
                ?: return@runCatching VaultExportResult.Error()
            val encryptedFile = File(vaultDir, item.encryptedFileName)
            if (!encryptedFile.exists()) {
                return@runCatching VaultExportResult.Error()
            }

            val outputName = buildExportFileName(item.originalFileName)
            val mimeType = item.mimeType.ifBlank { "image/jpeg" }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outputName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoBook/Vault")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val outputUri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return@runCatching VaultExportResult.Error()

            try {
                val decryptedInput = buildEncryptedFile(encryptedFile).openFileInput()
                val output = context.contentResolver.openOutputStream(outputUri, "w")
                    ?: return@runCatching VaultExportResult.Error()
                decryptedInput.use { input ->
                    output.use { stream ->
                        input.copyTo(stream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.update(
                        outputUri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }
                VaultExportResult.Success(uri = outputUri, fileName = outputName)
            } catch (t: Throwable) {
                runCatching { context.contentResolver.delete(outputUri, null, null) }
                VaultExportResult.Error(t)
            }
        }.getOrElse { error ->
            VaultExportResult.Error(error)
        }
    }

    suspend fun deleteItem(itemId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            migrateLegacyItemsIfNeeded()
            val item = vaultDao.getVaultItemById(itemId) ?: return@runCatching false
            val deletedRows = vaultDao.deleteVaultItemById(itemId)
            if (deletedRows <= 0) return@runCatching false
            runCatching { File(vaultDir, item.encryptedFileName).delete() }
            deletePreviewFile(item.id)
            true
        }.getOrDefault(false)
    }

    fun clearPreviewCache() {
        runCatching {
            File(context.cacheDir, VAULT_PREVIEW_DIR).deleteRecursively()
        }
    }

    private fun buildEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }

    private fun copyIntoEncryptedFile(sourceUri: Uri, target: EncryptedFile): Boolean {
        val input = context.contentResolver.openInputStream(sourceUri) ?: return false
        return runCatching {
            input.use { stream ->
                target.openFileOutput().use { output ->
                    stream.copyTo(output)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun createPreviewUri(entity: VaultEntity): Uri? {
        val encryptedSource = File(vaultDir, entity.encryptedFileName)
        if (!encryptedSource.exists()) return null

        val previewFile = previewFile(entity.id)
        if (previewFile.exists() && previewFile.length() > 0L) {
            return Uri.fromFile(previewFile)
        }

        return runCatching {
            val encryptedFile = buildEncryptedFile(encryptedSource)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            encryptedFile.openFileInput().use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, PREVIEW_MAX_EDGE_PX)
            }
            val bitmap = encryptedFile.openFileInput().use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return@runCatching null

            previewFile.parentFile?.mkdirs()
            previewFile.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, PREVIEW_JPEG_QUALITY, output)
            }
            bitmap.recycle()
            Uri.fromFile(previewFile)
        }.getOrNull()
    }

    private fun previewFile(itemId: String): File {
        val safeName = itemId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(context.cacheDir, VAULT_PREVIEW_DIR), "$safeName.jpg")
    }

    private fun deletePreviewFile(itemId: String) {
        runCatching { previewFile(itemId).delete() }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height
        while (sampledWidth / 2 >= maxEdge || sampledHeight / 2 >= maxEdge) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private suspend fun migrateLegacyItemsIfNeeded() {
        val legacyItems = loadLegacyItems()
        if (legacyItems.isEmpty()) return

        var allImported = true
        legacyItems.forEach { item ->
            val inserted = runCatching {
                vaultDao.insertVaultItem(item.toVaultEntity())
            }.getOrElse {
                allImported = false
                INSERT_CONFLICT
            }
            if (inserted == INSERT_CONFLICT) {
                val existing = vaultDao.getProtectedPhotoIds(listOf(item.sourcePhotoId))
                if (existing.isEmpty()) {
                    allImported = false
                }
            }
        }

        if (allImported) {
            securePrefs.edit().remove(KEY_ITEMS).apply()
        }
    }

    private fun loadLegacyItems(): List<VaultItem> {
        val encoded = securePrefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    add(
                        VaultItem(
                            id = obj.optString("id"),
                            sourcePhotoId = obj.optLong("sourcePhotoId"),
                            originalFileName = obj.optString("originalFileName"),
                            mimeType = obj.optString("mimeType"),
                            encryptedFileName = obj.optString("encryptedFileName"),
                            addedAtMs = obj.optLong("addedAtMs"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildEncryptedFileName(originalName: String): String {
        val base = originalName.substringBeforeLast('.', missingDelimiterValue = originalName)
            .ifBlank { "PhotoBook" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(44)
        return "${base}_${UUID.randomUUID().toString().take(8)}.pbvault"
    }

    private fun buildExportFileName(originalName: String): String {
        val dot = originalName.lastIndexOf('.')
        return if (dot in 1 until originalName.lastIndex) {
            val base = originalName.substring(0, dot)
            val ext = originalName.substring(dot)
            "${base}_vault$ext"
        } else {
            "${originalName}_vault.jpg"
        }
    }

    private companion object {
        private const val PREFS_NAME = "vault_secure_prefs"
        private const val KEY_ITEMS = "vault_items"
        private const val VAULT_DIR = "vault_store"
        private const val VAULT_PREVIEW_DIR = "vault_preview"
        private const val INSERT_CONFLICT = -1L
        private const val PREVIEW_MAX_EDGE_PX = 960
        private const val PREVIEW_JPEG_QUALITY = 82
    }
}

private fun VaultEntity.toVaultItem(): VaultItem {
    return VaultItem(
        id = id,
        sourcePhotoId = sourcePhotoId,
        originalFileName = originalFileName,
        mimeType = mimeType,
        encryptedFileName = encryptedFileName,
        addedAtMs = addedAtMs,
    )
}

private fun VaultItem.toVaultEntity(): VaultEntity {
    return VaultEntity(
        id = id,
        sourcePhotoId = sourcePhotoId,
        originalFileName = originalFileName,
        mimeType = mimeType,
        encryptedFileName = encryptedFileName,
        addedAtMs = addedAtMs,
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface VaultServiceEntryPoint {
    fun vaultDao(): VaultDao
}
