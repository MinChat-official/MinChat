package io.minchat.server.modules

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.minchat.server.databases.Users
import io.minchat.common.Route
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class UserModule : MinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.User.fetch) {
				val id = call.parameters.getOrFail<Long>("id")

				transaction {
					Users.getByIdOrNull(id)
				}?.let { call.respond(it) } ?: run {
					call.response.status(HttpStatusCode.NotFound)
				}
			}
			post(Route.User.edit) {
				TODO()
			}
			post(Route.User.delete) {
				TODO()
			}
		}
	}
}
