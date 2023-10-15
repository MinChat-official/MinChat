package io.minchat.rest.entity

import io.minchat.common.entity.Channel
import io.minchat.common.entity.Channel.AccessMode
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.entity.MinchatChannel
import kotlinx.coroutines.flow.flow

sealed class MinchatChannel(
	val data: Channel,
	override val rest: MinchatRestClient
) : MinchatEntity<MinchatChannel>() {
	override val id by data::id
	/** ID of the [MinchatChannelGroup] this channel belongs to. May be null, which means this channel belongs to a global group. */
	val groupId by data::groupId

	val name by data::name
	val description by data::description

	/** Users who can view this channel. */
	val viewMode by data::viewMode
	/** Users who can message this channel. AccessMode.EVERYONE has no effect here. */
	val sendMode by data::sendMode
	/** The order of this channel as it should appear within its group. Lower order channels come first. */
	val order by data::order

	override suspend fun fetch() =
		rest.getChannel(id)

	/** Creates a message in this channel. */
	suspend fun createMessage(content: String, referencedMessageId: Long? = null) =
		rest.createMessage(id, content, referencedMessageId)

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
	 * However, the returned flow can contain a little more messages than
	 * specified.
	 */
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

	override fun toString() =
		"MinchatChannel(id=$id, name=$name, description=$description)"

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		return data == (other as MinchatChannel).data
	}

	override fun hashCode(): Int = data.hashCode()

	/** Returns true if the given user is allowed to message this channel. */
	fun canBeMessagedBy(user: MinchatUser) =
		data.canBeMessagedBy(user.data)

	/**
	 * Copies this [MinchatChannel] object, allowing to override some of its data values.
	 */
	fun copy(
		name: String = data.name,
		description: String = data.description,
		viewMode: AccessMode = data.viewMode,
		sendMode: AccessMode = data.sendMode,
		type: Channel.Type = data.type,
		groupId: Long? = data.groupId,
		order: Int = data.order
	) =
		data.copy(
			name = name,
			description = description,
			viewMode = viewMode,
			sendMode = sendMode,
			type = type,
			groupId = groupId,
			order = order
		).withClient(rest)
}

class NormalMinchatChannel(
	data: Channel,
	rest: MinchatRestClient
) : MinchatChannel(data, rest) {
	init {
		require(data.type == Channel.Type.NORMAL) {
			"Channel of type ${data.type} is not a normal channel."
		}
	}

	/** 
	 * Edits this Channel. Requires admin rights. 
	 *
	 * This function returns a __new__ channel object.
	 */
	suspend fun edit(
		newName: String? = null,
		newDescription: String? = null,
		newViewMode: AccessMode? = null,
		newSendMode: AccessMode? = null,
		newType: Channel.Type? = null,
		newOrder: Int? = null
	) =
		rest.editChannel(id, newName, newDescription, newViewMode, newSendMode, newType, newOrder)

	/** Deletes this channel. Requires admin rights. */
	suspend fun delete() =
		rest.deleteChannel(id)
}

class MinchatDMChannel(
	data: Channel,
	rest: MinchatRestClient
) : MinchatChannel(data, rest) {
	val user1id get() = data.user1id!!
	val user2id get() = data.user2id!!

	init {
		require(data.type == Channel.Type.DM) {
			"Channel of type ${data.type} is not a DM channel."
		}
	}

	/** Requires the logged-in account to be a participant of this channel. */
	suspend fun delete() =
		rest.deleteChannel(id)

	/** Tries to get the first user. Uses the cache. */
	suspend fun getUser1() =
		rest.cache.getUser(user1id)

	/** Tries to get the second user. Uses the cache. */
	suspend fun getUser2() =
		rest.cache.getUser(user2id)
}

fun Channel.withClient(rest: MinchatRestClient) =
	when (type) {
		Channel.Type.NORMAL -> NormalMinchatChannel(this, rest)
		Channel.Type.DM -> MinchatDMChannel(this, rest)
	}
