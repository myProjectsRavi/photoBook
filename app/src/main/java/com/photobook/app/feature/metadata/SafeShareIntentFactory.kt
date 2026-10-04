package com.photobook.app.feature.metadata

import android.content.ClipData
import android.content.Context
import android.content.Intent

object SafeShareIntentFactory {
    fun build(
        context: Context,
        items: List<SafeShareItem>,
    ): Intent? {
        if (items.isEmpty()) return null

        val uris = items.map { item -> item.uri }
        val mimeType = items
            .map { item -> item.mimeType.ifBlank { "image/*" } }
            .distinct()
            .singleOrNull()
            ?: "image/*"

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }

        val clip = ClipData.newUri(
            context.contentResolver,
            items.first().label,
            uris.first(),
        )
        uris.drop(1).forEach { uri ->
            clip.addItem(ClipData.Item(uri))
        }

        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }
}
