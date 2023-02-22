package io.minchat.rest.service

import io.ktor.client.*
import io.minchat.rest.MinChatAccount
import org.mindrot.jbcrypt.BCrypt

class UserService(
	val client: HttpClient,
	val baseUrl: String 
) {
	/** Hashes and validates the password and performs authorization. Returns a logged-in MinChatAccount. */
	fun login(username: String, password: String) {
		TODO()
	}

	/** 
	 * Hashes the password with BCrypt. 
	 * Uses a cost of 11 to reduce the performance impact. 
	 *
	 * Additionally, throws an exception if the password length is !in 8..40.
	 */
	fun hashPasswordLocal(password: String) = run {
		if (password.length !in 8..40) error("Password must be 8..40 characters long!")

		val salt = BCrypt.gensalt(11)
		BCrypt.hashpw(password, salt)
	}
}
