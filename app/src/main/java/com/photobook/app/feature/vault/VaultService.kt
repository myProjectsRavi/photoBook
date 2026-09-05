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
import com.photobook.app.data.db.VaultDao
import com.photobook.app.data.db.VaultEntity
import com.photobook.app.data.model.PhotoRecord
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
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

    private val legacyMasterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            legacyMasterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val vaultDir: File by lazy {
        File(context.filesDir, VAULT_DIR).apply { if (!exists()) mkdirs() }
    }

    private val authCrypto: VaultAuthCrypto by lazy {
        VaultAuthCrypto(context)
    }

    internal fun prepareAuthentication(): VaultAuthPreparation = authCrypto.prepareAuthentication()

    internal fun completeAuthentication(
        preparation: VaultAuthPreparation,
        authenticatedCipher: Cipher,
    ): VaultCryptoSession = authCrypto.completeAuthentication(preparation, authenticatedCipher)

    internal fun preparePreREnrollmentAfterCredential(): VaultPreRCredentialResult =
        authCrypto.preparePreREnrollmentAfterCredential()

    internal fun preparePreRRecoveryAfterCredential(): VaultPreRCredentialResult =
        authCrypto.preparePreRRecoveryAfterCredential()

    internal fun cancelAuthentication(preparation: VaultAuthPreparation) {
        authCrypto.cancelAuthentication(preparation)
    }

    suspend fun listItems(
        session: VaultCryptoSession,
        includePreviews: Boolean = false,
    ): List<VaultItem> = withContext(Dispatchers.IO) {
        migrateLegacyItemsIfNeeded()
        migrateLegacyCiphertextIfNeeded(session)
        vaultDao.getAllVaultItems().map { entity ->
            val item = entity.toVaultItem()
            if (includePreviews) {
                item.copy(previewUri = createPreviewUri(entity, session))
            } else {
                item
            }
        }
    }

    suspend fun addPhotos(
        photos: List<PhotoRecord>,
        session: VaultCryptoSession,
    ): VaultSaveResult = withContext(Dispatchers.IO) {
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
            migrateLegacyCiphertextIfNeeded(session)
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
                val itemId = UUID.randomUUID().toString()
                val encryptedName = buildV2EncryptedFileName(photo.fileName)
                val targetFile = File(vaultDir, encryptedName)
                val tempFile = File(vaultDir, authCrypto.temporaryV2FileName(itemId))
                prepareFreshTemp(tempFile)
                if (targetFile.exists()) {
                    throw IOException("Refusing to overwrite an existing Vault v2 file")
                }

                val sourceInput = context.contentResolver.openInputStream(sourceUri)
                    ?: return@forEach
                val copied = runCatching {
                    sourceInput.use { input ->
                        authCrypto.encryptToFile(
                            session = session,
                            input = input,
                            target = tempFile,
                            associatedData = authCrypto.associatedData(itemId),
                        )
                    }
                    authCrypto.renameAtomically(tempFile, targetFile)
                    true
                }.getOrElse {
                    runCatching { tempFile.delete() }
                    runCatching { targetFile.delete() }
                    false
                }
                if (!copied) return@forEach

                val entity = VaultEntity(
                    id = itemId,
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

    suspend fun exportToDevice(
        itemId: String,
        session: VaultCryptoSession,
    ): VaultExportResult = withContext(Dispatchers.IO) {
        runCatching {
            migrateLegacyItemsIfNeeded()
            migrateLegacyCiphertextIfNeeded(session)
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
                val decryptedInput = openVaultInput(item, session)
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
            authCrypto.legacyTwinNameFor(item.encryptedFileName)?.let { legacyTwin ->
                runCatching { File(vaultDir, legacyTwin).delete() }
            }
            deletePreviewFile(item.id)
            true
        }.getOrDefault(false)
    }

    fun clearPreviewCache() {
        runCatching {
            File(context.cacheDir, VAULT_PREVIEW_DIR).deleteRecursively()
        }
    }

    private fun buildLegacyEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            legacyMasterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }

    private fun createPreviewUri(
        entity: VaultEntity,
        session: VaultCryptoSession,
    ): Uri? {
        val encryptedSource = File(vaultDir, entity.encryptedFileName)
        if (!encryptedSource.exists()) return null

        val previewFile = previewFile(entity.id)
        if (previewFile.exists() && previewFile.length() > 0L) {
            return Uri.fromFile(previewFile)
        }

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openVaultInput(entity, session).use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, PREVIEW_MAX_EDGE_PX)
            }
            val bitmap = openVaultInput(entity, session).use { input ->
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

    private fun openVaultInput(
        entity: VaultEntity,
        session: VaultCryptoSession,
    ): InputStream {
        val source = File(vaultDir, entity.encryptedFileName)
        if (!source.exists()) throw IOException("Vault ciphertext is missing")
        return if (authCrypto.isV2FileName(entity.encryptedFileName)) {
            authCrypto.openDecryptedInput(
                session = session,
                encryptedFile = source,
                associatedData = authCrypto.associatedData(entity.id),
            )
        } else {
            buildLegacyEncryptedFile(source).openFileInput()
        }
    }

    private suspend fun migrateLegacyCiphertextIfNeeded(session: VaultCryptoSession) {
        vaultDao.getAllVaultItems().forEach { entity ->
            if (authCrypto.isV2FileName(entity.encryptedFileName)) {
                cleanupInterruptedLegacyTwin(entity)
            } else {
                migrateLegacyCiphertext(entity, session)
            }
        }
    }

    private suspend fun migrateLegacyCiphertext(
        entity: VaultEntity,
        session: VaultCryptoSession,
    ) {
        if (!entity.encryptedFileName.endsWith(VaultAuthCrypto.LEGACY_FILE_SUFFIX)) {
            throw IOException("Unknown Vault ciphertext format")
        }

        val legacyFile = File(vaultDir, entity.encryptedFileName)
        if (!legacyFile.exists()) throw IOException("Legacy Vault ciphertext is missing")

        val finalName = authCrypto.v2FileNameFor(entity.encryptedFileName)
        val finalFile = File(vaultDir, finalName)
        val tempFile = File(vaultDir, authCrypto.temporaryV2FileName(entity.id))
        val associatedData = authCrypto.associatedData(entity.id)

        // Recovery point: a previous run may have completed and renamed a verified v2 file but
        // crashed before Room switched filenames. Verify it against the still-authoritative
        // legacy plaintext before reusing it.
        if (finalFile.exists()) {
            val legacyDigest = sha256(buildLegacyEncryptedFile(legacyFile).openFileInput())
            val v2Digest = runCatching {
                sha256(authCrypto.openDecryptedInput(session, finalFile, associatedData))
            }.getOrNull()
            if (v2Digest != null && legacyDigest.contentEquals(v2Digest)) {
                commitMigratedCiphertext(entity, finalName, legacyFile, tempFile)
                return
            }
            if (!finalFile.delete()) {
                throw IOException("Unable to remove an invalid interrupted Vault v2 file")
            }
        }

        prepareFreshTemp(tempFile)
        val legacyDigest = MessageDigest.getInstance("SHA-256")
        buildLegacyEncryptedFile(legacyFile).openFileInput().use { legacyInput ->
            DigestInputStream(legacyInput, legacyDigest).use { digestingInput ->
                authCrypto.encryptToFile(
                    session = session,
                    input = digestingInput,
                    target = tempFile,
                    associatedData = associatedData,
                )
            }
        }
        val expectedDigest = legacyDigest.digest()
        val actualDigest = runCatching {
            sha256(authCrypto.openDecryptedInput(session, tempFile, associatedData))
        }.getOrElse { error ->
            runCatching { tempFile.delete() }
            throw error
        }
        if (!expectedDigest.contentEquals(actualDigest)) {
            runCatching { tempFile.delete() }
            throw IOException("Vault migration verification failed")
        }

        authCrypto.renameAtomically(tempFile, finalFile)
        commitMigratedCiphertext(entity, finalName, legacyFile, tempFile)
    }

    private suspend fun commitMigratedCiphertext(
        entity: VaultEntity,
        finalName: String,
        legacyFile: File,
        tempFile: File,
    ) {
        val updated = vaultDao.updateEncryptedFileName(entity.id, finalName)
        if (updated != 1) {
            runCatching { File(vaultDir, finalName).delete() }
            throw IOException("Vault migration could not commit metadata")
        }

        deletePreviewFile(entity.id)
        runCatching { tempFile.delete() }
        if (legacyFile.exists() && !legacyFile.delete()) {
            // Room now points at the verified v2 file. Fail closed so the user is not told the
            // hardening completed while a legacy decryptable twin still remains on disk.
            throw IOException("Vault migration could not remove the legacy ciphertext")
        }
    }

    private fun cleanupInterruptedLegacyTwin(entity: VaultEntity) {
        runCatching { File(vaultDir, authCrypto.temporaryV2FileName(entity.id)).delete() }
        val legacyName = authCrypto.legacyTwinNameFor(entity.encryptedFileName) ?: return
        val legacyFile = File(vaultDir, legacyName)
        if (legacyFile.exists() && !legacyFile.delete()) {
            throw IOException("Vault v2 is active but its legacy ciphertext twin could not be removed")
        }
    }

    private fun prepareFreshTemp(tempFile: File) {
        if (tempFile.exists() && !tempFile.delete()) {
            throw IOException("Unable to clear interrupted Vault migration temp file")
        }
    }

    private fun sha256(input: InputStream): ByteArray {
        return input.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
            digest.digest()
        }
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

    private fun buildLegacyEncryptedFileName(originalName: String): String {
        val base = originalName.substringBeforeLast('.', missingDelimiterValue = originalName)
            .ifBlank { "PhotoBook" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(44)
        return "${base}_${UUID.randomUUID().toString().take(8)}${VaultAuthCrypto.LEGACY_FILE_SUFFIX}"
    }

    private fun buildV2EncryptedFileName(originalName: String): String {
        return authCrypto.v2FileNameFor(buildLegacyEncryptedFileName(originalName))
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
        private const val STREAM_BUFFER_BYTES = 64 * 1024
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
