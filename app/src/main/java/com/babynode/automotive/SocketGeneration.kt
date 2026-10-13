package com.babynode.automotive

import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the lifecycle generation of sockets within one transport.
 *
 * Work associated with an older generation must not modify or close
 * resources belonging to a newer connection.
 */
class SocketGeneration {

    private val generation =
        AtomicLong(
            0L
        )

    fun advance():
        Long {
        return generation.incrementAndGet()
    }

    fun invalidate():
        Long {
        return generation.incrementAndGet()
    }

    fun isCurrent(
        candidate: Long
    ): Boolean {
        return candidate > 0L &&
            generation.get() ==
            candidate
    }
}
