package io.minchat.common.event

import io.minchat.common.entity.Message
import kotlinx.serialization.*

@Serializable
@SerialName("MessageCreate")
data class MessageCreateEvent(
	val message: Message
) : Event() {
	override fun toString() =
		"MessageCreateEvent(author=${message.author.loggable()}, message=${message.loggable()})"
}

@Serializable
@SerialName("MessageModify")
data class MessageModifyEvent(
	val message: Message
) : Event() {
	override fun toString() =
		"MessageModifyEvent(author=${message.author.loggable()}, message=${message.loggable()})"
}

@Serializable
@SerialName("MessageDelete")
data class MessageDeleteEvent(
	val messageId: Long,
	val channelId: Long,
	val authorId: Long,
	val byAuthor: Boolean
) : Event()
