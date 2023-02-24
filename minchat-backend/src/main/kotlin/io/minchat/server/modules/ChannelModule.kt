package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.minchat.common.*
import io.minchat.common.Route
import io.minchat.common.request.*
import io.minchat.server.databases.Channels
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
				TODO()
			}

			post(Route.Channel.send) {
				TODO()
			}

			// Admin-only
			post(Route.Channel.create) {
				TODO()
			}

			post(Route.Channel.edit) {
				TODO()
			}

			post(Route.Channel.delete) {
				TODO()
			}
		}
	}
}
