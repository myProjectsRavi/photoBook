package com.photobook.app.feature.notes

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject

class PhotoNoteStore @Inject constructor(
    private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        createEncryptedPrefsOrFallback()
    }

    fun getNote(photoId: Long): String {
        if (photoId <= 0L) return ""
        return prefs.getString(key(photoId), "").orEmpty()
    }

    fun saveNote(photoId: Long, note: String) {
        if (photoId <= 0L) return
        val trimmed = note.trim()
        if (trimmed.isEmpty()) {
            deleteNote(photoId)
            return
        }
        prefs.edit().putString(key(photoId), trimmed.take(MAX_NOTE_CHARS)).apply()
    }

    fun deleteNote(photoId: Long) {
        if (photoId <= 0L) return
        prefs.edit().remove(key(photoId)).apply()
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
