package io.minchat.rest.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.minchat.common.*
import io.minchat.common.entity.User
import io.minchat.common.request.*
import io.minchat.rest.*
import org.mindrot.jbcrypt.BCrypt

class UserService(baseUrl: String, client: HttpClient) : RestService(baseUrl, client) {
	/** Gets the user with the specified ID. */
	suspend fun getUser(id: Long) = run {
		client.get(makeRouteUrl(Route.User.fetch, id))
			.body<User>()
	}

	/** Edits the user with the specified ID using the providen token. Null preserves the old value. */
	suspend fun editUser(
		id: Long,
		token: String,
		newUsername: String?
	) = run {
		client.post(makeRouteUrl(Route.User.edit, id)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(UserModifyRequest(newUsername = newUsername))
		}.body<User>()
	}

	/** Permamently and irreversibly deletes the providen user using the specified token. */
	suspend fun deleteUser(
		id: Long,
		token: String
	) {
		client.post(makeRouteUrl(Route.User.delete, id)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(UserDeleteRequest())
		}
	}

	/** Hashes and validates the password and performs authorization. Returns a logged-in MinchatAccount. */
	suspend fun login(username: String, password: String): MinchatAccount {
		val hash = hashPasswordLocal(password)
		return loginOrRegister(Route.Auth.login, username, hash)
	}

	/** Hashes and validates the password and tries to register a new account. Returns a logged-in MinchatAccount. */
	suspend fun register(username: String, password: String): MinchatAccount {
		val hash = hashPasswordLocal(password)
		return loginOrRegister(Route.Auth.register, username, hash)
	}

	// for now, login and register routes use the same pattern, so we can just share the code
	private inline suspend fun loginOrRegister(
		route: String, 
		username: String,
		passwordHash: String
	): MinchatAccount {	
		val response = client.post(makeRouteUrl(route)) {
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

		val salt = Constants.hashSaltPre
		BCrypt.hashpw(password, salt)
	}
}
