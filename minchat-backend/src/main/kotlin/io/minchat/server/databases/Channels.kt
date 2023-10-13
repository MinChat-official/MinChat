package io.minchat.server.databases

import io.minchat.common.entity.Channel
import io.minchat.common.entity.Channel.AccessMode
import org.jetbrains.exposed.sql.ResultRow

object Channels : MinchatEntityTable<Channel>() {
	val name = varchar("name", Channel.nameLength.endInclusive)
	val description = varchar("description", Channel.descriptionLength.endInclusive)

	val viewMode = enumeration<AccessMode>("view-mode").default(AccessMode.EVERYONE)
	val sendMode = enumeration<AccessMode>("send-mode").default(AccessMode.LOGGED_IN)

	val type = enumeration<Channel.Type>("type").default(Channel.Type.NORMAL)
	val groupId = reference("group-id", ChannelGroups).nullable()

	val order = integer("order").default(0)

	override fun createEntity(row: ResultRow) =
		Channel(
			id = row[Channels.id].value,
			name = row[name],
			description = row[description],

			viewMode = row[viewMode],
			sendMode = row[sendMode],

			type = row[type],
			groupId = row[groupId]?.value,

			order = row[order]
		)
}
