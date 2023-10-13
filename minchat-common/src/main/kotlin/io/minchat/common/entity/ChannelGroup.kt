package io.minchat.common.entity

import kotlinx.serialization.Serializable

@Serializable
data class ChannelGroup(
	val id: Long,
	val name: String,
	val description: String,
	val type: Type,
	val channels: List<Channel>,

	/** The order of this group as it should appear in the list. Lower order groups come first. */
	val order: Int = 0
) {
	enum class Type {
		/** A group of any message channels other than DM ones. */
		NORMAL,
		/** A group of direct message channels associated with a user. */
		DIRECT
	}

	companion object {
		val nameLength = 3..64
		val descriptionLength = 0..512

		/** A global placeholder channel group for channels with [groupId] == null. */
		val GLOBAL = ChannelGroup(
			-1,
			"global",
			"",
			Type.NORMAL,
			listOf()
		)
	}
}
