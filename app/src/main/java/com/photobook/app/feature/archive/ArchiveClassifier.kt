package com.photobook.app.feature.archive

import com.photobook.app.data.model.PhotoRecord
import javax.inject.Inject

data class ArchiveClassification(
    val confidence: Double,
    val reasons: List<String>,
)

class ArchiveClassifier @Inject constructor() {

    fun classify(photo: PhotoRecord, nowMs: Long = System.currentTimeMillis()): ArchiveClassification? {
        if (photo.isFavorite) return null
        if (!photo.mimeType.startsWith("image/", ignoreCase = true)) return null
        if (!isScreenshot(photo)) return null
        if (isFresh(photo, nowMs)) return null

        val metadataText = buildMetadataText(photo)
        val ocrText = photo.ocrText.lowercase()
        val combinedText = "$metadataText $ocrText"

        if (hasSensitiveDocumentCue(combinedText)) return null

        val hasPaymentAppCue = PAYMENT_APP_CUES.any { cue -> combinedText.contains(cue) }
        val hasUpiCue = UPI_CUES.any { cue -> combinedText.contains(cue) } || UPI_ID_REGEX.containsMatchIn(combinedText)
        val transactionCueCount = TRANSACTION_CUES.count { cue -> combinedText.contains(cue) }
        val hasAmountCue = AMOUNT_REGEX.containsMatchIn(combinedText)

        if (!photo.isOcrProcessed && !hasStrongMetadataPaymentCue(metadataText)) {
            return null
        }

        val eligible = when {
            hasPaymentAppCue && hasAmountCue && transactionCueCount >= 1 -> true
            hasPaymentAppCue && hasUpiCue && transactionCueCount >= 1 -> true
            hasUpiCue && hasAmountCue && transactionCueCount >= 2 -> true
            else -> false
        }
        if (!eligible) return null

        val reasons = buildList {
            if (hasPaymentAppCue) add("Payment app cue")
            if (hasUpiCue) add("UPI cue")
            if (hasAmountCue) add("Amount-like text")
            if (transactionCueCount > 0) add("Transaction status")
        }
        val confidence = (
            BASE_CONFIDENCE +
                (if (hasPaymentAppCue) 0.08 else 0.0) +
                (if (hasUpiCue) 0.06 else 0.0) +
                (if (hasAmountCue) 0.06 else 0.0) +
                minOf(transactionCueCount, 3) * 0.03
            ).coerceAtMost(MAX_CONFIDENCE)

        return if (confidence >= MIN_CONFIDENCE) {
            ArchiveClassification(confidence = confidence, reasons = reasons)
        } else {
            null
        }
    }

    private fun isScreenshot(photo: PhotoRecord): Boolean {
        val text = buildMetadataText(photo)
        return SCREENSHOT_CUES.any { cue -> text.contains(cue) }
    }

    private fun isFresh(photo: PhotoRecord, nowMs: Long): Boolean {
        if (photo.dateAdded <= 0L) return false
        return nowMs - photo.dateAdded < FRESH_GRACE_MS
    }

    private fun buildMetadataText(photo: PhotoRecord): String {
        return buildString {
            append(photo.folderPath)
            append(' ')
            append(photo.folderName)
            append(' ')
            append(photo.filePath)
            append(' ')
            append(photo.fileName)
        }.lowercase()
    }

    private fun hasStrongMetadataPaymentCue(metadataText: String): Boolean {
        val hasPaymentApp = PAYMENT_APP_CUES.any { cue -> metadataText.contains(cue) }
        val hasPaymentTerm = UPI_CUES.any { cue -> metadataText.contains(cue) } ||
            TRANSACTION_CUES.any { cue -> metadataText.contains(cue) }
        return hasPaymentApp && hasPaymentTerm
    }

    private fun hasSensitiveDocumentCue(text: String): Boolean {
        return SENSITIVE_DOCUMENT_CUE_REGEXES.any { regex -> regex.containsMatchIn(text) }
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30
        const val MIN_RETENTION_DAYS = 7
        const val MAX_RETENTION_DAYS = 30
        private const val BASE_CONFIDENCE = 0.68
        private const val MIN_CONFIDENCE = 0.84
        private const val MAX_CONFIDENCE = 0.98
        private const val FRESH_GRACE_MS = 24L * 60L * 60L * 1000L

        private val SCREENSHOT_CUES = listOf(
            "screenshot",
            "screen_shot",
            "screen-shot",
        )

        private val PAYMENT_APP_CUES = listOf(
            "phonepe",
            "phone pe",
            "gpay",
            "google pay",
            "paytm",
            "bhim",
            "cred",
            "amazon pay",
            "whatsapp pay",
            "mobikwik",
            "freecharge",
        )

        private val UPI_CUES = listOf(
            "upi",
            "utr",
            "vpa",
            "transaction id",
            "txn id",
            "reference id",
            "ref no",
        )

        private val TRANSACTION_CUES = listOf(
            "paid",
            "payment",
            "sent",
            "received",
            "debited",
            "credited",
            "successful",
            "success",
            "completed",
            "transferred",
            "transaction",
        )

        private val SENSITIVE_DOCUMENT_CUES = listOf(
            "aadhaar",
            "aadhar",
            "passport",
            "pan card",
            "driving licence",
            "driver license",
            "boarding pass",
            "flight ticket",
            "train ticket",
            "password",
            "one time password",
            "otp",
            "resume",
            "certificate",
            "insurance",
            "marksheet",
        )

        private val SENSITIVE_DOCUMENT_CUE_REGEXES = SENSITIVE_DOCUMENT_CUES.map { cue ->
            Regex("(^|[^a-z0-9])${Regex.escape(cue)}([^a-z0-9]|$)")
        }

        private val AMOUNT_REGEX = Regex(
            pattern = "(?:rs\\.?|inr|\\u20b9)\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?|[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s*(?:rs\\.?|inr)",
            option = RegexOption.IGNORE_CASE,
        )
        private val UPI_ID_REGEX = Regex("[a-z0-9._-]+@[a-z][a-z0-9._-]{2,}")
    }
}
