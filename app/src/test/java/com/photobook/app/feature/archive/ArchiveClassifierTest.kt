package com.photobook.app.feature.archive

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.model.MLTag
import com.photobook.app.data.model.PhotoRecord
import org.junit.Test

class ArchiveClassifierTest {

    private val classifier = ArchiveClassifier()

    @Test
    fun phonePeTransactionScreenshot_matchesStrictClassifier() {
        val photo = sampleScreenshot(
            ocrText = "PhonePe payment successful. Paid Rs. 500 to Ravi by UPI. UTR 123456789.",
        )

        val result = classifier.classify(photo, NOW_MS)

        assertThat(result).isNotNull()
        assertThat(result!!.confidence).isAtLeast(0.84)
        assertThat(result.reasons).contains("Payment app cue")
        assertThat(result.reasons).contains("Payment network/reference cue")
    }

    @Test
    fun gPayTransactionScreenshot_matchesStrictClassifier() {
        val photo = sampleScreenshot(
            ocrText = "Google Pay sent INR 1,200. Transaction completed with UPI reference id 98765.",
        )

        val result = classifier.classify(photo, NOW_MS)

        assertThat(result).isNotNull()
        assertThat(result!!.reasons).contains("Amount-like text")
    }

    @Test
    fun uppercasePaymentText_isCaseInsensitive() {
        val photo = sampleScreenshot(
            ocrText = "GOOGLE PAY PAYMENT SUCCESSFUL INR 500 UPI REFERENCE ID 778899",
        )

        val result = classifier.classify(photo, NOW_MS)

        assertThat(result).isNotNull()
        assertThat(result!!.category).isEqualTo(ArchiveCategory.Payments)
    }

    @Test
    fun bankTransferScreenshot_matchesNetworkAmountAndStatusEvidence() {
        val photo = sampleScreenshot(
            ocrText = "NEFT transaction successful. INR 5,000 debited. Reference id 991122.",
        )

        val result = classifier.classify(photo, NOW_MS)

        assertThat(result).isNotNull()
        assertThat(result!!.reasons).contains("Payment network/reference cue")
        assertThat(result.reasons).contains("Amount-like text")
    }

    @Test
    fun genericScreenshot_isRejected() {
        val photo = sampleScreenshot(
            ocrText = "Lunch plan, movie timings, and a reminder to call later.",
        )

        val result = classifier.classify(photo, NOW_MS)

        assertThat(result).isNull()
    }

    @Test
    fun amountOnlyScreenshot_isRejected() {
        val photo = sampleScreenshot(
            ocrText = "Shopping list total INR 1,200",
        )

        val result = classifier.classify(photo, NOW_MS)

        assertThat(result).isNull()
    }

    @Test
    fun sensitiveDocumentScreenshot_isRejected() {
        val photo = sampleScreenshot(
            ocrText = "Google Pay payment successful Rs. 900 UPI transaction. Aadhaar number visible.",
        )

        val result = classifier.classify(photo, NOW_MS)

        assertThat(result).isNull()
    }

    @Test
    fun favoriteScreenshot_isRejected() {
        val photo = sampleScreenshot(
            isFavorite = true,
            ocrText = "Paytm payment successful Rs. 250 UPI transaction completed.",
        )

        val result = classifier.classify(photo, NOW_MS)

        assertThat(result).isNull()
    }

    @Test
    fun freshScreenshot_isRejected() {
        val photo = sampleScreenshot(
            dateAdded = NOW_MS - (60L * 60L * 1000L),
            ocrText = "BHIM UPI payment successful Rs. 300 transaction completed.",
        )

        val result = classifier.classify(photo, NOW_MS)

        assertThat(result).isNull()
    }

    @Test
    fun missingOcrWithWeakMetadata_isRejected() {
        val photo = sampleScreenshot(
            ocrText = "",
            isOcrProcessed = false,
        )

        val result = classifier.classify(photo, NOW_MS)

        assertThat(result).isNull()
    }

    @Test
    fun paymentCategoryDisabled_rejectsPaymentScreenshot() {
        val photo = sampleScreenshot(
            ocrText = "PhonePe payment successful. Paid Rs. 500 to Ravi by UPI. UTR 123456789.",
        )

        val result = classifier.classify(
            photo = photo,
            nowMs = NOW_MS,
            enabledCategories = setOf(ArchiveCategory.Food),
        )

        assertThat(result).isNull()
    }

