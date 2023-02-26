package io.minchat.server.databases

import io.minchat.common.entity.*
import io.minchat.server.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.*

object Messages : MinchatEntityTable<Message>() {
	val content = varchar("content", 1024)
	val author = reference("author", Users)
	val channel = reference("channel", Channels)

	val timestamp = long("timestamp")

	val isDeleted = bool("deleted").default(false)
	
	override fun getRawByIdOrNull(id: Long) =
		super.getRawByIdOrNull(id)?.takeIf { !it[isDeleted] }

	override fun createEntity(row: ResultRow) =
		Message(
			id = row[Messages.id].value,

			content = row[Messages.content],
			author = Users.getById(row[Messages.author].value),
			channel = Channels.getById(row[Messages.channel].value),

			timestamp = row[Messages.timestamp]
		)
}
