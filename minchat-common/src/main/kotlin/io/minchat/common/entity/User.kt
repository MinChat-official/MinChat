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
	@Deprecated("Unused. To be removed in later versions of MinChat.", level = DeprecationLevel.ERROR)
	val isBanned: Boolean = false,
	/** If this user is muted, this property indicates the duration and reason. */
	val mute: Punishment? = null,
	/** If this user is banned, this property indicates the duration and reason. */
	val ban: Punishment? = null,

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

		/** A placeholder object returned instead of a deleted user. */
		val deletedUser = User(
			-1,
			"<this user was deleted>",
			"<deleted_user>",
			0,
			false,
			messageCount = 0,
			lastMessageTimestamp = 0L,
			creationTimestamp = 0L
		)

		/** Checks if the provided user is a placeholder. */
		fun isDeletedUser(user: User) = user.id == deletedUser.id
	}

	/**
	 * Represents an abstract punishment.
	 *
	 * May have an expiration date or be indefinite, and may have a reason.
	 *
	 * @param expiresAt an epoch timestamp showing when this punishment expires.
	 *      If null, the punishment is indefinite.
	 * @param reason optional reason for the punishment.
	 */
	@Serializable
	data class Punishment(
		val expiresAt: Long?,
		val reason: String?
	) {
		val isExpired get() = expiresAt == null || System.currentTimeMillis() >= expiresAt

		companion object {
			val reasonLength = 0..128
		}
	}
}
