package io.minchat.rest.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.minchat.common.Route
import io.minchat.common.entity.*
import io.minchat.common.request.*

class ChannelService(baseUrl: String, client: HttpClient) : AbstractRestService(baseUrl, client) {
	suspend fun getChannel(
		id: Long,
		token: String? = null
	) = run {
		client.get(makeRouteUrl(Route.Channel.fetch, id)) {
			token?.let { authorizeBearer(it) }
		}.body<Channel>()
	}

	/**
	 * Fetches all channels registered on the server.
	 */
	suspend fun getAllChannels(token: String? = null) = run {
		client.get(makeRouteUrl(Route.Channel.all)) {
			token?.let { authorizeBearer(it) }
		}.body<List<Channel>>()
	}

	/** Fetches all DM channels associated with the user of the provided token. */
	suspend fun getAllDMChannels(token: String) = run {
		client.get(makeRouteUrl(Route.DMChannel.all)) {
			authorizeBearer(token)
		}.body<Map<Long, List<Channel>>>()
	}

	/** 
	 * Fetches a limited number of messages from the specified channel.
	 * See [Route.Channel.messages] for more info.
	 * 
	 * @param fromTimestamp If present, messages sent before or at that moment are not listed.
	 * @param toTimestamp If present, messages sent after that moment are not listed.
	 */
	suspend fun getMessages(
		id: Long,
		token: String? = null,
		fromTimestamp: Long? = null,
		toTimestamp: Long? = null
	) = run {
		client.get(makeRouteUrl(Route.Channel.messages, id)) {
			token?.let { authorizeBearer(it) }
			url {
				fromTimestamp?.let { parameters.append("from", it.toString()) }
				toTimestamp?.let { parameters.append("to", it.toString()) }
			}
		}.body<List<Message>>()
	}

	/** 
	 * Creates a message in the specified channel 
	 * using the bearer of the token as the user.
	 */
	suspend fun createMessage(
		channelId: Long,
		token: String,
		content: String,
		referencedMessageId: Long?
	) = run {
		client.post(makeRouteUrl(Route.Channel.send, channelId)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(MessageCreateRequest(content = content, referencedMessageId = referencedMessageId))
		}.body<Message>()
	}

	/** Creates a new channel using the providen token. */
	suspend fun createChannel(
		token: String,
		name: String,
		description: String,
		viewMode: Channel.AccessMode,
		sendMode: Channel.AccessMode,
		groupId: Long?,
		order: Int
	) = run {
		client.post(makeRouteUrl(Route.Channel.create)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(ChannelCreateRequest(
				name = name,
				description = description,
				viewMode = viewMode,
				sendMode = sendMode,
				groupId = groupId,
				order = order
			))
		}.body<Channel>()
	}

	/** Creates a new DM channel using the provided token. */
	suspend fun createDMChannel(
		token: String,
		otherUserId: Long,
		name: String,
		description: String,
		order: Int
	) = run {
		client.post(makeRouteUrl(Route.DMChannel.create)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(DMChannelCreateRequest(
				otherUserId = otherUserId,
				name = name,
				description = description,
				order = order
			))
		}.body<Channel>()
	}

	/**
	 * Edits the channel with the specified ID using the providen token.
	 *
	 * The channel's group id will be set to null if [newGroupId] is set to -1.
	 */
	suspend fun editChannel(
		id: Long,
		token: String,
		newName: String?,
		newDescription: String?,
		newViewMode: Channel.AccessMode?,
		newSendMode: Channel.AccessMode?,
		newGroupId: Long?,
		newOrder: Int?
	) = run {
		client.post(makeRouteUrl(Route.Channel.edit, id)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(ChannelModifyRequest(
				newName = newName,
				newDescription = newDescription,
				newViewMode = newViewMode,
				newSendMode = newSendMode,
				newGroupId = newGroupId,
				newOrder = newOrder
			))
		}.body<Channel>()
	}

	/** Permanently and irreversibly deletes the provided channel using the specified token. */
	suspend fun deleteChannel(
		id: Long,
		token: String
	) {
		client.post(makeRouteUrl(Route.Channel.delete, id)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(ChannelDeleteRequest())
		}
	}
}
