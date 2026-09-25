package com.photobook.app.ui.screen

/**
 * One-shot gate for explicit in-photo search reveal commands.
 *
 * A gate is created for one photo/search session and initialized with the current request id,
 * so an old command cannot replay when search is reopened or a new page is composed.
 */
internal class PhotoSearchRevealRequestGate(
    initialConsumedRequest: Long,
) {
    private var lastConsumedRequest: Long = initialConsumedRequest

    fun consume(request: Long): Boolean {
        if (request <= lastConsumedRequest) return false
        lastConsumedRequest = request
        return true
    }
}
