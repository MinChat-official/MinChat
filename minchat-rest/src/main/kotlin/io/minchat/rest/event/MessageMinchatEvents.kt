package io.minchat.rest.event

import io.minchat.common.entity.Message
import io.minchat.common.event.*
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.entity.*

sealed class MinchatMessageEvent<T : Event>(data: T, client: MinchatRestClient) : MinchatEvent<T>(data, client) {
	protected abstract val dataMessage: Message

	private var message_: MinchatMessage? = null
	val message: MinchatMessage
		get() {
			message_?.let { return it }
			return dataMessage.withClient(client).also { message_ = it }
		}

	val channel by message::channel
	val author by message::author

	val authorId by dataMessage.author::id
	val channelId by dataMessage.channel::id
}

class MinchatMessageCreate(
	data: MessageCreateEvent,
	client: MinchatRestClient
) : MinchatMessageEvent<MessageCreateEvent>(data, client) {
	override val dataMessage = data.message
}

class MinchatMessageModify(
	data: MessageModifyEvent,
	client: MinchatRestClient
) : MinchatMessageEvent<MessageModifyEvent>(data, client) {
	override val dataMessage = data.message
}

class MinchatMessageDelete(
	data: MessageDeleteEvent,
	client: MinchatRestClient
) : MinchatEvent<MessageDeleteEvent>(data, client) {
	val messageId by data::messageId
	val channelId by data::channelId
	val authorId by data::authorId
	val byAuthor by data::byAuthor
}
