package com.photobook.app.feature.notes

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoNoteStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        createEncryptedPrefsOrFallback()
    }

    // In-memory cache for fast search across all notes. Invalidated on save/delete.
    @Volatile
    private var noteCache: Map<Long, String>? = null

    fun getNote(photoId: Long): String {
        if (photoId <= 0L) return ""
        return prefs.getString(key(photoId), "").orEmpty()
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

    fun saveNote(photoId: Long, note: String) {
        if (photoId <= 0L) return
        val trimmed = note.trim()
        if (trimmed.isEmpty()) {
            deleteNote(photoId)
            return
        }
        prefs.edit().putString(key(photoId), trimmed.take(MAX_NOTE_CHARS)).apply()
        noteCache = null // Invalidate cache
    }

    fun deleteNote(photoId: Long) {
        if (photoId <= 0L) return
        prefs.edit().remove(key(photoId)).apply()
        noteCache = null // Invalidate cache
    }

    private fun loadAllNotes(): Map<Long, String> {
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

    private fun createEncryptedPrefsOrFallback(): SharedPreferences {
        return runCatching {
            val key = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            context.getSharedPreferences(PREFS_FALLBACK_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun key(photoId: Long): String = "photo_note_$photoId"

    companion object {
        private const val PREFS_NAME = "photobook_private_notes"
        private const val PREFS_FALLBACK_NAME = "photobook_private_notes_fallback"
        const val MAX_NOTE_CHARS = 1000
    }
}
