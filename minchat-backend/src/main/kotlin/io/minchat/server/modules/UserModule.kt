package io.minchat.server.modules

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.minchat.common.Route
import io.minchat.common.request.*
import io.minchat.server.databases.Users
import io.minchat.server.util.*
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
					notFound("user $id")
				}
			}
			post(Route.User.edit) {
				val data = call.receive<UserModifyRequest>()

				
			}
			post(Route.User.delete) {
				TODO()
			}
		}
	}
}
