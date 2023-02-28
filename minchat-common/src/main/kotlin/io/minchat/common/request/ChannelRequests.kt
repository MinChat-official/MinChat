package io.minchat.common.request

import io.minchat.common.entity.*
import kotlinx.serialization.*

/** Admin-only request to create a channel. */
@Serializable
data class ChannelCreateRequest(
	val name: String,
	val description: String
)

/** 
 * Admin-only request to edit a channel.
 * Null values mean that the old value is to be preserved.
 */
@Serializable
data class ChannelModifyRequest(
	val newName: String?,
	val newDescription: String?
)

/** Admin-only request to delete a channel. */
@Serializable
class ChannelDeleteRequest
