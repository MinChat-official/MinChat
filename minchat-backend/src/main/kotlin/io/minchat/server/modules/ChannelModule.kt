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
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

class ChannelModule : MinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.Channel.fetch) {
				val id = call.parameters.getOrFail<Long>("id")

				newSuspendedTransaction {
					call.respond(Channels.getById(id))
				}
			}
			get(Route.Channel.messages) {
				val id = call.parameters.getOrFail<Long>("id")

				val fromTimestamp = call.request.queryParameters["from"]?.toLong() ?: 0L
				val toTimestamp = call.request.queryParameters["to"]?.toLong() ?: System.currentTimeMillis()
				
				newSuspendedTransaction {
					val channel = Channels.getById(id)

					lateinit var messages: List<Message>
					// TODO: this may be unoptimal.
					// consider getting rid of innerJoin.
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
								author = Users.createEntity(it),
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
					val user = Users.getByToken(call.token())
					val channel = Channels.getById(channelId)

					if (user.isBanned) {
						accessDenied("You are banned and can not send messages.")
					}

					val cooldown = user.lastMessageTimestamp + User.messageRateLimit - System.currentTimeMillis()
					if (cooldown > 0 && !user.isAdmin) {
						tooManyRequests("Wait $cooldown milliseconds before sending another message.")
					}

					val message = Messages.createMessage(channel, user, data.content)

					Users.update({ Users.id eq user.id }) {
						it[Users.messageCount] = user.messageCount + 1
						it[Users.lastMessageTimestamp] = message.timestamp
					}

					call.respond(message)
					
					server.sendEvent(MessageCreateEvent(message))
				}
			}

			// Admin-only
			post(Route.Channel.create) {
				val data = call.receive<ChannelCreateRequest>()
				val channelName = data.name.nameConvention()

				channelName.requireLength(Channel.nameLength) {
					"Name length must be in range of Channels.nameLength"
				}
				data.description.requireLength(Channel.descriptionLength) { 
					"Description length must be shorter than 512" 
				}

				newSuspendedTransaction {
					call.requireAdmin()

					val channelRow = Channels.insert {
						it[name] = channelName
						it[description] = data.description
					}.resultedValues!!.first()

					val channel = Channels.createEntity(channelRow)
					Log.info { "A new channel was created: #${channel.name}" }

					call.respond(channel)
					server.sendEvent(ChannelCreateEvent(channel))
				}
			}

			post(Route.Channel.edit) {
				val id = call.parameters.getOrFail<Long>("id")
				val data = call.receive<ChannelModifyRequest>()
				val newName = data.newName?.nameConvention()

				newName?.requireLength(Channel.nameLength) {
					"Name length must be in range of Channels.nameLength"
				}
				data.newDescription?.requireLength(Channel.descriptionLength) { 
					"Description length must be shorter than 512" 
				}

				newSuspendedTransaction {
					call.requireAdmin()

					Channels.update({ Channels.id eq id }) {
						newName?.let { newName ->
							it[Channels.name] = newName
						}
						data.newDescription?.let { newDescription ->
							it[Channels.description] = newDescription
						}
					}.throwIfNotFound { "no such channel." }

					Log.info { "Channel $id was edited." }

					val newChannel = Channels.getById(id)
					call.respond(newChannel)
					server.sendEvent(ChannelModifyEvent(newChannel))
				}
			}

			post(Route.Channel.delete) {
				val channelId = call.parameters.getOrFail<Long>("id")
				call.receive<ChannelDeleteRequest>()

				transaction {
					call.requireAdmin()

					Channels.deleteWhere { with(it) { Channels.id eq channelId } }.throwIfNotFound { "no such channel." }

					// also actually delete all associated messages
					Messages.deleteWhere { with(it) { Messages.channel eq channelId } }
				}

				call.response.statusOk()

				Log.info { "Channel $channelId was deleted." }

				server.sendEvent(ChannelDeleteEvent(channelId))
			}

			get(Route.Channel.all) {
				newSuspendedTransaction {
					val list = Channels.selectAll()
						.orderBy(Channels.id to SortOrder.ASC)
						.toList()
						.map { Channels.createEntity(it) }
					
					call.respond(list)
				}
			}
		}
	}
}
