package io.minchat.rest.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.minchat.common.*
import io.minchat.common.request.TokenValidateRequest
import kotlinx.coroutines.delay

class RootService(baseUrl: String, client: HttpClient) : AbstractRestService(baseUrl, client) {
	suspend fun getServerVersion() = run {
		client.get(makeRouteUrl(Route.Root.version))
			.body<BuildVersion>()
	}

	suspend fun validateToken(username: String, token: String, attempts: Int = 5): Boolean {
		for (i in 1..attempts) {
			val code = client.post(makeRouteUrl(Route.Root.tokenValidate)) {
				contentType(ContentType.Application.Json)
				setBody(TokenValidateRequest(username, token))
				expectSuccess = false
			}.status.value

			if (code == 200) return true
			if (code == 403 || code == 404) return false

			delay(5000L)
		}

		throw IllegalStateException("Attempted to validate user token but hadn't received a valid response after $attempts attempts.")
	}
}
