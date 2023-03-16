package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.minchat.common.Route
import io.minchat.common.entity.*
import io.minchat.common.event.*
import io.minchat.common.request.*
import io.minchat.server.databases.*
import io.minchat.server.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.experimental.*

class MessageModule : MinchatServerModule() {
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

				transaction {
					val userRow = Users.getRawByToken(call.token())

					Messages.update(opWithAdminAccess(userRow[Users.isAdmin],
						common = { Messages.id eq id },
						userOnly = { Messages.author eq userRow[Users.id] }
					)) {
						it[Messages.content] = data.newContent
					}.throwIfNotFound { "A message matching the providen id-author pair does not exist (missing admin rights?)" }
					
					Log.info { "Message $id was edited." }

					server.sendEvent(MessageModifyEvent(Messages.getById(id)))
				}
			}

			post(Route.Message.delete) {
				val id = call.parameters.getOrFail<Long>("id")
				call.receive<MessageDeleteRequest>() // unused

				transaction {
					val userRow = Users.getRawByToken(call.token())

					Messages.update(opWithAdminAccess(userRow[Users.isAdmin],
						common = { Messages.id eq id },
						userOnly = { Messages.author eq userRow[Users.id] }
					)) {
						// actually deleting a message may lead to certain sync issues, so we avoid that
						it[Messages.content] = ""
						it[Messages.isDeleted] = true
					}.throwIfNotFound { "the message does not exist or you lack the permission to delete it." }

					Log.info { "Message $id was deleted." }

					val deletedMessage = Messages.select { Messages.id eq id }.single()
					server.sendEvent(MessageDeleteEvent(
						messageId = deletedMessage[Messages.id].value,
						authorId = deletedMessage[Messages.author].value,
						channelId = deletedMessage[Messages.channel].value,
						byAuthor = deletedMessage[Messages.author].value == userRow[Users.id].value
					))
				}
			}
		}
	}
}
