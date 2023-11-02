package io.minchat.rest.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.minchat.common.Route
import io.minchat.common.entity.ChannelGroup
import io.minchat.common.request.*

class ChannelGroupService(baseUrl: String, client: HttpClient) : AbstractRestService(baseUrl, client) {
	suspend fun getGroup(id: Long) = run {
		client.get(makeRouteUrl(Route.ChannelGroup.fetch, id))
			.body<ChannelGroup>()
	}

	/**
	 * Fetches all groups registered on the server.
	 */
	suspend fun getAllGroups() = run {
		client.get(makeRouteUrl(Route.ChannelGroup.all))
			.body<List<ChannelGroup>>()
	}

	/** Edits the group with the specified ID using the provided token. */
	suspend fun editGroup(
		id: Long,
		token: String,
		newName: String?,
		newDescription: String?,
		newOrder: Int?
	) = run {
		client.post(makeRouteUrl(Route.ChannelGroup.edit, id)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(
				ChannelGroupModifyRequest(
					newName = newName,
					newDescription = newDescription,
						newOrder = newOrder
				)
			)
		}.body<ChannelGroup>()
	}

	/** Permamently and irreversibly deletes the provided group using the specified token. */
	suspend fun deleteGroup(
		id: Long,
		token: String
	) {
		client.post(makeRouteUrl(Route.ChannelGroup.delete, id)) {
			contentType(ContentType.Application.Json)
			authorizeBearer(token)
			setBody(ChannelGroupDeleteRequest())
		}
	}
}
