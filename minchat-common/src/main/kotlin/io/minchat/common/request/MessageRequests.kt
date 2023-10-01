package io.minchat.common.request

import kotlinx.serialization.Serializable

/** Request to create a message in the channel. */
@Serializable
data class MessageCreateRequest(
	val content: String,
	val referencedMessageId: Long? = null
)

/** Request to edit a message previously sent in the specified channel. */
@Serializable
data class MessageModifyRequest(
	val newContent: String
)

/** Request to delete a message previously sent in the specified channel. */
@Serializable
class MessageDeleteRequest
