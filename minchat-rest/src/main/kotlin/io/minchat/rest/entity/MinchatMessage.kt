package io.minchat.rest.entity

import io.minchat.common.entity.*
import io.minchat.rest.MinchatRestClient

data class MinchatMessage(
	val data: Message,
	override val rest: MinchatRestClient
) : MinchatEntity<MinchatMessage>() {
	override val id by data::id
	val channelId by data.channel::id
	val authorId by data.author::id

	val content by data::content
	val author by lazy { MinchatUser(data.author, rest) }
	val channel by lazy { MinchatChannel(data.channel, rest) }

	val timestamp by data::timestamp

	override suspend fun fetch() = 
		rest.getMessage(id)
	
	/** 
	 * Edits this message.
	 * Unless this message was sent by [rest.account.user], requires admin rights. 
	 *
	 * This function returns a __new__ message object.
	 */
	suspend fun edit(newContent: String) =
		rest.editMessage(id, newContent)

	/** 
	 * Deletes this message. Unless this message was sent by [rest.account.user], 
	 * requires admin rights. 
	 */
	suspend fun delete() =
		rest.deleteMessage(id)

	/**
	 * Copies this [MinchatMessage] object, allowing to override some of its data values.
	 */
	fun copy(
		content: String = data.content,
		author: User = data.author,
		channel: Channel = data.channel,
		timestamp: Long = data.timestamp
	) =
		MinchatMessage(data.copy(content = content, author = author, channel = channel, timestamp = timestamp), rest)
}

fun Message.withClient(rest: MinchatRestClient) =
	MinchatMessage(this, rest)
