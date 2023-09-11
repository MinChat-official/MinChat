package io.minchat.common.request

import io.minchat.common.entity.User
import kotlinx.serialization.Serializable

/** Request to log into an existing account. */
@Serializable
class UserLoginRequest(
	val username: String,
	val passwordHash: String
) {
	@Serializable
	data class Response(
		val token: String,
		val user: User
	)
}

/** Request to create a new account. */
@Serializable
class UserRegisterRequest(
	val username: String,
	val nickname: String?,
	val passwordHash: String
) {
	@Serializable
	data class Response(
		val token: String,
		val user: User
	)
}

/**
 * Request to modify the account the providen token belongs to.
 * Null values mean that the old value is to be preserved.
 */
@Serializable
data class UserModifyRequest(
	val newNickname: String?
)

/** 
 * Request to delete the account the providen token belongs to.
 *
 * Once this request is processed, the account becomes permamently and irreversibly
 * inaccessible. The token also becomes invalid.
 */
@Serializable
class UserDeleteRequest

@Serializable
class TokenValidateRequest(val username: String, val token: String)
