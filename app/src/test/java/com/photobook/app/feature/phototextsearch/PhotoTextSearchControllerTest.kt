package com.photobook.app.feature.phototextsearch

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PhotoTextSearchControllerTest {

    @Test
    fun obsoletePhotoResultCannotPublishAfterActivationChanges() = runBlocking {
        val firstResult = CompletableDeferred<PhotoTextLayoutLoadResult>()
        val loads = AtomicInteger(0)
        val source = object : PhotoTextLayoutSource {
            override suspend fun load(source: PhotoTextSourceKey): PhotoTextLayoutLoadResult {
                return if (loads.getAndIncrement() == 0) {
                    firstResult.await()
                } else {
                    PhotoTextLayoutLoadResult.Success(layout("second"))
                }
            }
        }
        val controller = PhotoTextSearchController(source = source, scope = this)

        controller.activate(1L, "content://photo/1")
        controller.open()
        delay(20)
        controller.activate(2L, "content://photo/2")
        controller.open()

        firstResult.complete(PhotoTextLayoutLoadResult.Success(layout("first")))
        waitUntil { controller.state.value.phase == PhotoTextSearchPhase.READY }

        assertThat(controller.state.value.layout?.elements?.single()?.text).isEqualTo("second")
        assertThat(loads.get()).isEqualTo(2)
        controller.dispose()
    }

    @Test
    fun queryEditsReuseOneLayoutWithoutRerunningOcr() = runBlocking {
        val loads = AtomicInteger(0)
        val source = object : PhotoTextLayoutSource {
            override suspend fun load(source: PhotoTextSourceKey): PhotoTextLayoutLoadResult {
                loads.incrementAndGet()
                return PhotoTextLayoutLoadResult.Success(layout("Invoice 001234"))
            }
        }
        val controller = PhotoTextSearchController(source = source, scope = this)

        controller.activate(1L, "content://photo/1")
        controller.open()
        waitUntil { controller.state.value.phase == PhotoTextSearchPhase.READY }
        controller.setQuery("invoice")
        waitUntil { controller.state.value.matches.isNotEmpty() }
        controller.setQuery("1234")
        waitUntil { controller.state.value.query == "1234" && controller.state.value.matches.isNotEmpty() }

        assertThat(loads.get()).isEqualTo(1)
        controller.dispose()
    }

    @Test
    fun queryOver128CharactersIsRejectedWithoutSilentTruncation() = runBlocking {
        val controller = PhotoTextSearchController(
            source = object : PhotoTextLayoutSource {
                override suspend fun load(source: PhotoTextSourceKey) =
                    PhotoTextLayoutLoadResult.Success(layout("hello"))
            },
            scope = this,
        )
        controller.activate(1L, "content://photo/1")
        controller.open()
        waitUntil { controller.state.value.phase == PhotoTextSearchPhase.READY }

        val pasted = "x".repeat(PhotoTextSearchController.MAX_QUERY_CHARACTERS + 1)
        controller.setQuery(pasted)

        assertThat(controller.state.value.query).isEqualTo(pasted)
        assertThat(controller.state.value.queryTooLong).isTrue()
        assertThat(controller.state.value.matches).isEmpty()
        controller.dispose()
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(200) {
            if (predicate()) return
            delay(10)
        }
        error("Timed out waiting for controller state")
    }

    private fun layout(text: String): PhotoTextLayout {
        return PhotoTextLayout(
            uprightWidth = 100,
            uprightHeight = 100,
            elements = listOf(
                TextElement(
                    id = 1,
                    blockId = 0,
                    lineId = 0,
                    text = text,
                    corners = listOf(
                        UnitPoint(0.1f, 0.1f),
                        UnitPoint(0.9f, 0.1f),
                        UnitPoint(0.9f, 0.2f),
                        UnitPoint(0.1f, 0.2f),
                    ),
                ),
            ),
            completeness = PhotoTextCompleteness.COMPLETE,
        )
    }
}
