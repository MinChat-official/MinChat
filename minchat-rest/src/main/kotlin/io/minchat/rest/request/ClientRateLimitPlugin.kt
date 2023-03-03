package io.minchat.rest.ratelimit

import io.ktor.client.plugins.api.*
import io.ktor.client.statement.*
import io.ktor.client.request.*

val ClientRateLimit = createClientPlugin("ClientRateLimit", ::ClientRateLimitConfiguration) {
	val limiter = pluginConfig.limiter ?: GlobalBucketRateLimiter()

	onResponse { response ->
		limiter.extractRateLimits(response)

		// todo: retry on 429?
	}

	onRequest { request, _ ->
		limiter.awaitNext(request)
	}
}

class ClientRateLimitConfiguration {
	/** Which request rate limiter to use. Defaults to [GlobalBucketRateLimiter] if not specified. */
	var limiter: AbstractRateLimiter? = null
	/** Whether to retry a request after receiving a 429 response code. */
	//var retryOnRateLimit = false
}
