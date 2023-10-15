package io.minchat.rest.entity

import io.minchat.common.entity.*
import io.minchat.rest.MinchatRestClient

class MinchatChannelGroup(
	val data: ChannelGroup,
	override val rest: MinchatRestClient
) : MinchatEntity<MinchatChannelGroup>() {
	override val id by data::id

	val name by data::name
	val description by data::description
	val type by data::type

	val order by data::order

	val channels get() = data.channels.map { it.withClient(rest) }

	override suspend fun fetch(): MinchatChannelGroup {
		return rest.getChannelGroup(id)
	}
}

fun ChannelGroup.withClient(client: MinchatRestClient)
	= MinchatChannelGroup(this, client)
