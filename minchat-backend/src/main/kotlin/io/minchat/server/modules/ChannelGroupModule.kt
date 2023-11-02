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

class ChannelGroupModule : AbstractMinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.Channel.fetch) {
				val id = call.parameters.getOrFail<Long>("id")

				newSuspendedTransaction {
					call.respond(ChannelGroups.getById(id))
				}
			}

			get(Route.ChannelGroup.all) {
				val token = call.tokenOrNull()

				newSuspendedTransaction {
					// Get the user if possible
					val user = token?.let { Users.getByToken(it) }

					// Get all available groups in the raw form (without creating entities yet)
					val rawGroups = ChannelGroups.selectAll()
						.orderBy(ChannelGroups.order to SortOrder.ASC, ChannelGroups.id to SortOrder.ASC)
						.toList()

					// Get all channels and associate them with the groups
					val groups = Channels.select({ Channels.type eq Channel.Type.NORMAL })
						.orderBy(Channels.order to SortOrder.ASC, Channels.id to SortOrder.ASC)
						.toList()
						.map { Channels.createEntity(it) }
						.filter {
							it.viewMode == Channel.AccessMode.EVERYONE
							|| (user != null && user.canViewChannel(it))
						}
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

				val name = data.name.nameConvention()

				newSuspendedTransaction {
					call.requireAdmin()
					validate(name, data.description)

					if (ChannelGroups.select { ChannelGroups.name eq name }.any()) {
						illegalInput("A group with this name already exists.")
					}

					val group = ChannelGroups.insert {
						it[ChannelGroups.name] = name
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

				newSuspendedTransaction {
					call.requireAdmin()
					validate(newName, newDescription)

					val oldGroup = ChannelGroups.getById(id)
					if (newName != null && newName != oldGroup.name && ChannelGroups.select { ChannelGroups.name eq newName }.any()) {
						illegalInput("A group with this name already exists.")
					}

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

					Log.info { "Channel group was edited: #${id}." }
				}
			}

			post(Route.ChannelGroup.delete) {
				val id = call.parameters.getOrFail<Long>("id")

				newSuspendedTransaction {
					call.requireAdmin()

					val group = ChannelGroups.getById(id)

					// Move all channels from deleted group to the global one
					Channels.update({ Channels.groupId eq id }) {
						it[groupId] = null
					}

					ChannelGroups.deleteWhere { ChannelGroups.id eq id }.throwIfNotFound { "no such group." }

					Log.info { "Channel group was deleted: ${group.loggable()}." }
				}
			}
		}
	}

	fun validate(name: String?, description: String?) {
		name?.requireLength(ChannelGroup.nameLength) {
			"Name length must be in range of ${ChannelGroup.nameLength} characters!"
		}
		description?.requireLength(ChannelGroup.descriptionLength) {
			"Description length must be in range of ${ChannelGroup.descriptionLength} characters!"
		}

		if (name != null && ChannelGroups.select { ChannelGroups.name eq name }.any()) {
			illegalInput("A group with this name already exists.")
		}
	}
}
