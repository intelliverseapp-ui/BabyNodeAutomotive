package com.babynode.automotive

import java.io.BufferedReader
import java.io.IOException

/**
 * Reads one newline-delimited frame while enforcing a strict maximum
 * character length.
 */
object BoundedFrameReader {

    fun read(
        reader: BufferedReader,
        maximumLength: Int
    ): String? {
        require(
            maximumLength >= 0
        ) {
            "Maximum frame length must not be negative"
        }

        val frame =
            StringBuilder()

        var pendingCarriageReturn =
            false

        while (true) {
            val character =
                reader.read()

            if (character == -1) {
                return if (
                    frame.isEmpty() &&
                    !pendingCarriageReturn
                ) {
                    null
                } else {
                    throw IOException(
                        "Frame ended before newline"
                    )
                }
            }

            val nextCharacter =
                character.toChar()

            if (nextCharacter == '\n') {
                return frame.toString()
            }

            if (pendingCarriageReturn) {
                throw IOException(
                    "Frame contains an embedded carriage return"
                )
            }

            if (nextCharacter == '\r') {
                pendingCarriageReturn =
                    true

                continue
            }

            frame.append(
                nextCharacter
            )

            if (frame.length > maximumLength) {
                throw IOException(
                    "Frame exceeds " +
                        "$maximumLength characters"
                )
            }
        }
    }
}
