package io.minchat.common.entity

import io.minchat.common.entity.Channel.AccessMode
import kotlinx.serialization.Serializable

@Serializable
data class Channel(
	val id: Long,
	val name: String,
	val description: String,

	/** Users who can view messages in this channel. */
	val viewMode: AccessMode,
	/** Users who can send messages in this channel. [AccessMode.EVERYONE] has no effect here, only logged-in users can send messages. */
	val sendMode: AccessMode
) {
	fun canBeSeenBy(user: User) =
		user.role.isAdmin || viewMode.isApplicableTo(user.role)

	fun canBeMessagedBy(user: User) =
		user.role.isAdmin || sendMode.isApplicableTo(user.role)

	companion object {
		val nameLength = 3..64
		val descriptionLength = 0..512
		/** The maximum number of messages returned by the `channel/.../messages` route. */
		val messagesPerFetch = 50
	}

	enum class AccessMode(val readableName: String) {
		/** Everyone can access. */
		EVERYONE("everyone"),
		/** Only logged-in users can access. */
		LOGGED_IN("logged-in users"),
		/** Only moderators or higher can access. */
		MODERATORS("moderators only"),
		/** Only admins can access. */
		ADMINS("admins only");

		fun isApplicableTo(role: User.RoleBitSet?) = when (this) {
			EVERYONE -> true
			LOGGED_IN -> role != null
			MODERATORS -> role?.isModerator ?: false
			ADMINS -> role?.isAdmin ?: false
		}
	}
}
