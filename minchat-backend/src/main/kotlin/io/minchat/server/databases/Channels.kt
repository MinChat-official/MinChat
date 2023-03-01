package io.minchat.server.databases

import io.minchat.common.entity.*
import org.jetbrains.exposed.sql.*

object Channels : MinchatEntityTable<Channel>() {
	val name = varchar("name", 64)
	val description = varchar("description", 512)

	override fun createEntity(row: ResultRow) =
		Channel(
			id = row[Channels.id].value,
			name = row[Channels.name],
			description = row[Channels.description]
		)
	
	companion object {
		val nameLength = 3..64
		val descriptionLength = 0..512
	}
}
