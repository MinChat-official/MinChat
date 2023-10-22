package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.minchat.common.Route
import io.minchat.common.entity.Channel
import io.minchat.common.request.DMChannelCreateRequest
import io.minchat.server.databases.*
import io.minchat.server.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class DMChannelModule : AbstractMinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.DMChannel.all) {
				val token = call.token()

				newSuspendedTransaction {
					val invokingUser = Users.getByToken(token)

					val allDms = Channels.select {
						(Channels.type eq Channel.Type.DM) and
						((Channels.user1 eq invokingUser.id) or (Channels.user2 eq invokingUser.id))
					}.map(Channels::createEntity).filter {
						it.user1id != null && it.user2id != null // to avoid errors
					}

					val map = allDms.groupBy {
						// For channels where invokingUser is user1, associate with user 2, and vice versa.
						// This results in a map where keys are ids of other users and values are conversations with them
						if (it.user1id == invokingUser.id) {
							it.user2id!!
						} else {
							it.user1id!!
						}
					}

					call.respond(map)
				}
			}

			post(Route.DMChannel.create) {
				val token = call.token()
				val data = call.receive<DMChannelCreateRequest>()

				newSuspendedTransaction {
					val invokingUser = Users.getByToken(token)
					val otherUser = Users.getById(data.otherUserId)

					invokingUser.checkAndUpdateUserPunishments(checkMute = false)
					if (invokingUser.id == otherUser.id) {
						illegalInput("You cannot create a DM channel with yourself.")
					}

					if (Channels.select {
						(Channels.user1 eq invokingUser.id) and (Channels.user2 eq otherUser.id) or
						(Channels.user1 eq otherUser.id) and (Channels.user2 eq invokingUser.id)
					}.count() > Channel.maxDMCount) {
						illegalInput("You cannot create more than ${Channel.maxDMCount} DM channels with one user.")
					}

					val channel = Channels.insert {
						it[name] = data.name
						it[description] = data.description
						it[order] = data.order
						it[type] = Channel.Type.DM
						it[user1] = invokingUser.id
						it[user2] = otherUser.id
					}.resultedValues!!.first().let(Channels::createEntity)

					call.respond(channel)
				}
			}
		}
	}

	override fun createServiceName(): String {
		return "dm-channel"
	}
}
