package io.minchat.rest.ratelimit

import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * Limits client requests.
 *
 * Implementations of this class must be thread-safe.
 */
abstract class AbstractRateLimiter {
	/** Extracts rate limit headers from a response and adjusts to them. */
	abstract fun extractRateLimits(response: HttpResponse)

	/** Returns the available number of requests the client has right now. */
	abstract fun availableRequests(context: HttpRequestBuilder): Int

	/** 
	 * Returns the amount of time in milliseconds until
	 * the client can make another request.
	 *
	 * May return 0 if a request can be made right now.
	 */
	abstract fun untilNextRequest(context: HttpRequestBuilder): Long

	/** 
	 * Consumes a request token from a bucket a
	 * ssociated with the host of this request.
	 *
	 * Returns true if there were free tokens.
	 */
	abstract fun consumeToken(context: HttpRequestBuilder): Boolean

	/**
	 * Checks if the client can make this response right now.
	 * If it can not, suspends the current coroutine until it can.
	 *
	 * This method must automatically consume a token.
	 */
	abstract suspend fun awaitNext(context: HttpRequestBuilder)
}
