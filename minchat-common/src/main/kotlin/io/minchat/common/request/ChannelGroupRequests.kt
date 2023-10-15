package io.minchat.common.request

import kotlinx.serialization.Serializable

/** Admin-only request to create a group. */
@Serializable
data class ChannelGroupCreateRequest(
	val name: String,
	val description: String,
	val order: Int
)

/**
 * Admin-only request to edit a group.
 * Null values mean that the old value is to be preserved.
 */
@Serializable
data class ChannelGroupModifyRequest(
	val newName: String?,
	val newDescription: String?,
	val newOrder: Int?
)

/** Admin-only request to delete a group. */
@Serializable
class ChannelGroupDeleteRequest
