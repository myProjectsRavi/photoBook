package com.photobook.app.ui.viewmodel

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.photobook.app.data.index.IndexPersistence
import com.photobook.app.data.model.PhotoRecord
import kotlin.math.min

class SearchResultsPagingSource(
    private val orderedPhotoIds: List<Long>,
    private val indexPersistence: IndexPersistence,
) : PagingSource<Int, PhotoRecord>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PhotoRecord> {
        return runCatching {
            if (orderedPhotoIds.isEmpty()) {
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null,
                )
            }

            val start = params.key ?: 0
            if (start >= orderedPhotoIds.size) {
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null,
                )
            }

            val endExclusive = min(start + params.loadSize, orderedPhotoIds.size)
            val idsSlice = orderedPhotoIds.subList(start, endExclusive)
            val page = indexPersistence.getByIdsOrdered(idsSlice)

            val prevKey = if (start == 0) {
                null
            } else {
                (start - params.loadSize).coerceAtLeast(0)
            }
            val nextKey = if (endExclusive >= orderedPhotoIds.size) {
                null
            } else {
                endExclusive
            }

            LoadResult.Page(
                data = page,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        }.getOrElse { throwable ->
            LoadResult.Error(throwable)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, PhotoRecord>): Int? {
        val anchor = state.anchorPosition ?: return null
        val pageSize = state.config.pageSize.coerceAtLeast(1)
        return (anchor / pageSize) * pageSize
    }
}
