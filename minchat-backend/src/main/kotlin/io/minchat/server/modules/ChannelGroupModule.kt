package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.minchat.common.Route
import io.minchat.common.entity.*
import io.minchat.common.request.*
import io.minchat.server.databases.*
import io.minchat.server.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ChannelGroupModule : MinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.Channel.fetch) {
				val id = call.parameters.getOrFail<Long>("id")

				newSuspendedTransaction {
					call.respond(ChannelGroups.getById(id))
				}
			}

			get(Route.ChannelGroup.all) {
				newSuspendedTransaction {
					// Get all available groups in the raw form (without creating entities yet)
					val rawGroups = ChannelGroups.selectAll()
						.orderBy(ChannelGroups.order to SortOrder.ASC, ChannelGroups.id to SortOrder.ASC)
						.toList()

					// Get all channels and associate them with the groups
					val groups = Channels.select({ Channels.type eq Channel.Type.NORMAL })
						.orderBy(Channels.order to SortOrder.ASC, Channels.id to SortOrder.ASC)
						.toList()
						.map { Channels.createEntity(it) }
						.groupBy { it.groupId }
						.map { (groupId, channels) ->
							if (groupId == null) {
								ChannelGroup.GLOBAL.copy(channels = channels)
							} else {
								// Create the respective group entities with these channels, or fall back to global.
								rawGroups.find { it[ChannelGroups.id].value == groupId }
									?.let { ChannelGroups.createEntity(it, channels) }
								?: ChannelGroup.GLOBAL.copy(channels = channels)
							}
						}

					call.respond(groups)
				}
			}

			post(Route.ChannelGroup.create) {
				val data = call.receive<ChannelGroupCreateRequest>()

				newSuspendedTransaction {
					call.requireAdmin()

					val group = ChannelGroups.insert {
						it[name] = data.name
						it[description] = data.description
						it[order] = data.order
					}.resultedValues!!.first().let { ChannelGroups.createEntity(it, listOf()) }

					Log.info { "Channel group was created: #${group.name}." }

					call.respond(group)
				}
			}

			post(Route.ChannelGroup.edit) {
				val id = call.parameters.getOrFail<Long>("id")
				val data = call.receive<ChannelGroupModifyRequest>()
				val newName = data.newName?.nameConvention()
				val newDescription = data.newDescription?.nameConvention()

				newName?.requireLength(ChannelGroup.nameLength) {
					"Name length must be in range of ${ChannelGroup.nameLength} characters!"
				}

				newDescription?.requireLength(ChannelGroup.descriptionLength) {
					"Description length must be in range of ${ChannelGroup.descriptionLength} characters!"
				}

				newSuspendedTransaction {
					call.requireAdmin()

					ChannelGroups.update({ ChannelGroups.id eq id }) {
						newName?.let { newName ->
							it[name] = newName
						}
						newDescription?.let { newDescription ->
							it[description] = newDescription
						}
						data.newOrder?.let { newOrder ->
							it[order] = newOrder
						}
					}
				}
			}

			post(Route.ChannelGroup.delete) {
				val id = call.parameters.getOrFail<Long>("id")

				newSuspendedTransaction {
					call.requireAdmin()

					ChannelGroups.deleteWhere { ChannelGroups.id eq id }.throwIfNotFound { "no such group." }
				}
			}
		}
	}
}
