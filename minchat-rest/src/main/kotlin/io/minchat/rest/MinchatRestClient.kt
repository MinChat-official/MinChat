package io.minchat.rest

import io.ktor.http.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.minchat.rest.service.*

class MinchatRestClient(
	val baseUrl: String,
	val httpClient: HttpClient = HttpClient(CIO)
) {
	var account: MinChatAccount? = null

	val userService = UserService(baseUrl, httpClient)

	/** 
	 * Attempts to log into the providen MinChat account.
	 * Must be called before using most other methods.
	 *
	 * If the account doesn't exist and [register] is true,
	 * a new account is registered. Otherwise, an exception is thrown.
	 *
	 * Length ranges:
	 * * [username] - 3..40 characters (server-side)
	 * * [password] - 8..40 characters (client-side only)
	 */
	suspend fun login(username: String, password: String, register: Boolean = true) {
		account = try {
			userService.login(username, password)
		} catch (e: ClientRequestException) {
			// 400 bad request is a sign that the account doesn't exist
			if (!register || e.response.status.value != HttpStatusCode.BadRequest.value) {
				throw e
			}

			userService.register(username, password)
		}
	}
}
