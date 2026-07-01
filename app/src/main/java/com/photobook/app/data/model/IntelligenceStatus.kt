package com.photobook.app.data.model

enum class IntelligenceStatus {
    PENDING,
    MODEL_PREPARING,
    PROCESSED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT;

    val shouldProcess: Boolean
        get() = this == PENDING || this == MODEL_PREPARING || this == FAILED_RETRYABLE

    companion object {
        fun fromStored(value: String?, processedFallback: Boolean): IntelligenceStatus {
            val parsed = entries.firstOrNull { status ->
                status.name.equals(value.orEmpty(), ignoreCase = true)
            }
            return parsed ?: if (processedFallback) PROCESSED else PENDING
        }
    }
}
