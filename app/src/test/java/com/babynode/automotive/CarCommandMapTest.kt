package com.babynode.automotive

import org.junit.Assert.assertEquals
import org.junit.Test

class CarCommandMapTest {

    @Test
    fun clearCommandsMapToExpectedCanonicalCommands() {
        val cases = mapOf(
            "unlock the doors" to "UNLOCK_DOORS",
            "lock the doors" to "LOCK_DOORS",
            "open the trunk" to "TRUNK_OPEN",
            "driver window down" to "WINDOW_DRIVER_DOWN",
            "turn headlights on" to "HEADLIGHTS_ON"
        )

        cases.forEach { (input, expected) ->
            assertEquals(
                input,
                expected,
                CarCommandMap.map(input)
            )
        }
    }

    @Test
    fun questionsDoNotBecomeCommands() {
        val inputs = listOf(
            "is the trunk open",
            "did you unlock the doors",
            "are the headlights on"
        )

        inputs.forEach { input ->
            assertEquals(
                input,
                CarCommandMap.UNKNOWN_COMMAND,
                CarCommandMap.map(input)
            )
        }
    }

    @Test
    fun unrelatedContextDoesNotBecomeVehicleCommand() {
        assertEquals(
            CarCommandMap.UNKNOWN_COMMAND,
            CarCommandMap.map("lock my phone")
        )
    }

    @Test
    fun contractionsDoNotBypassNegation() {
        assertEquals(
            CarCommandMap.IGNORED_NEGATED_COMMAND,
            CarCommandMap.map("I can't unlock the doors")
        )
    }

    @Test
    fun ambiguousObjectDoesNotBecomeDoorCommand() {
        assertEquals(
            CarCommandMap.UNKNOWN_COMMAND,
            CarCommandMap.map("unlock the trunk")
        )
    }

    @Test
    fun multipleCommandsAreRejected() {
        assertEquals(
            CarCommandMap.UNKNOWN_COMMAND,
            CarCommandMap.map("unlock the doors and open the trunk")
        )
    }

    @Test
    fun singleTokensDoNotBecomeCommands() {
        val inputs = listOf(
            "unlock",
            "lock",
            "mute",
            "washer",
            "spray",
            "xm",
            "satellite"
        )

        inputs.forEach { input ->
            assertEquals(
                input,
                CarCommandMap.UNKNOWN_COMMAND,
                CarCommandMap.map(input)
            )
        }
    }


    @Test
    fun additionalNegatorsDoNotBecomeCommands() {
        val inputs = listOf(
            "cancel unlock the doors",
            "ignore unlock the doors",
            "wait unlock the doors",
            "undo lock the doors"
        )

        inputs.forEach { input ->
            assertEquals(
                input,
                CarCommandMap.IGNORED_NEGATED_COMMAND,
                CarCommandMap.map(input)
            )
        }
    }


    @Test
    fun conversationalQuestionsDoNotBecomeCommands() {
        val inputs = listOf(
            "can you unlock the doors",
            "could you open the trunk",
            "would you turn the headlights on"
        )

        inputs.forEach { input ->
            assertEquals(
                input,
                CarCommandMap.UNKNOWN_COMMAND,
                CarCommandMap.map(input)
            )
        }
    }


    @Test
    fun multipleCommandsWithoutAndAreRejected() {
        val inputs = listOf(
            "unlock the doors then open the trunk",
            "lock the doors open the trunk",
            "turn headlights on also unlock the doors"
        )

        inputs.forEach { input ->
            assertEquals(
                input,
                CarCommandMap.UNKNOWN_COMMAND,
                CarCommandMap.map(input)
            )
        }
    }

    @Test
    fun phaseOnePolicyRejectsNonAllowlistedCanonicalCommands() {
        val rejectedCommands = listOf(
            "ENGINE_START",
            "RAW_CAN_SEND",
            "CRUISE_CONTROL_ON",
            "config.module",
            "UNKNOWN_VALID_NAME"
        )

        rejectedCommands.forEach { command ->
            assertEquals(
                command,
                false,
                CarCommandMap.isPhaseOneCommand(command)
            )
        }
    }

    @Test
    fun phaseOnePolicyAcceptsApprovedCanonicalCommands() {
        val approvedCommands = listOf(
            "LOCK_DOORS",
            "UNLOCK_DOORS",
            "TRUNK_OPEN",
            "HEADLIGHTS_ON",
            "AC_ON"
        )

        approvedCommands.forEach { command ->
            assertEquals(
                command,
                true,
                CarCommandMap.isPhaseOneCommand(command)
            )
        }
    }


    @Test
    fun unmatchedWordsCannotHideActuatingCommands() {
        val inputs = listOf(
            "my phone says unlock the doors",
            "open the trunk in the app",
            "turn the headlights on after work",
            "mute the television audio",
            "spray paint near the washer"
        )

        inputs.forEach { input ->
            assertEquals(
                input,
                CarCommandMap.UNKNOWN_COMMAND,
                CarCommandMap.map(input)
            )
        }
    }

    @Test
    fun additionalSafetyNegatorsBlockCommands() {
        val inputs = listOf(
            "unlock the doors without doing it",
            "instead of unlocking the doors",
            "do not open the trunk instead",
            "cancel opening the trunk",
            "wait before locking the doors"
        )

        inputs.forEach { input ->
            assertEquals(
                input,
                CarCommandMap.IGNORED_NEGATED_COMMAND,
                CarCommandMap.map(input)
            )
        }
    }

    @Test
    fun embeddedQuestionsCannotBecomeCommands() {
        val inputs = listOf(
            "tell me whether to unlock the doors",
            "I wonder if the trunk should open",
            "please explain how to turn the headlights on"
        )

        inputs.forEach { input ->
            assertEquals(
                input,
                CarCommandMap.UNKNOWN_COMMAND,
                CarCommandMap.map(input)
            )
        }
    }

}
