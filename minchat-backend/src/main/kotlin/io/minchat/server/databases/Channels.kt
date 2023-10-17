package io.minchat.server.databases

import io.minchat.common.entity.Channel
import io.minchat.common.entity.Channel.AccessMode
import org.jetbrains.exposed.sql.ResultRow

object Channels : AbstractMinchatEntityTable<Channel>() {
	val name = varchar("name", Channel.nameLength.endInclusive)
	val description = varchar("description", Channel.descriptionLength.endInclusive)

	val viewMode = enumeration<AccessMode>("view-mode").default(AccessMode.EVERYONE)
	val sendMode = enumeration<AccessMode>("send-mode").default(AccessMode.LOGGED_IN)

	val type = enumeration<Channel.Type>("type").default(Channel.Type.NORMAL)
	val groupId = reference("group-id", ChannelGroups).nullable()

	val order = integer("order").default(0)

	/** Only applicable to DM channels. */
	val user1 = reference("user1", Users).nullable()
	/** Only applicable to DM channels. */
	val user2 = reference("user2", Users).nullable()

	override fun createEntity(row: ResultRow) =
		Channel(
			id = row[Channels.id].value,
			name = row[name],
			description = row[description],

			viewMode = row[viewMode],
			sendMode = row[sendMode],

			type = row[type],
			groupId = row[groupId]?.value,

			order = row[order],

			user1id = row[user1]?.value,
			user2id = row[user2]?.value
		)
}
