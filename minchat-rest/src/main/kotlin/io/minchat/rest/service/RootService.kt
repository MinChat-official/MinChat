package io.minchat.rest.service

import io.minchat.common.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class RootService(baseUrl: String, client: HttpClient) : RestService(baseUrl, client) {
	suspend fun getServerVersion() = run {
		client.get(makeRouteUrl(Route.Root.version))
			.body<BuildVersion>()
	}
}