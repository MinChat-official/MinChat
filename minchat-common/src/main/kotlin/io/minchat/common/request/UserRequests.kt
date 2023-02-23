package io.minchat.common.request

import io.minchat.common.entity.*
import kotlinx.serialization.*

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

@Serializable
class UserRegisterRequest(
	val username: String,
	val passwordHash: String
) {
	@Serializable
	data class Response(
		val token: String,
		val user: User
	)
}

@Serializable
data class UserModifyRequest(
	val token: String,
	val newUsername: String?
) {
	@Serializable
	data class Response(
		val new: User
	)
}

@Serializable
data class UserDeleteRequest(
	val token: String
)
