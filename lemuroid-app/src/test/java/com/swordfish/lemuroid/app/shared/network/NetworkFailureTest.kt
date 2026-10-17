package com.swordfish.lemuroid.app.shared.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkFailureTest {
    @Test
    fun `a host that cannot be reached is an offline failure`() {
        assertEquals(NetworkFailure.OFFLINE, UnknownHostException("store.example").asNetworkFailure())
        assertEquals(NetworkFailure.OFFLINE, ConnectException("refused").asNetworkFailure())
    }

    @Test
    fun `a connection that dies halfway is worth retrying`() {
        assertEquals(NetworkFailure.OFFLINE, EOFException("closed").asNetworkFailure())
    }

    @Test
    fun `a server that does not answer in time is a timeout`() {
        assertEquals(NetworkFailure.TIMEOUT, SocketTimeoutException("took too long").asNetworkFailure())
    }

    @Test
    fun `an http error is a server failure unless it is a 404`() {
        assertEquals(NetworkFailure.SERVER, HttpFailure(503).asNetworkFailure())
        assertEquals(NetworkFailure.SERVER, HttpFailure(500).asNetworkFailure())
        assertEquals(NetworkFailure.NOT_FOUND, HttpFailure(404).asNetworkFailure())
    }

    @Test
    fun `something that cannot be parsed is invalid content`() {
        assertEquals(NetworkFailure.INVALID_CONTENT, SerializationException("not json").asNetworkFailure())
    }

    @Test
    fun `the cause chain is walked to find the real failure`() {
        val wrapped = IOException("the request failed", SocketTimeoutException("timed out"))

        assertEquals(NetworkFailure.TIMEOUT, wrapped.asNetworkFailure())
    }

    @Test
    fun `a named failure keeps its own name`() {
        val corrupt = NetworkFailureException(NetworkFailure.CORRUPT, "checksum mismatch")

        assertEquals(NetworkFailure.CORRUPT, corrupt.asNetworkFailure())
    }

    @Test
    fun `a nameless wrapper lets its cause decide`() {
        val wrapper = NetworkFailureException(NetworkFailure.UNKNOWN, "wrapped", UnknownHostException("no dns"))

        assertEquals(NetworkFailure.OFFLINE, wrapper.asNetworkFailure())
    }

    @Test
    fun `anything else is unknown`() {
        assertEquals(NetworkFailure.UNKNOWN, IllegalStateException("surprise").asNetworkFailure())
    }

    @Test
    fun `only failures that could go away are retried`() {
        assertTrue(NetworkFailure.OFFLINE.retryable)
        assertTrue(NetworkFailure.TIMEOUT.retryable)
        assertTrue(NetworkFailure.SERVER.retryable)

        assertFalse(NetworkFailure.NOT_FOUND.retryable)
        assertFalse(NetworkFailure.INVALID_CONTENT.retryable)
        assertFalse(NetworkFailure.CORRUPT.retryable)
        assertFalse(NetworkFailure.NOT_SHARED.retryable)
        assertFalse(NetworkFailure.UNKNOWN.retryable)
    }

    @Test
    fun `the first attempt waits nothing and each further one doubles`() {
        assertEquals(0L, retryDelayMs(1, initialDelayMs = 800L))
        assertEquals(800L, retryDelayMs(2, initialDelayMs = 800L))
        assertEquals(1600L, retryDelayMs(3, initialDelayMs = 800L))
        assertEquals(3200L, retryDelayMs(4, initialDelayMs = 800L))
    }

    @Test
    fun `the wait stops growing after a few doublings`() {
        val capped = retryDelayMs(20, initialDelayMs = 800L)

        assertEquals(retryDelayMs(2 + 4, initialDelayMs = 800L), capped)
    }

    @Test
    fun `a retryable failure is attempted again and can then succeed`() =
        runBlocking {
            var calls = 0

            val result =
                withNetworkRetry(attempts = 3, initialDelayMs = 0L) {
                    calls++
                    if (calls < 3) throw SocketTimeoutException("not yet")
                    "downloaded"
                }

            assertEquals("downloaded", result)
            assertEquals(3, calls)
        }

    @Test
    fun `a failure that will not go away is not attempted again`() =
        runBlocking {
            var calls = 0

            val failure =
                runCatching {
                    withNetworkRetry(attempts = 3, initialDelayMs = 0L) {
                        calls++
                        throw HttpFailure(404)
                    }
                }.exceptionOrNull()

            assertEquals(1, calls)
            assertEquals(NetworkFailure.NOT_FOUND, failure?.asNetworkFailure())
        }

    @Test
    fun `the last attempt is reported when every one of them fails`() =
        runBlocking {
            var calls = 0

            val failure =
                runCatching {
                    withNetworkRetry(attempts = 3, initialDelayMs = 0L) {
                        calls++
                        throw UnknownHostException("never resolves")
                    }
                }.exceptionOrNull()

            assertEquals(3, calls)
            assertEquals(NetworkFailure.OFFLINE, failure?.asNetworkFailure())
        }

    @Test
    fun `a single attempt does not retry`() =
        runBlocking {
            var calls = 0

            runCatching {
                withNetworkRetry(attempts = 1, initialDelayMs = 0L) {
                    calls++
                    throw SocketTimeoutException("too slow")
                }
            }

            assertEquals(1, calls)
        }
}
