package io.minchat.common.entity

import io.minchat.common.entity.Channel.*
import kotlinx.serialization.Serializable

@Serializable
data class Channel(
	val id: Long,
	/** ID of the [ChannelGroup] this channel belongs to. May be null, which means this channel belongs to a global group. */
	val groupId: Long?,

	val name: String,
	val description: String,
	val lastMessageTimestamp: Long = 0L,

 	/** Users who can view messages in this channel. Has no effect if [type] is [Type.DM]. */
	val viewMode: AccessMode,
	/**
	 * Users who can send messages in this channel. [AccessMode.EVERYONE] has no effect here, only logged-in users can send messages.
	 * Additionally, this has no effect if [this.type] is [Type.DM].
	 */
	val sendMode: AccessMode,

	/** The order of this channel as it should appear within its group. Lower order channels come first. */
	val order: Int = 0,

	val type: Type,

	/** ID of the first user in this (dm) channel. Non-null only if [this.Type] is Type.DM. */
	val user1id: Long?,
	/** ID of the second user in this (dm) channel. Non-null only if [this.Type] is Type.DM. */
	val user2id: Long?
) {
	fun canBeSeenBy(user: User) = when (type) {
		Type.NORMAL -> user.role.isAdmin || viewMode.isApplicableTo(user.role)
		Type.DM -> user.id == user1id || user.id == user2id
	}

	fun canBeMessagedBy(user: User) = when (type) {
		Type.NORMAL -> user.role.isAdmin || sendMode.isApplicableTo(user.role)
		Type.DM -> user.id == user1id || user.id == user2id
	}

	fun canBeEditedBy(user: User) = when (type) {
		Type.NORMAL -> user.role.isAdmin
		Type.DM -> user.id == user1id || user.id == user2id
	}

	fun canBeDeletedBy(user: User) = when (type) {
		Type.NORMAL -> user.role.isAdmin
		Type.DM -> user.id == user1id || user.id == user2id
	}

	/** Creates a short string containing data useful for logging. */
	fun loggable() =
		"Channel($id, #$name, ${description.length} char description)"

	companion object {
		val nameLength = 3..64
		val descriptionLength = 0..512
		/** The maximum number of messages returned by the `channel/.../messages` route. */
		val messagesPerFetch = 50

		/** Maximum number of DM channels per a pair of individual users. */
		val maxDMCount = 8
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
