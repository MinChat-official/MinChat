package io.minchat.server.databases

import io.minchat.common.entity.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.*

object Messages : MinchatEntityTable<Message>() {
	val content = varchar("content", 1024)
	val author = reference("author", Users)
	val channel = reference("channel", Channels)

	val timestamp = long("timestamp")

	override fun createEntity(row: ResultRow) =
		Message(
			id = row[Messages.id].value,

			content = row[Messages.content],
			author = Users.getById(row[Messages.author].value),
			channel = Channels.getById(row[Messages.channel].value),

			timestamp = row[Messages.timestamp]
		)
}
