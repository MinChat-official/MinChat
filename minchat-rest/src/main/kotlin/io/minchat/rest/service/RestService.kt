package io.minchat.rest.service

import io.ktor.client.*
import io.ktor.client.request.*

abstract class RestService(
	val baseUrl: String,
	val client: HttpClient
) {
	val suffixlessUrl = baseUrl.removeSuffix("/")

	open fun makeRouteUrl(route: String): String {
		return suffixlessUrl + route
	}

	/** Creates a route url with an ID parameter. */
	open fun makeRouteUrl(route: String, id: Long) =
		makeRouteUrl(route.replace("{id}", id.toString()))

	/** Appends the specified authorization header to the builder. */
	fun HttpRequestBuilder.authorizeBearer(token: String) {
		headers {
			append("Authorization", "Bearer $token")
		}
	}
}
