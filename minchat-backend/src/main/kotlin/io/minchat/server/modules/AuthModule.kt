package io.minchat.server.modules

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.minchat.common.*
import io.minchat.common.Route
import io.minchat.common.entity.User
import io.minchat.common.request.*
import io.minchat.server.databases.Users
import io.minchat.server.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

class AuthModule : MinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			post(Route.Auth.login) {
				val data = call.receive<UserLoginRequest>()

				val response = transaction {
					val userRow = Users
						.select { Users.username.lowerCase() eq data.username.lowercase() }
						.find {
							// There should be only 1 matching user... Normally.
							BCrypt.checkpw(data.passwordHash, it[Users.passwordHash])
						} 
						?: accessDenied("Incorrect login or password")

					val user = Users.createEntity(userRow)

					Users.update({ Users.id eq user.id }) {
						it[Users.lastLoginTimestamp] = System.currentTimeMillis()
					}

					UserLoginRequest.Response(
						user = user,
						token = userRow[Users.token]
					)
				}

				call.respond(response)
			}

			post(Route.Auth.register) {
				val data = call.receive<UserRegisterRequest>()
				val name = data.username.nameConvention()
				val nickname = data.nickname?.nameConvention()

				name.requireLength(User.nameLength) {
					"Username length must be in the range of ${User.nameLength} characters!" 
				}
				nickname?.requireLength(User.nameLength) {
					"Nickname length must be in the range of ${User.nameLength} characters!"
				}

				val complexity = Constants.hashComplexityPre
				if (data.passwordHash.startsWith("\$2a\$$complexity\$").not()) {
					illegalInput("The password must be hashed with BCrypt and use a complexity of $complexity.")
				}

				newSuspendedTransaction {
					// ensure the name is vacant
					if (Users.select { Users.username.lowerCase() eq name.lowercase() }.empty().not()) {
						illegalInput("This username is already taken. Create a unique username; you will use it to log in later.")
					}
					
					val userRow = Users.register(name, nickname, data.passwordHash, User.RoleBitSet.REGULAR_USER)

					UserRegisterRequest.Response(
						token = userRow[Users.token],
						user = Users.createEntity(userRow)
					).let {	
						Log.info { "A new user has been registered: ${it.user.tag}" }
						call.respond(it)
					}
				}
			}
		}
	}
}
