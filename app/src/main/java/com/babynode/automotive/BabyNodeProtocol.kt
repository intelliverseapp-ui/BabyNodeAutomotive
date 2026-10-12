package com.babynode.automotive

object BabyNodeProtocol {

    const val VERSION =
        1

    const val MIN_REQUEST_ID =
        1L

    const val MAX_REQUEST_ID =
        2_147_483_647L

    fun validateRequestId(
        id: Any?
    ): Boolean {
        val value =
            when (id) {
                is Byte ->
                    id.toLong()

                is Short ->
                    id.toLong()

                is Int ->
                    id.toLong()

                is Long ->
                    id

                else ->
                    return false
            }

        return value in
            MIN_REQUEST_ID..MAX_REQUEST_ID
    }

    const val MAX_SESSION_ID =
        4_294_967_295L

    fun validateHandshakeCapabilities(
        sessionId: Any?,
        module: Any?,
        canTransmit: Any?,
        vehicleCommands: Any?
    ): Boolean {
        return BabyNodePeerContract.validateCapabilities(
            sessionId =
                sessionId,
            module =
                module,
            canTransmit =
                canTransmit,
            vehicleCommands =
                vehicleCommands
        )
    }

    fun hasDuplicateTopLevelKeys(
        packet: String
    ): Boolean {
        val keys =
            mutableSetOf<String>()

        var objectDepth =
            0

        var arrayDepth =
            0

        var index =
            0

        var previousSignificantCharacter:
            Char? = null

        while (index < packet.length) {
            val character =
                packet[index]

            when (character) {
                '{' -> {
                    objectDepth +=
                        1

                    previousSignificantCharacter =
                        character

                    index +=
                        1
                }

                '}' -> {
                    objectDepth -=
                        1

                    previousSignificantCharacter =
                        character

                    index +=
                        1
                }

                '[' -> {
                    arrayDepth +=
                        1

                    previousSignificantCharacter =
                        character

                    index +=
                        1
                }

                ']' -> {
                    arrayDepth -=
                        1

                    previousSignificantCharacter =
                        character

                    index +=
                        1
                }

                '"' -> {
                    val parsedString =
                        parseJsonString(
                            packet =
                                packet,
                            openingQuoteIndex =
                                index
                        ) ?: return false

                    val stringValue =
                        parsedString.first

                    val closingQuoteIndex =
                        parsedString.second

                    var nextIndex =
                        closingQuoteIndex +
                            1

                    while (
                        nextIndex <
                        packet.length &&
                        packet[nextIndex].isWhitespace()
                    ) {
                        nextIndex +=
                            1
                    }

                    val isTopLevelKey =
                        objectDepth == 1 &&
                            arrayDepth == 0 &&
                            (
                                previousSignificantCharacter ==
                                    '{' ||
                                    previousSignificantCharacter ==
                                    ','
                            ) &&
                            nextIndex <
                            packet.length &&
                            packet[nextIndex] ==
                                ':'

                    if (
                        isTopLevelKey &&
                        !keys.add(
                            stringValue
                        )
                    ) {
                        return true
                    }

                    previousSignificantCharacter =
                        '"'

                    index =
                        closingQuoteIndex +
                            1
                }

                else -> {
                    if (!character.isWhitespace()) {
                        previousSignificantCharacter =
                            character
                    }

                    index +=
                        1
                }
            }
        }

        return false
    }

    private fun parseJsonString(
        packet: String,
        openingQuoteIndex: Int
    ): Pair<String, Int>? {
        val value =
            StringBuilder()

        var index =
            openingQuoteIndex +
                1

        while (index < packet.length) {
            val character =
                packet[index]

            if (character == '"') {
                return value.toString() to index
            }

            if (character != '\\') {
                value.append(
                    character
                )

                index +=
                    1

                continue
            }

            index +=
                1

            if (index >= packet.length) {
                return null
            }

            when (val escaped = packet[index]) {
                '"',
                '\\',
                '/' ->
                    value.append(
                        escaped
                    )

                'b' ->
                    value.append(
                        '\b'
                    )

                'f' ->
                    value.append(
                        '\u000C'
                    )

                'n' ->
                    value.append(
                        '\n'
                    )

                'r' ->
                    value.append(
                        '\r'
                    )

                't' ->
                    value.append(
                        '\t'
                    )

                'u' -> {
                    val unicodeEnd =
                        index +
                            5

                    if (unicodeEnd > packet.length) {
                        return null
                    }

                    val hexadecimal =
                        packet.substring(
                            index +
                                1,
                            unicodeEnd
                        )

                    val codePoint =
                        hexadecimal.toIntOrNull(
                            radix =
                                16
                        ) ?: return null

                    value.append(
                        codePoint.toChar()
                    )

                    index =
                        unicodeEnd -
                            1
                }

                else ->
                    return null
            }

            index +=
                1
        }

        return null
    }

    const val MAX_REASON_LENGTH =
        256

    fun validateResponseReason(
        reason: Any?
    ): Boolean {
        if (
            reason == null ||
            reason === org.json.JSONObject.NULL
        ) {
            return true
        }

        if (reason !is String) {
            return false
        }

        if (reason.length > MAX_REASON_LENGTH) {
            return false
        }

        return reason.none {
            character ->
            character.code < 0x20 ||
                character.code == 0x7F
        }
    }

    private val VALID_RESPONSE_STATUSES =
        setOf(
            "ok",
            "error",
            "unsupported"
        )

    fun validateResponseType(
        type: Any?
    ): Boolean {
        return type is String &&
            type == "response"
    }

    fun validateResponseStatus(
        status: Any?
    ): Boolean {
        return status is String &&
            status in VALID_RESPONSE_STATUSES
    }

    fun validateResponseCommand(
        command: Any?,
        expectedCommand: String
    ): Boolean {
        return command is String &&
            command == expectedCommand
    }

    fun validateVersion(
        version: Any?
    ): Boolean {
        return when (version) {
            is Byte ->
                version.toInt() == VERSION

            is Short ->
                version.toInt() == VERSION

            is Int ->
                version == VERSION

            is Long ->
                version == VERSION.toLong()

            else ->
                false
        }
    }
}
