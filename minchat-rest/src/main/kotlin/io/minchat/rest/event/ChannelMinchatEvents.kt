package io.minchat.rest.event

import io.minchat.common.event.*
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.entity.withClient

class MinchatChannelCreate(
	data: ChannelCreateEvent,
	client: MinchatRestClient
) : MinchatEvent<ChannelCreateEvent>(data, client) {
	val channel = data.channel.withClient(client)
}

class MinchatChannelModify(
	data: ChannelModifyEvent,
	client: MinchatRestClient
) : MinchatEvent<ChannelModifyEvent>(data, client) {
	val channel = data.channel.withClient(client)
}

class MinchatChannelDelete(
	data: ChannelDeleteEvent,
	client: MinchatRestClient
) : MinchatEvent<ChannelDeleteEvent>(data, client) {
	val channelId by data::channelId
}
