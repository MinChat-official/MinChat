package io.minchat.common.entity

import kotlinx.serialization.Serializable

@Serializable
data class User(
	val id: Long,
	val username: String,
	val discriminator: Int,
	val isAdmin: Boolean,

	val isBanned: Boolean,

	val lastMessageTimestamp: Long,
	val creationTimestamp: Long
) {
	val tag get() = run {
		val disc = discriminator.toString().padStart(4, '0')
		"$username#$disc"
	}
}
