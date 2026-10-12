package com.babynode.automotive

import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedFrameReaderTest {

    @Test
    fun readsNewlineTerminatedFrame() {
        val reader =
            BufferedReader(
                StringReader(
                    """{"type":"response"}""" + "\n"
                )
            )

        assertEquals(
            """{"type":"response"}""",
            BoundedFrameReader.read(
                reader =
                    reader,
                maximumLength =
                    4_096
            )
        )
    }

    @Test
    fun removesCarriageReturnFromCrLfFrame() {
        val reader =
            BufferedReader(
                StringReader(
                    "hello\r\n"
                )
            )

        assertEquals(
            "hello",
            BoundedFrameReader.read(
                reader =
                    reader,
                maximumLength =
                    4_096
            )
        )
    }

    @Test
    fun returnsEmptyStringForEmptyFrame() {
        val reader =
            BufferedReader(
                StringReader(
                    "\n"
                )
            )

        assertEquals(
            "",
            BoundedFrameReader.read(
                reader =
                    reader,
                maximumLength =
                    4_096
            )
        )
    }

    @Test
    fun returnsNullAtCleanEndOfStream() {
        val reader =
            BufferedReader(
                StringReader(
                    ""
                )
            )

        assertNull(
            BoundedFrameReader.read(
                reader =
                    reader,
                maximumLength =
                    4_096
            )
        )
    }

    @Test
    fun rejectsFrameEndingBeforeNewline() {
        val reader =
            BufferedReader(
                StringReader(
                    "partial"
                )
            )

        assertThrows(
            IOException::class.java
        ) {
            BoundedFrameReader.read(
                reader =
                    reader,
                maximumLength =
                    4_096
            )
        }
    }

    @Test
    fun rejectsOversizedFrame() {
        val reader =
            BufferedReader(
                StringReader(
                    "ABCDE\n"
                )
            )

        assertThrows(
            IOException::class.java
        ) {
            BoundedFrameReader.read(
                reader =
                    reader,
                maximumLength =
                    4
            )
        }
    }

    @Test
    fun acceptsFrameAtExactMaximumLength() {
        val reader =
            BufferedReader(
                StringReader(
                    "ABCD\n"
                )
            )

        assertEquals(
            "ABCD",
            BoundedFrameReader.read(
                reader =
                    reader,
                maximumLength =
                    4
            )
        )
    }
}
