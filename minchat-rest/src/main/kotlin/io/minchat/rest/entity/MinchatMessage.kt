package io.minchat.rest.entity

import io.minchat.common.entity.*
import io.minchat.rest.MinchatRestClient

data class MinchatMessage(
	val data: Message,
	override val rest: MinchatRestClient
) : AbstractMinchatEntity<MinchatMessage>() {
	override val id by data::id
	val channelId by data.channel::id
	val authorId by data.author::id

	val content by data::content
	val author by lazy { data.author.withClient(rest) }
	val channel by lazy { data.channel.withClient(rest) }

	val timestamp by data::timestamp
	val editTimestamp by data::editTimestamp

	/** The id of the message this message references. See [getReferencedMessage]. */
	val referencedMessageId by data::referencedMessageId

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
	 * Retrieves the referenced message. Tries to retrieve it from the cache first, then uses rest.
	 *
	 * Returns null if there's no referenced message (see [referencedMessageId]),
	 * or if it's not found on the server (e.g. deleted).
	 */
	suspend fun getReferencedMessage(): MinchatMessage? {
		val refId = referencedMessageId ?: return null

		return rest.cache.getMessage(refId)
	}

	fun canBeEditedBy(user: MinchatUser) =
		data.canBeEditedBy(user.data)

	fun canBeDeletedBy(user: MinchatUser) =
		data.canBeDeletedBy(user.data)

	override fun toString(): String =
		"MinchatMessage(id=$id, channelId=$channelId, authorId=$authorId, content=$content, timestamp=$timestamp)"

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		return data == (other as MinchatMessage).data
	}

	override fun hashCode(): Int = data.hashCode()

	/** Returns true if the two messages are indistinguishable in terms of visual info. */
	fun similar(other: MinchatMessage): Boolean {
		if (id != other.id) return false
		if (editTimestamp != other.editTimestamp) return false
		if (channelId != other.channelId) return false
		if (authorId != other.authorId) return false
		if (content != other.content) return false
		if (timestamp != other.timestamp) return false
		if (!author.similar(other.author)) return false
		return true
	}

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
