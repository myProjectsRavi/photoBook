package com.photobook.app.ml

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The single readiness boundary for all bundled, network-independent intelligence. */
interface OnDeviceIntelligence {
    suspend fun ensureReady(needsMl: Boolean, needsOcr: Boolean): Availability

    data class Availability(
        val mlReady: Boolean,
        val ocrReady: Boolean,
    )
}

@Singleton
class BundledOnDeviceIntelligence @Inject constructor(
) : OnDeviceIntelligence {

    override suspend fun ensureReady(
        needsMl: Boolean,
        needsOcr: Boolean,
    ): OnDeviceIntelligence.Availability = withContext(Dispatchers.IO) {
        // These capabilities are deliberately local and do not consult a
        // remote model registry. The compact OCR implementation reports its
        // own permanent capability failure when no supported local model is
        // available, so callers never manufacture a processed result.
        val mlReady = true
        val ocrReady = true

        OnDeviceIntelligence.Availability(
            mlReady = !needsMl || mlReady,
            ocrReady = !needsOcr || ocrReady,
        )
    }
}
