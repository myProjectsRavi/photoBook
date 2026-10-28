package com.photobook.app.verification

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.db.PhotoBookDatabase
import com.photobook.app.data.index.IndexPersistence
import com.photobook.app.ui.viewmodel.SearchResultsPagingSource
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchPagingCancellationInstrumentedTest {

    @Test
    fun cancelledRoomPageLoad_propagatesCancellationInsteadOfPublishingLoadError() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, PhotoBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val persistence = IndexPersistence(context, database, database.photoDao())
            val pagingSource = SearchResultsPagingSource(listOf(1L), persistence)
            var observed: Any? = null

            // A canceled Paging generation must not turn Room's cancellation into a
            // visible retryable LoadResult.Error. A nonempty ID list reaches Room I/O.
            val worker = launch(start = CoroutineStart.UNDISPATCHED) {
                coroutineContext.cancel()
                observed = try {
                    pagingSource.load(
                        PagingSource.LoadParams.Refresh<Int>(
                            key = null,
                            loadSize = 1,
                            placeholdersEnabled = false,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    cancelled
                }
            }
            worker.join()
            assertTrue(
                "A cancelled Room page must propagate CancellationException, not LoadResult.Error",
                observed is CancellationException,
            )
        } finally {
            database.close()
        }
    }
}
