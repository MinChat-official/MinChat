package io.minchat.rest.event

import io.minchat.common.event.*
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.entity.withClient

class MinchatMessageCreate(
	data: MessageCreateEvent,
	client: MinchatRestClient
) : MinchatEvent<MessageCreateEvent>(data, client) {
	val message = data.message.withClient(client)

	val channel get() = message.channel
	val author get() = message.author

	val authorId get() = message.author.id
	val channelId get() = message.channel.id
}

class MinchatMessageModify(
	data: MessageModifyEvent,
	client: MinchatRestClient
) : MinchatEvent<MessageModifyEvent>(data, client) {
	val message = data.message.withClient(client)

	val channel get() = message.channel
	val author get() = message.author

	val authorId get() = message.author.id
	val channelId get() = message.channel.id
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
