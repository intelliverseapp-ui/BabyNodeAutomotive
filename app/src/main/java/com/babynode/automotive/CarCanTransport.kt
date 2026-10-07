package com.babynode.automotive

import kotlinx.coroutines.flow.Flow

/**
 * Canonical command sent from BabyNode Automotive to BabyNodeCAN.
 *
 * Android remains CAN-agnostic. Vehicle-specific CAN identifiers
 * and payloads belong exclusively in BabyNodeCAN.
 */
data class CanonicalCommand(
    val command: String,
    val value: String? = null
)

/**
 * Abstraction over the BabyNode Automotive transport layer.
 *
 * Implementations must explicitly provide every transport operation.
 * No default no-op behavior is allowed.
 */
interface CarCanTransport {

    /**
     * Connects to the underlying transport.
     */
    suspend fun connect()

    /**
     * Disconnects from the underlying transport.
     *
     * Unlike close(), disconnect() may leave an implementation
     * available for an intentional future reconnection.
     */
    suspend fun disconnect()

    /**
     * Sends one canonical command.
     */
    suspend fun sendCommand(
        command: CanonicalCommand
    )

    /**
     * Exposes connection, request, response, and error events.
     */
    fun status(): Flow<CarStatusEvent>

    /**
     * Permanently releases transport resources.
     *
     * A transport must not be reused after close().
     */
    fun close()
}

/**
 * Events emitted by a CarCanTransport implementation.
 *
 * These classes intentionally contain no Android framework calls,
 * logging side effects, or transport behavior. They remain pure data
 * objects that can be used in ordinary JVM unit tests.
 */
sealed interface CarStatusEvent {

    data class Connected(
        val transportName: String
    ) : CarStatusEvent

    data class Disconnected(
        val transportName: String
    ) : CarStatusEvent

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : CarStatusEvent

    data class CommandSent(
        val command: CanonicalCommand
    ) : CarStatusEvent

    /**
     * Indicates that BabyNodeCAN returned a real, validated response.
     *
     * An "ok" response means BabyNodeCAN accepted the request. It does
     * not by itself prove that the requested physical vehicle action
     * completed.
     */
    data class CommandResponse(
        val commandId: Long,
        val status: String
    ) : CarStatusEvent
}