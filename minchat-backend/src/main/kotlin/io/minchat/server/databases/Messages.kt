package io.minchat.server.databases

import io.minchat.common.entity.*
import org.jetbrains.exposed.sql.*

object Messages : AbstractMinchatEntityTable<Message>() {
	val content = varchar("content", Message.contentLength.endInclusive)
	val author = reference("author", Users)
	val channel = reference("channel", Channels)

	val timestamp = long("timestamp")
	val editTimestamp = long("edited").nullable().default(null)

	val referencedMessage = reference("reference", Messages).nullable().default(null)

	/** 
	 * Creates an entity from the specified result row,
	 * QUERYING the channel and author objects from the corresponding tables.
	 * This can be slow.
	 */
	override fun createEntity(row: ResultRow) =
		createEntity(
			row,
			Users.getByIdOrPlaceholder(row[author].value),
			Channels.getById(row[channel].value)
		)

	/** Creates an entity from the specified result row, using the providen channel and user objects. */
	fun createEntity(row: ResultRow, author: User, channel: Channel) =
		Message(
			id = row[id].value,

			content = row[content],
			author = author,
			channel = channel,

			timestamp = row[timestamp],
			editTimestamp = row[editTimestamp],

			referencedMessageId = row[referencedMessage]?.value
		)
	
	/** 
	 * Creates a message in the specified channel.
	 * Does not send the corresponding event.
	 * Returns the created row.
	 *
	 * Requires a transaction. 
	 */
	fun createMessageRaw(
		channelId: Long,
		authorId: Long,
		messageContent: String,
		referencedMessageId: Long? = null
	) = insert {
		it[content] = messageContent
		it[author] = authorId
		it[channel] = channelId

		it[timestamp] = System.currentTimeMillis()

		it[referencedMessage] = referencedMessageId
	}.resultedValues!!.first()

	/** 
	 * Creates a message in the specified channel.
	 * Returns the created entity.
	 *
	 * The "channel" and "author" properties of the returned
	 * entity may be different from those providen as parameters.
	 *
	 * Requires a transaction. 
	 */
	suspend fun createMessage(
		channel: Channel,
		author: User,
		messageContent: String,
		referencedMessageId: Long? = null
	) =
		createMessageRaw(channel.id, author.id, messageContent, referencedMessageId).let {
			// we need to increment the message count too
			val newAuthor = author.copy(messageCount = author.messageCount + 1)
			createEntity(it, newAuthor, channel)
		}
}
