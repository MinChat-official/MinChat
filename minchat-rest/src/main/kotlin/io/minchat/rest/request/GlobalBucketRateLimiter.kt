package io.minchat.rest.ratelimit

import io.ktor.client.statement.*
import io.ktor.client.request.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

/**
 * Limits requests per-host using the bucket system.
 */
class GlobalBucketRateLimiter : AbstractRateLimiter() {
	/** Maps hostnames to the request buckets associated with them. */
	val limits = ConcurrentHashMap<String, Bucket>()

	override fun extractRateLimits(response: HttpResponse) {
		if (response.status.value != 429) {
			val limit = response.headers["X-RateLimit-Limit"]?.toIntOrNull()
			val remaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
			val reset = response.headers["X-RateLimit-Reset"]?.toLongOrNull()
			
			limits.getOrPut(response.call.request.url.host) { Bucket() }.let { bucket ->
				synchronized(bucket) {
					limit?.let { bucket.capacity = it }
					reset?.let { bucket.refreshOn = it * 1000 + 1000 }

					bucket.requestsLeft = remaining ?: 1000
				}
			}
		}
	}

	fun getBucket(context: HttpRequestBuilder) =
		limits.getOrPut(context.url.host) { Bucket() }
		.also { it.checkRefresh() }

	override fun availableRequests(context: HttpRequestBuilder) =
		getBucket(context)
		.requestsLeft

	override fun untilNextRequest(context: HttpRequestBuilder) = run {
		val bucket = getBucket(context)
		if (bucket.requestsLeft > 0) {
			0
		} else {
			bucket.refreshOn - System.currentTimeMillis()
		}
	}

	override fun consumeToken(context: HttpRequestBuilder) = run {
		val bucket = getBucket(context)
		
		if (bucket.requestsLeft <= 0) {
			false
		} else synchronized(bucket) {
			bucket.requestsLeft -= 1
			true
		}
	}

	override suspend fun awaitNext(context: HttpRequestBuilder) {
		val bucket = getBucket(context)

		while (bucket.requestsLeft <= 0) {
			delay(10L)
			bucket.checkRefresh()
		}

		synchronized(bucket) {
			bucket.requestsLeft -= 1
		}
	}

	data class Bucket(
		var capacity: Int = 100,
		var requestsLeft: Int = 100,
		var refreshOn: Long = 0
	) {
		fun checkRefresh() = synchronized(this) {
			if (refreshOn < System.currentTimeMillis()) {
				refreshOn = Long.MAX_VALUE
				requestsLeft = capacity
			}
		}
	}
}
