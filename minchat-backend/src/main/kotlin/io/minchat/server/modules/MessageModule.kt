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

class MessageModule : AbstractMinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.Message.fetch) {
				val id = call.parameters.getOrFail<Long>("id")

				newSuspendedTransaction {
					call.respond(Messages.getById(id))
				}
			}
			
			post(Route.Message.edit) {
				val id = call.parameters.getOrFail<Long>("id")
				val data = call.receive<MessageModifyRequest>()

				data.newContent.requireLength(Message.contentLength) {
					"Content length must be in range of ${Message.contentLength}"
				}

				newSuspendedTransaction {
					val user = Users.getByToken(call.token())
					user.checkAndUpdateUserPunishments()
					val oldMessage = Messages.getById(id)

					require(user.canEditMessage(oldMessage)) {
						"You are not allowed to edit this message."
					}

					Messages.update({ (Messages.id eq id) and (Messages.author eq user.id) }) {
						it[content] = data.newContent
						it[editTimestamp] = System.currentTimeMillis()
					}.throwIfNotFound { "A message matching the providen id-author pair does not exist." }

					val newMessage = Messages.getById(id)
					Log.info { "${oldMessage.loggable()} was edited by ${user.loggable()}. Now: ${newMessage.loggable()}" }
					call.respond(newMessage)

					val event = MessageModifyEvent(newMessage)
					if (newMessage.channel.type == Channel.Type.DM) {
						val channel = newMessage.channel
						event.withRecipients(channel.user1id!!, channel.user2id!!)
					}
					server.sendEvent(event)
				}
			}

			post(Route.Message.delete) {
				val id = call.parameters.getOrFail<Long>("id")
				call.receive<MessageDeleteRequest>() // unused

				transaction {
					val user = Users.getByToken(call.token())
					user.checkAndUpdateUserPunishments()
					val oldMessage = Messages.getById(id)

					Messages.update(opWithAdminAccess(user.role.isAdmin,
						common = { Messages.id eq id },
						userOnly = { Messages.author eq user.id }
					)) {
						// actually deleting a message may lead to certain sync issues, so we avoid that
						it[content] = ""
						it[isDeleted] = true
					}.throwIfNotFound { "the message does not exist or you lack the permission to delete it." }

					Log.info { "Message $id was deleted." }

					val deletedMessage = Messages.select { Messages.id eq id }.single()
					val event = MessageDeleteEvent(
						messageId = deletedMessage[Messages.id].value,
						authorId = deletedMessage[Messages.author].value,
						channelId = deletedMessage[Messages.channel].value,
						byAuthor = deletedMessage[Messages.author].value == user.id
					)
					val channel = oldMessage.channel
					if (channel.type == Channel.Type.DM) {
						event.withRecipients(channel.user1id!!, channel.user2id!!)
					}
					server.sendEvent(event)
				}

				call.response.statusOk()
			}
		}
	}
}
