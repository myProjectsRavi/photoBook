package com.photobook.app.feature.qrshare

import java.util.LinkedHashMap

/**
 * Process-local replay/conflict guard shared by receiver assembler instances.
 *
 * It is intentionally bounded and time-limited: receiver lifecycle recreation or "scan another"
 * must not immediately re-publish the same transfer, while old transfer IDs eventually expire.
 */
internal object QrReplayGuard {
    private val blockedTransfers = LinkedHashMap<String, Long>()

    @Synchronized
    fun isBlocked(
        transferId: String,
        nowMs: Long,
    ): Boolean {
        prune(nowMs)
        return blockedTransfers.containsKey(transferId)
    }

    @Synchronized
    fun block(
        transferId: String,
        nowMs: Long,
    ) {
        prune(nowMs)
        blockedTransfers.remove(transferId)
        blockedTransfers[transferId] = nowMs
        while (blockedTransfers.size > MAX_BLOCKED_TRANSFERS) {
            blockedTransfers.remove(blockedTransfers.entries.first().key)
        }
    }

    @Synchronized
    internal fun clearForTests() {
        blockedTransfers.clear()
    }

    private fun prune(nowMs: Long) {
        blockedTransfers.entries.removeIf { nowMs - it.value > REPLAY_TTL_MS }
    }

    private const val MAX_BLOCKED_TRANSFERS = 16
    internal const val REPLAY_TTL_MS = 5 * 60 * 1000L
}
