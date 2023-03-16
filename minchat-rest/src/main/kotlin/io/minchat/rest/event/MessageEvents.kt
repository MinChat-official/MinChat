package io.minchat.redt.event

import io.minchat.common.event.*
import io.minchat.rest.entity.*

class MinchatMessageCreate(
	data: MessageCreateEvent,
	client: MinchatRestClient
) : MinchatEvent<_>(data, client) {
	val message = data.message.withClient(client)
}

class MinchatMessageModify(
	data: MessageModifyEvent,
	client: MinchatRestClient
) : MinchatEvent<_>(data, client) {
	val message = data.message.withClient(client)
}

class MinchatMessageDelete(
	data: MessageModifyEvent,
	client: MinchatRestClient
) : MinchatEvent<_>(data, client) {
	val messageId by data.messageId
	val channelId by data.channelId
	val authorId by data.authorId
	val byAuthor by data.byAuthor
}
