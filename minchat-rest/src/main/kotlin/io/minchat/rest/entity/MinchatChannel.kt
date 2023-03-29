package io.minchat.rest.entity

import io.minchat.common.entity.Channel
import io.minchat.rest.MinchatRestClient
import kotlinx.coroutines.flow.flow

data class MinchatChannel(
	val data: Channel,
	override val rest: MinchatRestClient
) : MinchatEntity<MinchatChannel>() {
	override val id by data::id

	val name by data::name
	val description by data::description

	override suspend fun fetch() =
		rest.getChannel(id)
	
	/** Creates a message in this channel. */
	suspend fun createMessage(content: String) =
		rest.createMessage(id, content)
	
	/** 
	 * See [MinchatRestClient.getMessagesIn] for more info. 
	 *
	 * This method returns the last N messages matching the criteria 
	 * in the order they were sent in.
	 */
	suspend fun getMessages(
		fromTimestamp: Long? = null,
		toTimestamp: Long? = null
	) = rest.getMessagesIn(id, fromTimestamp, toTimestamp)

	/**
	 * Similar to [getMessages], but returns ALL messages
	 * matching the criteria as an asynchronous flow.
	 *
	 * Unlike [getMessages], this flow returns messages in the
	 * reverse order: newer messages come first.
	 *
	 * This function will use REST to fetch more messages
	 * until there's none left. This can result in a huge amount
	 * of REST api calls, and must therefore be used with caution.
	 *
	 * The [limit] parameter can be used to limit the number of messages.
	 * However, the returned flow can contain a little more messages tham
	 * specified.
	\*/
	suspend fun getAllMessages(
		fromTimestamp: Long? = null,
		toTimestamp: Long? = null,
		limit: Int = Int.MAX_VALUE
	) = flow<MinchatMessage> {
		// get messages returns N oldest messages matching `from < message.timestamp <= to`
		var minTimestamp = fromTimestamp ?: 0L
		var maxTimestamp = toTimestamp ?: Long.MAX_VALUE
		var count = 0

		while (count <= limit) {
			val portion = rest.getMessagesIn(id, minTimestamp, maxTimestamp)

			if (portion.isNotEmpty()) {
				for (i in portion.lastIndex downTo 0) {
					emit(portion[i])
				}
				maxTimestamp = portion.minOf { it.timestamp } - 1
			} else {
				break
			}
			
			count += portion.size
		}
	}
	
	/** 
	 * Edits this Channel. Requires admin rights. 
	 *
	 * This function returns a __new__ channel object.
	 */
	suspend fun edit(newName: String? = null, newDescription: String? = null) =
		rest.editChannel(id, newName, newDescription)

	/** Deletes this channel. Requires admin rights. */
	suspend fun delete() =
		rest.deleteChannel(id)

	override fun toString() =
		"MinchatChannel(id=$id, name=$name, description=$description)"

	/**
	 * Copies this [MinchatChannel] object, allowing to override some of its data values.
	 */
	fun copy(name: String = data.name, description: String = data.description) =
		MinchatChannel(data.copy(name = name, description = description), rest)
}

fun Channel.withClient(rest: MinchatRestClient) =
	MinchatChannel(this, rest)
