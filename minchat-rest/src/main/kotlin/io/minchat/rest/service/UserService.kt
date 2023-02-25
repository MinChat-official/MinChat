package io.minchat.rest.service

import io.ktor.http.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.minchat.common.*
import io.minchat.common.request.*
import io.minchat.rest.*
import org.mindrot.jbcrypt.BCrypt

class UserService(
	val baseUrl: String,
	val client: HttpClient
) {
	/** Hashes and validates the password and performs authorization. Returns a logged-in MinChatAccount. */
	suspend fun login(username: String, password: String): MinChatAccount {
		val hash = hashPasswordLocal(password)
		return loginOrRegister(Route.Auth.register, username, hash)
	}

	/** Hashes and validates the password and tries to register a new account. Returns a logged-in MinChatAccount. */
	suspend fun register(username: String, password: String): MinChatAccount {
		val hash = hashPasswordLocal(password)
		return loginOrRegister(Route.Auth.register, username, hash)
	}

	// for now, login and register routes use the same pattern, so we can just share the code
	private inline suspend fun loginOrRegister(
		route: String, 
		username: String,
		passwordHash: String
	): MinChatAccount {	
		val response = client.post("$baseUrl$route") {
			contentType(ContentType.Application.Json)
			// UserLoginRequest and UserRegisterRequest are the same. just as their responses
			setBody(UserLoginRequest(username, passwordHash))
		}.body<UserLoginRequest.Response>()

		return response.user.withToken(response.token)
	}

	/** 
	 * Hashes the password with BCrypt. 
	 * Uses a cost of 11 to reduce the performance impact. 
	 *
	 * Additionally, throws an exception if the password length is !in 8..40.
	 */
	fun hashPasswordLocal(password: String) = run {
		if (password.length !in 8..40) error("Password must be 8..40 characters long!")

		val salt = BCrypt.gensalt(Constants.hashComplexityPre)
		BCrypt.hashpw(password, salt)
	}
}
