package io.minchat.rest.entity

import io.minchat.common.entity.*
import io.minchat.rest.MinchatRestClient

class MinchatMessage(
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
}

fun Message.withClient(rest: MinchatRestClient) =
	MinchatMessage(this, rest)
