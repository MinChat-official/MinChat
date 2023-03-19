package io.minchat.rest.ratelimit

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.*

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
					reset?.let { bucket.refreshOn.set(it * 1000 + 1000) }

					bucket.requestsLeft.set(remaining ?: 1000)
				}
			}
		}
	}

	fun getBucket(context: HttpRequestBuilder) =
		limits.getOrPut(context.url.host) { Bucket() }
		.also { it.checkRefresh() }

	override fun availableRequests(context: HttpRequestBuilder) =
		getBucket(context)
		.requestsLeft.get()

	override fun untilNextRequest(context: HttpRequestBuilder) = run {
		val bucket = getBucket(context)
		if (bucket.requestsLeft.get() > 0) {
			0
		} else {
			bucket.refreshOn.get() - System.currentTimeMillis()
		}
	}

	override fun consumeToken(context: HttpRequestBuilder) = run {
		val bucket = getBucket(context)
		
		if (bucket.requestsLeft.get() <= 0) {
			false
		} else synchronized(bucket) {
			bucket.requestsLeft.decrementAndGet()
			true
		}
	}

	override suspend fun awaitNext(context: HttpRequestBuilder) {
		val bucket = getBucket(context)

		while (bucket.requestsLeft.get() <= 0) {
			delay(10L)
			bucket.checkRefresh()
		}

		synchronized(bucket) {
			bucket.requestsLeft.decrementAndGet()
		}
	}

	data class Bucket(
		@Volatile var capacity: Int = 100,
		val refreshOn: AtomicLong = AtomicLong(0),
		val requestsLeft: AtomicInteger = AtomicInteger(0)
	) {
		fun checkRefresh() = synchronized(this) {
			if (refreshOn.get() < System.currentTimeMillis()) {
				refreshOn.set(Long.MAX_VALUE)
				requestsLeft.set(capacity)
			}
		}
	}
}
