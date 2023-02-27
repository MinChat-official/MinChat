package io.minchat.rest.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

abstract class RestService(
	val baseUrl: String,
	val client: HttpClient
) {
	open fun makeRouteUrl(route: String) =
		baseUrl.removeSuffix("/") + route

	/** Creates a route url with an ID parameter. */
	open fun makeRouteUrl(route: String, id: Long) =
		baseUrl.removeSuffix("/") + route.replace("{id}", id.toString())

	/** Appends the specified authorization header to the builder. */
	fun HttpRequestBuilder.authorizeBearer(token: String) {
		headers {
			append("Authorization", "Bearer $token")
		}
	}
}
