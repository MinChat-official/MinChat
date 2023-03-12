package io.minchat.common.event

import io.minchat.common.entity.*
import kotlinx.serialization.*

@Serializable
@SerialName("MessageCreate")
data class MessageCreateEvent(
	val message: Message
) : Event()

@Serializable
@SerialName("MessageModify")
data class MessageModifyEvent(
	val message: Message
) : Event()

@Serializable
@SerialName("MessageDelete")
data class MessageDeleteEvent(
	val messageId: Long,
	val channelId: Long,
	val authorId: Long,
	val byAuthor: Boolean
) : Event()
