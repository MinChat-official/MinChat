package io.minchat.rest.event

import io.minchat.common.event.*
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.entity.withClient

class MinchatChannelGroupCreate(
	data: ChannelGroupCreateEvent,
	client: MinchatRestClient
) : MinchatEvent<ChannelGroupCreateEvent>(data, client) {
	val group = data.group.withClient(client)
}

class MinchatChannelGroupModify(
	data: ChannelGroupModifyEvent,
	client: MinchatRestClient
) : MinchatEvent<ChannelGroupModifyEvent>(data, client) {
	val group = data.group.withClient(client)
}

class MinchatChannelGroupDelete(
	data: ChannelGroupDeleteEvent,
	client: MinchatRestClient
) : MinchatEvent<ChannelGroupDeleteEvent>(data, client) {
	val groupId by data::groupId
}
