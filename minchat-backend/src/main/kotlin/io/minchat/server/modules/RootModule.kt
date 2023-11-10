package io.minchat.server.modules

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.minchat.common.*
import io.minchat.common.Route
import io.minchat.common.request.TokenValidateRequest
import io.minchat.server.databases.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class RootModule : AbstractMinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.Root.version) {
				call.respond(MINCHAT_VERSION)
			}

			post(Route.Root.tokenValidate) {
				val data = call.receive<TokenValidateRequest>()

				val valid = transaction {
					Users
						.slice(Users.username, Users.token, Users.isDeleted)
						.select({
							(Users.username eq data.username) and
							(Users.token eq data.token) and
							(Users.isDeleted eq false)
						})
						.empty().not()
				}

				if (valid) {
					call.respond(HttpStatusCode.OK)
				} else {
					call.respond(HttpStatusCode.Forbidden)
				}
			}
		}
	}
}
