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

	/** 
	 * Creates an entity from the specified result row,
	 * QUERYING the channel and author objects from the corresponding tables.
	 * This can be slow.
	 */
	override fun createEntity(row: ResultRow) =
		createEntity(
			row,
			Users.getById(row[author].value),
			Channels.getById(row[channel].value)
		)

	/** Creates an entity from the specified result row, using the providen channel and user objects. */
	fun createEntity(row: ResultRow, author: User, channel: Channel) =
		Message(
			id = row[id].value,

			content = row[content],
			author = author,
			channel = channel,

			timestamp = row[timestamp]
		)
}
