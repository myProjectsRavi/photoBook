package com.photobook.app.feature.qrshare

import java.security.MessageDigest

object QrPayloadHash {
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }
}
