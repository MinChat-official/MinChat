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
 * Request to modify a user account (the id is provided in the url).
 * Null values mean that the old value is to be preserved.
 */
@Serializable
data class UserModifyRequest(
	val newNickname: String?
)

/** 
 * Request to delete a user account (the id is provided in the url).
 *
 * Once this request is processed, the account becomes permamently and irreversibly
 * inaccessible. The token also becomes invalid.
 */
@Serializable
class UserDeleteRequest

/** Request to validate whether the given token-username pair is valid. */
@Serializable
class TokenValidateRequest(val username: String, val token: String)

/** Request to modify the punishments of a user (the id is provided in the url). */
@Serializable
class UserPunishmentsModifyRequest(
	val newMute: User.Punishment?,
	val newBan: User.Punishment?
)

/** Request to set the avatar of a user to the given icon, or reset to default. */
@Serializable
class IconAvatarSetRequest(val iconName: String?)
