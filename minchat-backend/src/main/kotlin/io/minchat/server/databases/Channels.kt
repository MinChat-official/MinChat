package io.minchat.server.databases

import io.minchat.common.entity.Channel
import org.jetbrains.exposed.sql.ResultRow

object Channels : MinchatEntityTable<Channel>() {
	val name = varchar("name", Channel.nameLength.endInclusive)
	val description = varchar("description", Channel.descriptionLength.endInclusive)

	override fun createEntity(row: ResultRow) =
		Channel(
			id = row[Channels.id].value,
			name = row[Channels.name],
			description = row[Channels.description]
		)
}
