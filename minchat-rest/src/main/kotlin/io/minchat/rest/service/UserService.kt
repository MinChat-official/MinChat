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
		newNickname: String?
	) = run {
		client.post(makeRouteUrl(Route.User.edit, id)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(UserModifyRequest(newNickname = newNickname))
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

	/** Modifies the punishments of the user with the specified id. Returns the updated user. Admin-only. */
	suspend fun modifyUserPunishments(
		token: String,
		id: Long,
		mute: User.Punishment?,
		ban: User.Punishment?
	): User {
		return client.post(makeRouteUrl(Route.User.modifyPunishments, id)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(UserPunishmentsModifyRequest(mute, ban))
		}.body<User>()
	}

	/** Hashes and validates the password and performs authorization. Returns a logged-in MinchatAccount. */
	suspend fun login(username: String, password: String): MinchatAccount {
		val hash = hashPasswordLocal(password)
		val response = client.post(makeRouteUrl(Route.Auth.login)) {
			contentType(ContentType.Application.Json)
			// UserLoginRequest and UserRegisterRequest are the same. just as their responses
			setBody(UserLoginRequest(username, hash))
		}.body<UserLoginRequest.Response>()

		return response.user.withToken(response.token)

	}

	/** Hashes and validates the password and tries to register a new account. Returns a logged-in MinchatAccount. */
	suspend fun register(username: String, nickname: String?, password: String): MinchatAccount {
		val hash = hashPasswordLocal(password)

		val response = client.post(makeRouteUrl(Route.Auth.register)) {
			contentType(ContentType.Application.Json)
			// UserLoginRequest and UserRegisterRequest are the same. just as their responses
			setBody(UserRegisterRequest(username, nickname, hash))
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
