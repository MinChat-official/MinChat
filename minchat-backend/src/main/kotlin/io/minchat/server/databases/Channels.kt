package io.minchat.server.databases

import io.minchat.common.entity.Channel
import io.minchat.common.entity.Channel.AccessMode
import org.jetbrains.exposed.sql.ResultRow

object Channels : MinchatEntityTable<Channel>() {
	val name = varchar("name", Channel.nameLength.endInclusive)
	val description = varchar("description", Channel.descriptionLength.endInclusive)

	val viewMode = enumeration<AccessMode>("view-mode").default(AccessMode.EVERYONE)
	val sendMode = enumeration<AccessMode>("send-mode").default(AccessMode.LOGGED_IN)

	override fun createEntity(row: ResultRow) =
		Channel(
			id = row[Channels.id].value,
			name = row[name],
			description = row[description],

			viewMode = row[viewMode],
			sendMode = row[sendMode]
		)
}
