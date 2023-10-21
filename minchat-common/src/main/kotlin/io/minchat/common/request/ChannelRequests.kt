package io.minchat.common.request

import io.minchat.common.entity.Channel
import kotlinx.serialization.Serializable

/** Admin-only request to create a channel. */
@Serializable
data class ChannelCreateRequest(
	val name: String,
	val description: String,
	val viewMode: Channel.AccessMode,
	val sendMode: Channel.AccessMode,
	val order: Int,
	val groupId: Long?
)

/** Request to create a DM channel with the provided user and the caller. */
@Serializable
data class DMChannelCreateRequest(
	val otherUserId: Long,
	val name: String,
	val description: String,
	val order: Int
)

/** 
 * Admin-only request to edit a channel.
 * Null values mean that the old value is to be preserved.
 */
@Serializable
data class ChannelModifyRequest(
	val newName: String?,
	val newDescription: String?,
	val newViewMode: Channel.AccessMode?,
	val newSendMode: Channel.AccessMode?,
	val newOrder: Int?,
	val newGroupId: Long?
)

/** Admin-only request to delete a channel. */
@Serializable
class ChannelDeleteRequest
