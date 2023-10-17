package io.minchat.rest.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.minchat.common.Route
import io.minchat.common.entity.Message
import io.minchat.common.request.*

class MessageService(baseUrl: String, client: HttpClient) : AbstractRestService(baseUrl, client) {
	suspend fun getMessage(id: Long) = run {
		client.get(makeRouteUrl(Route.Message.fetch, id))
			.body<Message>()
	}

	/** Edits the message with the specified ID using the providen token. */
	suspend fun editMessage(
		id: Long,
		token: String,
		newContent: String
	) = run {
		client.post(makeRouteUrl(Route.Message.edit, id)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(MessageModifyRequest(newContent = newContent))
		}.body<Message>()
	}

	/** Permamently and irreversibly deletes the providen message using the specified token. */
	suspend fun deleteMessage(
		id: Long,
		token: String
	) {
		client.post(makeRouteUrl(Route.Message.delete, id)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(MessageDeleteRequest())
		}
	}
}
