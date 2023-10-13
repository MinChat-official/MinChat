package io.minchat.server.databases

import io.minchat.common.entity.*
import io.minchat.server.databases.Channels.groupId
import org.jetbrains.exposed.sql.*

object ChannelGroups : MinchatEntityTable<ChannelGroup>() {
	val name = varchar("name", ChannelGroup.nameLength.endInclusive)
	val description = varchar("description", ChannelGroup.descriptionLength.endInclusive)
	val type = enumeration<ChannelGroup.Type>("type").default(ChannelGroup.Type.NORMAL)

	val order = integer("order").default(0)

	/**
	 * Creates an entity from the specified result row,
	 * QUERYING the respective channels from the channels table.
	 * This can be slow.
	 */
	override fun createEntity(row: ResultRow) = run {
		val id = row[id].value

		createEntity(
			row,
			Channels.select({ groupId eq id }).map {
				Channels.createEntity(it)
			}
		)
	}

	/** Creates an entity from the specified result row, using the provided channels list. */
	fun createEntity(row: ResultRow, channels: List<Channel>) =
		ChannelGroup(
			id = row[id].value,
			name = row[name],
			description = row[description],
			type = row[type],
			channels = channels,

			order = row[order]
		)

}
