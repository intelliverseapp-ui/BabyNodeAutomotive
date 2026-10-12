package com.babynode.automotive

import java.util.concurrent.atomic.AtomicLong

/**
 * Invalidates connection and reconnection work created for an older
 * transport generation.
 */
class ConnectionGeneration {

    private val value =
        AtomicLong(
            0L
        )

    fun current():
        Long {
        return value.get()
    }

    fun advance():
        Long {
        return value.incrementAndGet()
    }

    fun isCurrent(
        generation: Long
    ): Boolean {
        return value.get() ==
            generation
    }
}
