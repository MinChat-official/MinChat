package io.minchat.rest.event

import io.minchat.common.event.UserModifyEvent
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.entity.withClient

/** @see UserModifyEvent */
class MinchatUserModify(
	data: UserModifyEvent,
	client: MinchatRestClient
) : MinchatEvent<UserModifyEvent>(data, client) {
	val user = data.newUser.withClient(client)
}
