package io.minchat.common.request

import io.minchat.common.entity.*
import kotlinx.serialization.*

/** Request to create a message in the channel. */
@Serializable
data class MessageCreateRequest(
	val content: String
)

/** Request to edit a message previously sent in the specified channel. */
@Serializable
data class MessageModifyRequest(
	val newContent: String
)

/** Request to delete a message previously sent in the specified channel. */
@Serializable
class MessageDeleteRequest