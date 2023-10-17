package io.minchat.rest.entity

import io.minchat.common.entity.ChannelGroup
import io.minchat.rest.MinchatRestClient

class MinchatChannelGroup(
	val data: ChannelGroup,
	override val rest: MinchatRestClient
) : AbstractMinchatEntity<MinchatChannelGroup>() {
	override val id by data::id

	val name by data::name
	val description by data::description

	val order by data::order

	val channels get() = data.channels.map { it.withClient(rest) }

	override suspend fun fetch(): MinchatChannelGroup {
		return rest.getChannelGroup(id)
	}

	fun canBeEditedBy(user: MinchatUser) =
		data.canBeEditedBy(user.data)

	fun canBeDeletedBy(user: MinchatUser) =
		data.canBeDeletedBy(user.data)

	/** Requires admin rights. */
	suspend fun edit(
		newName: String = data.name,
		newDescription: String = data.description,
		newOrder: Int = data.order
	) = rest.editChannelGroup(id, newName, newDescription, newOrder)

	/** Requires admin rights. */
	suspend fun delete() = rest.deleteChannelGroup(id)
}

fun ChannelGroup.withClient(client: MinchatRestClient)
	= MinchatChannelGroup(this, client)
