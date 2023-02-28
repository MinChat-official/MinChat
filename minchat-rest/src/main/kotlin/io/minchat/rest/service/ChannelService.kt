package io.minchat.rest.service

import io.ktor.http.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.minchat.common.*
import io.minchat.common.entity.*
import io.minchat.common.request.*

class ChannelService(baseUrl: String, client: HttpClient) : RestService(baseUrl, client) {
	suspend fun getChannel(id: Long) = run {
		client.get(makeRouteUrl(Route.Channel.fetch, id))
			.body<Channel>()
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
		fromTimestamp: Long? = null,
		toTimestamp: Long? = null
	) = run {
		client.get(makeRouteUrl(Route.Channel.messages, id)) {
			url {
				fromTimestamp?.let { parameters.append("from", it.toString()) }
				toTimestamp?.let { parameters.append("from", it.toString()) }
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
		content: String
	) = run {
		client.post(makeRouteUrl(Route.Channel.send, channelId)) {
			authorizeBearer(token)
			setBody(MessageCreateRequest(content = content))
		}.body<Message>()
	}

	/** Creates a new channel using the providen token. */
	suspend fun createChannel(
		token: String,
		name: String,
		description: String
	) = run {
		client.post(makeRouteUrl(Route.Channel.create)) {
			authorizeBearer(token)
			setBody(ChannelCreateRequest(
				name = name,
				description = description
			))
		}.body<Channel>()
	}

	/** Edits the channel with the specified ID using the providen token. */
	suspend fun editChannel(
		id: Long,
		token: String,
		newName: String?,
		newDescription: String?
	) = run {
		client.post(makeRouteUrl(Route.Channel.edit, id)) {
			authorizeBearer(token)
			setBody(ChannelModifyRequest(
				newName = newName,
				newDescription = newDescription
			))
		}.body<Channel>()
	}

	/** Permamently and irreversibly deletes the providen channel using the specified token. */
	suspend fun deleteChannel(
		id: Long,
		token: String
	) {
		client.post(makeRouteUrl(Route.Channel.delete, id)) {
			authorizeBearer(token)
			setBody(ChannelDeleteRequest())
		}
	}
}
