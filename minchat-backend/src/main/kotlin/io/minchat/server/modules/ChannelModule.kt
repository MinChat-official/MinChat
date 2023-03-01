package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.minchat.common.*
import io.minchat.common.Route
import io.minchat.common.request.*
import io.minchat.common.entity.*
import io.minchat.server.databases.*
import io.minchat.server.util.*
import kotlin.system.measureTimeMillis
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.experimental.*

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
					measureTimeMillis { //todo remove after testing
						// using innerJoin to improve performance
						(Messages innerJoin Users).select {
							not(Messages.isDeleted) and
								(Messages.channel eq id) and
								(Messages.timestamp greater fromTimestamp) and
								(Messages.timestamp lessEq toTimestamp)
						}
							.orderBy(Messages.id to SortOrder.DESC)
							.limit(20) // this will take the 20 last messages in a descending order
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
					}.let { Log.debug { "Message query took $it ms" } } 

					call.respond(messages)
				}
			}

			post(Route.Channel.send) {
				val channelId = call.parameters.getOrFail<Long>("id")
				val data = call.receive<MessageCreateRequest>()

				data.content.requireLength(Messages.contentLength) {
					"Content length must be in range of ${Messages.contentLength}"
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
				}
			}

			// Admin-only
			post(Route.Channel.create) {
				val data = call.receive<ChannelCreateRequest>()

				data.name.requireLength(Channels.nameLength) { 
					"Name length must be in range of Channels.nameLength"
				}
				data.description.requireLength(Channels.descriptionLength) { 
					"Description length must be shorter than 512" 
				}

				newSuspendedTransaction {
					call.requireAdmin()

					val channel = Channels.insert {
						it[name] = data.name
						it[description] = data.description
					}.resultedValues!!.first()

					call.respond(Channels.createEntity(channel))
				}

				Log.info { "A new channel was created: #${data.name}" }
			}

			post(Route.Channel.edit) {
				val id = call.parameters.getOrFail<Long>("id")
				val data = call.receive<ChannelModifyRequest>()

				data.newName?.requireLength(Channels.nameLength) { 
					"Name length must be in range of Channels.nameLength"
				}
				data.newDescription?.requireLength(Channels.descriptionLength) { 
					"Description length must be shorter than 512" 
				}

				newSuspendedTransaction {
					call.requireAdmin()

					Channels.update({ Channels.id eq id }) {
						data.newName?.let { newName ->
							it[Channels.name] = newName
						}
						data.newDescription?.let { newDescription ->
							it[Channels.description] = newDescription
						}
					}.throwIfNotFound { "no such channel." }

					call.respond(Channels.getById(id))
				}

				Log.info { "Channel $id was edited." }
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
