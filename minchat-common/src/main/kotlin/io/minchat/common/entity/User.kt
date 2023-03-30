package io.minchat.common.entity

import kotlinx.serialization.Serializable

@Serializable
data class User(
	val id: Long,
	/** The real name of the user. Used for login. */
	val username: String,
	/** The display name of the user. Can be null if the user didn't have their name changed. */
	val nickname: String?,
	/** A unique discriminator used to distinguish users with the same display names. */
	val discriminator: Int,

	val isAdmin: Boolean,
	val isBanned: Boolean,

	var messageCount: Int,
	val lastMessageTimestamp: Long,

	val creationTimestamp: Long
){
	/** Returns [nickname] if the user has one, or [username] otherwise. */
	val displayName get() = nickname ?: username

	/** displayName#discriminator */
	val tag get() = run {
		val disc = discriminator.toString().padStart(4, '0')
		"$displayName#$disc"
	}

	companion object {
		/** The minimum amount of milliseconds a normal user has to wait before sending another message. */
		val messageRateLimit = 2500L
		val nameLength = 3..64
		val passwordLength = 8..40
	}
}
