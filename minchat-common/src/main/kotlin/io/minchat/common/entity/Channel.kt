package io.minchat.common.entity

import io.minchat.common.entity.Channel.AccessMode
import kotlinx.serialization.Serializable

@Serializable
data class Channel(
	val id: Long,
	val name: String,
	val description: String,

	val type: Type,
	/** ID of the [ChannelGroup] this channel belongs to. May be null, which means this channel belongs to a global group. */
	val groupId: Long?,

 	/** Users who can view messages in this channel. */
	val viewMode: AccessMode,
	/** Users who can send messages in this channel. [AccessMode.EVERYONE] has no effect here, only logged-in users can send messages. */
	val sendMode: AccessMode,

	/** The order of this channel as it should appear in the list. Lower order channels come first. */
	val order: Int = 0
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

	enum class Type {
		NORMAL,
		DM
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