    @Test
    fun foodCategoryEnabled_matchesPreparedFoodPhoto() {
        val photo = samplePhoto(
            mlTags = listOf(
                MLTag("food", 0.86f),
                MLTag("prepared_food", 0.86f),
            ),
            archiveFoodCandidate = true,
        )

        val result = classifier.classify(
            photo = photo,
            nowMs = NOW_MS,
            enabledCategories = setOf(ArchiveCategory.Food),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.category).isEqualTo(ArchiveCategory.Food)
        assertThat(result.reasons).contains("Prepared food evidence")
    }

    @Test
    fun foodCategoryEnabled_matchesPackagedFoodWithLocalOcrEvidence() {
        val photo = samplePhoto(
            mlTags = listOf(MLTag("food", 0.91f)),
            archiveFoodCandidate = true,
            ocrText = "NUTRITION FACTS Ingredients: oats sugar. NET WT 200 g. Protein 8 g.",
        )

        val result = classifier.classify(
            photo = photo,
            nowMs = NOW_MS,
            enabledCategories = setOf(ArchiveCategory.Food),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.category).isEqualTo(ArchiveCategory.Food)
        assertThat(result.reasons).contains("Packaged food evidence")
    }

    @Test
    fun packagedFoodEvidenceWithoutSemanticCandidate_isRejected() {
        val photo = samplePhoto(
            mlTags = listOf(MLTag("food", 0.91f)),
            archiveFoodCandidate = false,
            ocrText = "Nutrition facts ingredients net weight 200 g",
        )

        val result = classifier.classify(
            photo = photo,
            nowMs = NOW_MS,
            enabledCategories = setOf(ArchiveCategory.Food),
        )

        assertThat(result).isNull()
    }

    @Test
    fun packagedFoodWithBirdSignal_isRejectedForArchive() {
        val photo = samplePhoto(
            mlTags = listOf(
                MLTag("food", 0.91f),
                MLTag("bird", 0.88f),
            ),
            archiveFoodCandidate = true,
            ocrText = "Nutrition facts ingredients net weight 200 g",
        )

        val result = classifier.classify(
            photo = photo,
            nowMs = NOW_MS,
            enabledCategories = setOf(ArchiveCategory.Food),
        )

        assertThat(result).isNull()
    }

    @Test
    fun legacyFoodCandidateWithoutPersistedPreparedEvidence_isRejected() {
        val photo = samplePhoto(
            mlTags = listOf(MLTag("food", 0.92f)),
            archiveFoodCandidate = true,
        )

        val result = classifier.classify(
            photo = photo,
            nowMs = NOW_MS,
            enabledCategories = setOf(ArchiveCategory.Food),
        )

        assertThat(result).isNull()
    }

    @Test
    fun weakPersistedPreparedFoodEvidence_isRejected() {
        val photo = samplePhoto(
            mlTags = listOf(
                MLTag("food", 0.95f),
                MLTag("prepared_food", 0.59f),
            ),
            archiveFoodCandidate = true,
        )

        val result = classifier.classify(
            photo = photo,
            nowMs = NOW_MS,
            enabledCategories = setOf(ArchiveCategory.Food),
        )

        assertThat(result).isNull()
    }

    @Test
    fun foodCategoryDisabled_rejectsFoodPhotoByDefault() {
        val photo = samplePhoto(
            mlTags = listOf(
                MLTag("food", 0.86f),
                MLTag("prepared_food", 0.86f),
            ),
            archiveFoodCandidate = true,
        )

        val result = classifier.classify(photo, NOW_MS)

        assertThat(result).isNull()
    }

    @Test
    fun colorHeuristicFoodTag_isRejectedForArchive() {
        val photo = samplePhoto(
            mlTags = listOf(MLTag("food", 0.91f)),
        )

        val result = classifier.classify(
            photo = photo,
            nowMs = NOW_MS,
            enabledCategories = setOf(ArchiveCategory.Food),
        )

        assertThat(result).isNull()
    }

