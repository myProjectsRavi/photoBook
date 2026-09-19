package com.photobook.app.feature.notes

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    // In-memory cache for fast search across all notes. Invalidated on save/delete.
    @Volatile
    private var noteCache: Map<Long, String>? = null

    fun getNote(photoId: Long): String {
        if (photoId <= 0L) return ""
        return securePrefsResult.getOrNull()?.getString(key(photoId), "").orEmpty()
    }

    /**
     * Fast search helper: checks if the note for [photoId] contains [text] (case-insensitive).
     * Uses an in-memory cache so this is O(1) per call after first load.
     */
    fun noteContains(photoId: Long, text: String): Boolean {
        if (photoId <= 0L || text.isBlank()) return false
        val cache = noteCache ?: loadAllNotes().also { noteCache = it }
        val note = cache[photoId] ?: return false
        return note.contains(text, ignoreCase = true)
    }

    fun saveNote(photoId: Long, note: String): Boolean {
        if (photoId <= 0L) return false
        val trimmed = note.trim()
        if (trimmed.isEmpty()) {
            return deleteNote(photoId)
        }
        val prefs = securePrefsResult.getOrNull() ?: return false
        val committed = prefs.edit()
            .putString(key(photoId), trimmed.take(MAX_NOTE_CHARS))
            .commit()
        if (committed) publishRevision()
        return committed
    }

    fun deleteNote(photoId: Long): Boolean {
        if (photoId <= 0L) return false
        val prefs = securePrefsResult.getOrNull() ?: return false
        val committed = prefs.edit().remove(key(photoId)).commit()
        if (committed) publishRevision()
        return committed
    }

    private fun loadAllNotes(): Map<Long, String> {
        val prefs = securePrefsResult.getOrNull() ?: return emptyMap()
        val all = prefs.all ?: return emptyMap()
        val result = HashMap<Long, String>(all.size)
        val prefix = "photo_note_"
        for ((k, v) in all) {
            if (k.startsWith(prefix) && v is String && v.isNotBlank()) {
                val id = k.removePrefix(prefix).toLongOrNull() ?: continue
                result[id] = v
            }
        }
        return result
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

    private fun key(photoId: Long): String = "photo_note_$photoId"

    companion object {
        private const val PREFS_NAME = "photobook_private_notes"
        // The historical plaintext fallback file is intentionally left untouched for forensic/
        // recovery purposes, but new code never reads or writes it.
        const val MAX_NOTE_CHARS = 1000
    }
}
