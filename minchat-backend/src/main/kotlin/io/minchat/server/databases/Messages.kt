package io.minchat.server.databases

import io.minchat.common.entity.*
import io.minchat.server.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.*

object Messages : MinchatEntityTable<Message>() {
	val content = varchar("content", Message.contentLength.endInclusive)
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
	
	/** 
	 * Creates a message in the specified channel.
	 * Does not send the corresponding event.
	 * Returns the created row.
	 *
	 * Requires a transaction. 
	 */
	fun createMessageRaw(channelId: Long, authorId: Long, messageContent: String) = insert {
		it[content] = messageContent
		it[author] = authorId
		it[channel] = channelId

		it[timestamp] = System.currentTimeMillis()
	}.resultedValues!!.first()

	/** 
	 * Creates a message in the specified channel
	 * and sends the corresponding event [TODO]. 
	 * Returns the created entity.
	 *
	 * The "channel" and "author" properties of the returned
	 * entity may be different from those providen as parameters.
	 *
	 * Requires a transaction. 
	 */
	suspend fun createMessage(channel: Channel, author: User, messageContent: String) =
		createMessageRaw(channel.id, author.id, messageContent).let {
			// we need to increment the message count too
			val newAuthor = author.copy(messageCount = author.messageCount + 1)
			createEntity(it, newAuthor, channel)
		}
}
