package com.photobook.app.feature.copytext

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoTextCopyCoordinatorTest {

    @Test
    fun previewSeed_returnsFallback_whenCacheIsEmpty() = runTest {
        val coordinator = PhotoTextCopyCoordinator(
            extractor = FakeExtractor { ExtractedTextResult.Empty },
        )

        val seed = coordinator.previewSeed(
            photoId = 11L,
            fallbackRawText = "hello   world",
        )

        assertThat(seed).isEqualTo(PreviewSeed.Fallback("hello world"))
    }

    @Test
    fun extractForPhoto_cachesSuccess_andNextPreviewUsesCache() = runTest {
        val coordinator = PhotoTextCopyCoordinator(
            extractor = FakeExtractor { ExtractedTextResult.Success("Receipt #18") },
        )
        var callbackResult: ExtractedTextResult? = null

        coordinator.extractForPhoto(
            scope = this,
            photoId = 7L,
            photoUri = "content://photo/7",
        ) { result ->
            callbackResult = result
        }
        advanceUntilIdle()

        val seed = coordinator.previewSeed(photoId = 7L, fallbackRawText = "")

        assertThat(callbackResult).isEqualTo(ExtractedTextResult.Success("Receipt #18"))
        assertThat(seed).isEqualTo(PreviewSeed.Cached("Receipt #18"))
    }

    @Test
    fun cancelActiveRequest_ignoresLateResult() = runTest {
        val coordinator = PhotoTextCopyCoordinator(
            extractor = FakeExtractor {
                delay(1_000)
                ExtractedTextResult.Success("Late Text")
            },
        )
        var callbackCount = 0

        coordinator.extractForPhoto(
            scope = this,
            photoId = 99L,
            photoUri = "content://photo/99",
        ) {
            callbackCount += 1
        }
        coordinator.cancelActiveRequest()
        advanceUntilIdle()

        val seed = coordinator.previewSeed(photoId = 99L, fallbackRawText = "")

        assertThat(callbackCount).isEqualTo(0)
        assertThat(seed).isEqualTo(PreviewSeed.None)
    }

    private class FakeExtractor(
        private val block: suspend (String) -> ExtractedTextResult,
    ) : PhotoTextExtractor {
        override suspend fun extract(photoUri: String): ExtractedTextResult {
            return block(photoUri)
        }
    }
}
