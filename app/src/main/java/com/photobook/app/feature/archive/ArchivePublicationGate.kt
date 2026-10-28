package com.photobook.app.feature.archive

import java.util.concurrent.atomic.AtomicLong

internal class ArchivePublicationGate {
    private val revision = AtomicLong(0L)

    fun begin(): Long = revision.incrementAndGet()

    fun invalidate(): Long = revision.incrementAndGet()

    fun isCurrent(candidateRevision: Long): Boolean = revision.get() == candidateRevision
}
