package com.swordfish.lemuroid.app.shared.network

import androidx.annotation.StringRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Why a request to a remote service did not produce what was asked for.
 *
 * Naming the failure buys two things: the user reads something in their language instead of a Java
 * exception message, and it can be decided up front whether asking again is worth the wait.
 */
enum class NetworkFailure {
    /** Nothing could be reached: no network, no route, or a connection that died mid transfer. */
    OFFLINE,

    /** The server was reachable but did not answer in time. */
    TIMEOUT,

    /** The server answered with an error. */
    SERVER,

    /** The server says there is nothing at that address. */
    NOT_FOUND,

    /** The server answered with something that cannot be read. */
    INVALID_CONTENT,

    /** What arrived is not what was asked for (a download that does not match its checksum). */
    CORRUPT,

    /** The resource exists but the server will not hand it over. */
    NOT_SHARED,

    UNKNOWN,
    ;

    /** Whether waiting a little and asking again could plausibly end differently. */
    val retryable: Boolean
        get() =
            when (this) {
                OFFLINE, TIMEOUT, SERVER -> true
                NOT_FOUND, INVALID_CONTENT, CORRUPT, NOT_SHARED, UNKNOWN -> false
            }
}

/** An HTTP answer that was not a success, kept as a type so the code can say which one. */
class HttpFailure(val code: Int) : Exception("HTTP $code")

/**
 * A request that failed in a way this app can name.
 *
 * [message] is for the logs and for whoever reads the code; what the user reads is either the
 * generic text for [failure] or, when [messageRes] is set, one written for that exact case.
 */
open class NetworkFailureException(
    val failure: NetworkFailure,
    message: String,
    cause: Throwable? = null,
    @StringRes val messageRes: Int? = null,
) : Exception(message, cause)

/**
 * Names the failure behind anything thrown while talking to a remote service.
 *
 * The cause chain is walked because most of what this app throws is a wrapper: the interesting
 * exception (a timeout, an unknown host, a parse error) is invariably the cause of the one being
 * handled.
 */
fun Throwable.asNetworkFailure(): NetworkFailure {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this

    while (current != null && seen.add(current)) {
        current.failureOrNull()?.let { return it }
        current = current.cause
    }

    return NetworkFailure.UNKNOWN
}

private fun Throwable.failureOrNull(): NetworkFailure? =
    when (this) {
        // A wrapper that did not know better leaves the decision to its cause.
        is NetworkFailureException -> failure.takeIf { it != NetworkFailure.UNKNOWN }
        is HttpFailure -> if (code == 404) NetworkFailure.NOT_FOUND else NetworkFailure.SERVER
        is UnknownHostException, is ConnectException, is NoRouteToHostException, is SocketException ->
            NetworkFailure.OFFLINE
        is SocketTimeoutException -> NetworkFailure.TIMEOUT
        // Reading from a connection that died halfway: worth another go.
        is InterruptedIOException -> NetworkFailure.TIMEOUT
        is SerializationException -> NetworkFailure.INVALID_CONTENT
        is EOFException -> NetworkFailure.OFFLINE
        else -> null
    }

/** How many times a request that looks transient is attempted in total. */
const val DEFAULT_ATTEMPTS = 3

/** How long to wait before the second attempt; each further one waits twice as long. */
const val DEFAULT_INITIAL_DELAY_MS = 800L

/** How many times the wait can double before it stops growing. */
private const val MAX_DOUBLINGS = 4

/**
 * The wait before attempt [attempt] (1-based).
 *
 * Attempt one waits nothing, the second waits [initialDelayMs], the third twice that, and so on up
 * to a ceiling. Pure arithmetic, so the schedule can be tested without waiting for it.
 */
fun retryDelayMs(
    attempt: Int,
    initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
): Long {
    if (attempt <= 1) return 0L

    val doublings = (attempt - 2).coerceAtMost(MAX_DOUBLINGS)
    return initialDelayMs shl doublings
}

/**
 * Runs [block], trying again when it fails for a reason that could go away.
 *
 * Only failures that are worth retrying are retried, and only up to [attempts] times: a wrong
 * address or a malformed answer is reported straight away instead of being asked for three times.
 * Cancellation is never swallowed, so leaving the screen still stops the work.
 */
suspend fun <T> withNetworkRetry(
    attempts: Int = DEFAULT_ATTEMPTS,
    initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    onRetry: (attempt: Int, failure: NetworkFailure) -> Unit = { _, _ -> },
    block: suspend () -> T,
): T {
    var lastFailure: Throwable? = null

    for (attempt in 1..attempts.coerceAtLeast(1)) {
        try {
            return block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            val failure = t.asNetworkFailure()
            lastFailure = t

            if (!failure.retryable || attempt == attempts) throw t

            onRetry(attempt + 1, failure)
            delay(retryDelayMs(attempt + 1, initialDelayMs))
        }
    }

    throw lastFailure ?: IllegalStateException("Nothing was attempted")
}
