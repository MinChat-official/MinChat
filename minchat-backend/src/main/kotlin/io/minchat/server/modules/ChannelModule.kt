package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.minchat.common.Route
import io.minchat.common.entity.*
import io.minchat.common.event.*
import io.minchat.common.request.*
import io.minchat.server.databases.*
import io.minchat.server.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

class ChannelModule : AbstractMinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.Channel.fetch) {
				val id = call.parameters.getOrFail<Long>("id")
				val token = call.tokenOrNull()

				newSuspendedTransaction {
					val channel = Channels.getById(id)

					if (channel.type == Channel.Type.DM || channel.viewMode != Channel.AccessMode.EVERYONE) {
						if (token == null) accessDenied("You cannot view this channel unless you are logged in.")

						val user = Users.getByToken(token)
						if (!user.canViewChannel(channel)) accessDenied("You are not allowed to view this channel.")
					}

					call.respond(channel)
				}
			}

			get(Route.Channel.messages) {
				val id = call.parameters.getOrFail<Long>("id")
				val token = call.tokenOrNull()

				val fromTimestamp = call.request.queryParameters["from"]?.toLong() ?: 0L
				val toTimestamp = call.request.queryParameters["to"]?.toLong() ?: System.currentTimeMillis()
				
				newSuspendedTransaction {
					val channel = Channels.getById(id)

					if (channel.type == Channel.Type.DM || channel.viewMode != Channel.AccessMode.EVERYONE) {
						if (token == null) accessDenied("You cannot view this channel unless you are logged in.")

						val user = Users.getByToken(token)
						if (!user.canViewChannel(channel)) accessDenied("Your role is too low to view this channel.")
					}

					val messages: List<Message>
					(Messages innerJoin Users).select {
						not(Messages.isDeleted) and
							(Messages.channel eq id) and
							(Messages.timestamp greater fromTimestamp) and
							(Messages.timestamp lessEq toTimestamp)
					}
						.orderBy(Messages.id to SortOrder.DESC)
						.limit(Channel.messagesPerFetch) // this takes N last messages in descending order
						.toList()
						.reversed() // this makes the order correct
						.map {
							Messages.createEntity(
								row = it,
								author = if (!it[Users.isDeleted]) Users.createEntity(it) else User.deletedUser,
								channel = channel
							)
						}
						.let { messages = it }

					call.respond(messages)
				}
			}

			post(Route.Channel.send) {
				val channelId = call.parameters.getOrFail<Long>("id")
				val data = call.receive<MessageCreateRequest>()

				data.content.requireLength(Message.contentLength) {
					"Content length must be in range of ${Message.contentLength}"
				}
				
				newSuspendedTransaction {
					val user = Users.getByToken(call.token()).checkAndUpdateUserPunishments()
					val channel = Channels.getById(channelId)

					// Cooldown check
					val cooldown = user.lastMessageTimestamp + User.messageRateLimit - System.currentTimeMillis()
					if (cooldown > 0) {
						tooManyRequests("Wait $cooldown milliseconds before sending another message.")
					}

					if (!user.canMessageChannel(channel)) {
						accessDenied("You role is too low to send messages in this channel.")
					}

					val message = Messages.createMessage(channel, user, data.content, data.referencedMessageId)

					Users.update({ Users.id eq user.id }) {
						it[messageCount] = user.messageCount + 1
						it[lastMessageTimestamp] = message.timestamp
					}

					Channels.update({ Channels.id eq channel.id }) {
						it[lastMessageTimestamp] = message.timestamp
					}

					Log.lifecycle { "A new message was sent by ${user.loggable()} in ${channel.loggable()}: ${message.loggable()}" }
					call.respond(message)

					val event = MessageCreateEvent(message)
					if (channel.type == Channel.Type.DM) {
						event.withRecipients(channel.user1id!!, channel.user2id!!)
					}
					server.sendEvent(event)
				}
			}

			// Admin-only
			post(Route.Channel.create) {
				val data = call.receive<ChannelCreateRequest>()
				val channelName = data.name.nameConvention()

				newSuspendedTransaction {
					call.requireAdmin()
					validate(channelName, data.description, forEdit = false)

					val channelRow = Channels.insert {
						it[name] = channelName
						it[description] = data.description
						it[viewMode] = data.viewMode
						it[sendMode] = data.sendMode
						it[order] = data.order
						it[groupId] = data.groupId
					}.resultedValues!!.first()

					val channel = Channels.createEntity(channelRow)
					Log.info { "A new channel was created: ${channel.loggable()}" }

					call.respond(channel)
					server.sendEvent(ChannelCreateEvent(channel))
				}
			}

			post(Route.Channel.edit) {
				val id = call.parameters.getOrFail<Long>("id")
				val data = call.receive<ChannelModifyRequest>()
				val newName = data.newName?.nameConvention()

				newSuspendedTransaction {
					val oldChannel = Channels.getById(id)
					val invokingUser = Users.getByToken(call.token())

					if (!oldChannel.canBeEditedBy(invokingUser)) {
						accessDenied("You cannot edit this channel.")
					}
					if (oldChannel.type == Channel.Type.DM) {
						if (data.newSendMode != null || data.newViewMode != null) {
							illegalInput("Cannot change access modes of a DM channel.")
						}
						if (data.newGroupId != null) {
							illegalInput("DM channels do not support groups.")
						}
					}

					validate(newName, data.newDescription, forEdit = true)

					Channels.update({ Channels.id eq id }) {
						newName?.let { newName ->
							it[name] = newName
						}
						data.newDescription?.let { newDescription ->
							it[description] = newDescription
						}
						data.newViewMode?.let { newViewMode ->
							it[viewMode] = newViewMode
						}
						data.newSendMode?.let { newSendMode ->
							it[sendMode] = newSendMode
						}
						data.newGroupId?.let { newGroupId ->
							if (newGroupId != -1L) {
								it[groupId] = newGroupId
							} else {
								it[groupId] = null
							}
						}
						data.newOrder?.let { newOrder ->
							it[order] = newOrder
						}
					}.throwIfNotFound { "no such channel." }

					val newChannel = Channels.getById(id)
					Log.info { "${oldChannel.loggable()} was edited by ${invokingUser.loggable()}. Now: ${newChannel.loggable()}" }
					call.respond(newChannel)

					val event = ChannelModifyEvent(newChannel)
					if (newChannel.type == Channel.Type.DM) {
						event.withRecipients(newChannel.user1id!!, newChannel.user2id!!)
					}
					server.sendEvent(event)
				}
			}

			post(Route.Channel.delete) {
				val channelId = call.parameters.getOrFail<Long>("id")
				call.receive<ChannelDeleteRequest>()

				transaction {
					val oldChannel = Channels.getById(channelId)
					val invokingUser = Users.getByToken(call.token())

					if (!oldChannel.canBeDeletedBy(invokingUser)) {
						accessDenied("You cannot delete this channel.")
					}

					// First actually delete all associated messages
					Messages.deleteWhere { channel eq channelId }

					Channels.deleteWhere { with(it) { Channels.id eq channelId } }.throwIfNotFound { "no such channel." }

					Log.info { "${oldChannel.loggable()} was deleted by ${invokingUser.loggable()}" }

					call.response.statusOk()

					val event = ChannelDeleteEvent(channelId)
					if (oldChannel.type == Channel.Type.DM) {
						event.withRecipients(oldChannel.user1id!!, oldChannel.user2id!!)
					}
					server.sendEvent(event)
				}
			}

			get(Route.Channel.all) {
				newSuspendedTransaction {
					val list = Channels.select { Channels.type eq Channel.Type.NORMAL }
						.orderBy(
							Channels.groupId to SortOrder.ASC,
							Channels.order to SortOrder.ASC,
							Channels.id to SortOrder.ASC
						)
						.toList()
						.map { Channels.createEntity(it) }

					call.respond(list)
				}
			}
		}
	}

	fun validate(name: String?, description: String?, forEdit: Boolean) {
		name?.requireLength(Channel.nameLength) {
			"Name length must be in range of Channels.nameLength"
		}
		description?.requireLength(Channel.descriptionLength) {
			"Description length must be shorter than 512"
		}

		val maxCount = if (forEdit) 1 else 0
		if (name != null && Channels.select { Channels.name eq name }.count() > maxCount) {
			illegalInput("A channel with this name already exists.")
		}
	}
}