    @Test
    fun birdWithFoodSignal_isRejectedForArchive() {
        val photo = samplePhoto(
            mlTags = listOf(
                MLTag("food", 0.91f),
                MLTag("prepared_food", 0.91f),
                MLTag("bird", 0.88f),
            ),
            archiveFoodCandidate = true,
        )

        val result = classifier.classify(
            photo = photo,
            nowMs = NOW_MS,
            enabledCategories = setOf(ArchiveCategory.Food),
        )

        assertThat(result).isNull()
    }

    @Test
    fun personWithFoodSignal_isRejectedForArchive() {
        val photo = samplePhoto(
            mlTags = listOf(
                MLTag("food", 0.91f),
                MLTag("prepared_food", 0.91f),
                MLTag("people", 0.90f),
            ),
            archiveFoodCandidate = true,
        )

        val result = classifier.classify(
            photo = photo,
            nowMs = NOW_MS,
            enabledCategories = setOf(ArchiveCategory.Food),
        )

        assertThat(result).isNull()
    }

    @Test
    fun livestockWithFoodSignal_isRejectedForArchive() {
        val livestock = listOf("cow", "buffalo", "goat", "sheep", "lamb", "livestock")
        livestock.forEachIndexed { index, subject ->
            val photo = samplePhoto(
                id = 100L + index,
                mlTags = listOf(
                    MLTag("food", 0.96f),
                    MLTag("prepared_food", 0.96f),
                    MLTag(subject, 0.91f),
                ),
                archiveFoodCandidate = true,
            )

            val result = classifier.classify(
                photo = photo,
                nowMs = NOW_MS,
                enabledCategories = setOf(ArchiveCategory.Food),
            )

            assertThat(result).isNull()
        }
    }

    @Test
    fun weakSemanticFoodSignal_isRejectedForArchive() {
        val photo = samplePhoto(
            mlTags = listOf(MLTag("food", 0.69f)),
        )

        val result = classifier.classify(
            photo = photo,
            nowMs = NOW_MS,
            enabledCategories = setOf(ArchiveCategory.Food),
        )

        assertThat(result).isNull()
    }

    private fun sampleScreenshot(
        id: Long = 1L,
        dateAdded: Long = NOW_MS - (3L * 24L * 60L * 60L * 1000L),
        isFavorite: Boolean = false,
        ocrText: String,
        isOcrProcessed: Boolean = true,
    ): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = "content://media/external/images/media/$id",
            filePath = "/storage/emulated/0/Pictures/Screenshots/Screenshot_$id.png",
            fileName = "Screenshot_$id.png",
            dateAdded = dateAdded,
            year = 2026,
            month = 7,
            dayOfMonth = 1,
            dayOfWeek = 3,
            hourOfDay = 10,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = 512_000L,
            width = 1080,
            height = 2400,
            mimeType = "image/png",
            folderName = "Screenshots",
            folderPath = "Pictures/Screenshots/",
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
            isFavorite = isFavorite,
            ocrText = ocrText,
            isOcrProcessed = isOcrProcessed,
        )
    }

    private fun samplePhoto(
        id: Long = 2L,
        dateAdded: Long = NOW_MS - (3L * 24L * 60L * 60L * 1000L),
        mlTags: List<MLTag> = emptyList(),
        archiveFoodCandidate: Boolean = false,
        ocrText: String = "",
        isOcrProcessed: Boolean = ocrText.isNotBlank(),
    ): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = "content://media/external/images/media/$id",
            filePath = "/storage/emulated/0/DCIM/Camera/IMG_$id.jpg",
            fileName = "IMG_$id.jpg",
            dateAdded = dateAdded,
            year = 2026,
            month = 7,
            dayOfMonth = 1,
            dayOfWeek = 3,
            hourOfDay = 10,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = 1_512_000L,
            width = 3000,
            height = 2400,
            mimeType = "image/jpeg",
            folderName = "Camera",
            folderPath = "DCIM/Camera/",
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
            isFavorite = false,
            mlTags = mlTags,
            isArchiveFoodCandidate = archiveFoodCandidate,
            isMlProcessed = mlTags.isNotEmpty(),
            ocrText = ocrText,
            isOcrProcessed = isOcrProcessed,
        )
    }

    companion object {
        private const val NOW_MS = 1_783_000_000_000L
    }
}
