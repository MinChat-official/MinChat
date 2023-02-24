package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.minchat.common.*
import io.minchat.common.Route
import io.minchat.common.request.*
import io.minchat.server.databases.Users
import io.minchat.server.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.experimental.*

class UserModule : MinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			get(Route.User.fetch) {
				val id = call.parameters.getOrFail<Long>("id")

				newSuspendedTransaction {
					call.respond(Users.getById(id))
				}
			}
			post(Route.User.edit) {
				val id = call.parameters.getOrFail<Long>("id")
				val data = call.receive<UserModifyRequest>()
				val token = call.token()

				newSuspendedTransaction {
					Users.update(opWithAdminAccess(Users.isAdminToken(token),
						common = { Users.token eq token },
						userOnly = { Users.id eq id }
					)) { row ->
						// todo: support more fields
						data.newUsername?.let { row[Users.username] = it }
					}.throwIfNotFound { "user with the providen id-token pair does not exist." }

					call.respond(Users.getById(id))
				}

				Log.info { "User $id was modified." }
			}
			post(Route.User.delete) {
				val id = call.parameters.getOrFail<Long>("id")
				call.receive<UserDeleteRequest>() // unused
				val token = call.token()

				transaction {
					Users.update(opWithAdminAccess(Users.isAdminToken(token),
						common = { Users.token eq token },
						userOnly = { Users.id eq id }
					)) {
						it[Users.username] = Constants.deletedAccountName
						it[Users.token] = "" // token() will fail if an empty string is providen
						it[Users.passwordHash] = Constants.deletedAccountPasswordHash

						it[Users.discriminator] = 0
					}.throwIfNotFound { "user with the providen id-token pair does not exist." }
				}
				call.response.statusOk()

				Log.info { "User $id was deleted." }
			}
		}
	}
}
