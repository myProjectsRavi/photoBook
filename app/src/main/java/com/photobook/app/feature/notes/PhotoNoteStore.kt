package com.photobook.app.feature.notes

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.photobook.app.data.model.PhotoRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class PhotoNoteStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val securePrefsResult: Result<SharedPreferences> by lazy {
        runCatching { createEncryptedPrefs() }
    }

    private val revisionFlow = MutableStateFlow(0L)

    fun changes(): StateFlow<Long> = revisionFlow.asStateFlow()

    @Volatile
    private var noteCache: NoteCache? = null

    fun getNote(photo: PhotoRecord): String {
        if (!isValidIdentity(photo)) return ""
        val prefs = securePrefsResult.getOrNull() ?: return ""
        // Legacy ID-only aliases are quarantined. Without the historical media identity,
        // attaching one to the current row could disclose a private note after MediaStore ID reuse.
        return prefs.getString(stableKey(photo), "").orEmpty()
    }

    fun noteContains(photo: PhotoRecord, text: String): Boolean {
        if (!isValidIdentity(photo) || text.isBlank()) return false
        val cache = noteCache ?: loadAllNotes().also { noteCache = it }
        val note = cache.stable[stableKey(photo)] ?: return false
        return note.contains(text, ignoreCase = true)
    }

    fun saveNote(photo: PhotoRecord, note: String): Boolean {
        if (!isValidIdentity(photo)) return false
        val trimmed = note.trim()
        if (trimmed.isEmpty()) {
            return deleteNote(photo)
        }
        val prefs = securePrefsResult.getOrNull() ?: return false
        val committed = prefs.edit()
            .putString(stableKey(photo), trimmed.take(MAX_NOTE_CHARS))
            // Once a legacy encrypted note is explicitly saved for this concrete media record,
            // retire the ID-only alias so a future MediaStore ID reuse cannot inherit it.
            .remove(legacyKey(photo.id))
            .commit()
        if (committed) publishRevision()
        return committed
    }

    fun deleteNote(photo: PhotoRecord): Boolean {
        if (!isValidIdentity(photo)) return false
        val prefs = securePrefsResult.getOrNull() ?: return false
        val committed = prefs.edit()
            .remove(stableKey(photo))
            .remove(legacyKey(photo.id))
            .commit()
        if (committed) publishRevision()
        return committed
    }

    /**
     * Explicit legacy-recovery helpers. Production note UI and search must never call these:
     * ID-only aliases do not contain enough identity to bind safely after MediaStore ID reuse.
     */
    fun getNote(photoId: Long): String {
        if (photoId <= 0L) return ""
        return securePrefsResult.getOrNull()?.getString(legacyKey(photoId), "").orEmpty()
    }

    fun noteContains(photoId: Long, text: String): Boolean {
        if (photoId <= 0L || text.isBlank()) return false
        val cache = noteCache ?: loadAllNotes().also { noteCache = it }
        return cache.legacy[photoId]?.contains(text, ignoreCase = true) == true
    }

    fun saveNote(photoId: Long, note: String): Boolean {
        if (photoId <= 0L) return false
        val trimmed = note.trim()
        if (trimmed.isEmpty()) return deleteNote(photoId)
        val prefs = securePrefsResult.getOrNull() ?: return false
        val committed = prefs.edit()
            .putString(legacyKey(photoId), trimmed.take(MAX_NOTE_CHARS))
            .commit()
        if (committed) publishRevision()
        return committed
    }

    fun deleteNote(photoId: Long): Boolean {
        if (photoId <= 0L) return false
        val prefs = securePrefsResult.getOrNull() ?: return false
        val committed = prefs.edit().remove(legacyKey(photoId)).commit()
        if (committed) publishRevision()
        return committed
    }

    private fun loadAllNotes(): NoteCache {
        val prefs = securePrefsResult.getOrNull() ?: return NoteCache()
        val all = prefs.all ?: return NoteCache()
        val stable = HashMap<String, String>()
        val legacy = HashMap<Long, String>()
        for ((key, value) in all) {
            if (value !is String || value.isBlank()) continue
            when {
                key.startsWith(STABLE_PREFIX) -> stable[key] = value
                key.startsWith(LEGACY_PREFIX) -> {
                    val id = key.removePrefix(LEGACY_PREFIX).toLongOrNull() ?: continue
                    legacy[id] = value
                }
            }
        }
        return NoteCache(stable = stable, legacy = legacy)
    }

    private fun publishRevision() {
        noteCache = null
        revisionFlow.value = revisionFlow.value + 1L
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun stableKey(photo: PhotoRecord): String =
        "${STABLE_PREFIX}${photo.id}_${photo.dateAdded}"

    private fun legacyKey(photoId: Long): String = "$LEGACY_PREFIX$photoId"

    private fun isValidIdentity(photo: PhotoRecord): Boolean =
        photo.id > 0L && photo.dateAdded > 0L

    private data class NoteCache(
        val stable: Map<String, String> = emptyMap(),
        val legacy: Map<Long, String> = emptyMap(),
    )

    companion object {
        internal const val PREFS_NAME = "photobook_private_notes"
        private const val LEGACY_PREFIX = "photo_note_"
        private const val STABLE_PREFIX = "photo_note_v2_"
        // The historical plaintext fallback file is intentionally left untouched for forensic/
        // recovery purposes, but new code never reads or writes it.
        const val MAX_NOTE_CHARS = 1000
    }
}
