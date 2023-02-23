package io.minchat.server.modules

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.minchat.server.databases.Users
import io.minchat.common.Constants
import io.minchat.common.Route
import io.minchat.common.request.*
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.math.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

class AuthModule : MinchatServerModule() {
	override fun Application.onLoad() {
		routing {
			post(Route.Auth.login) {
				val data = call.receive<UserLoginRequest>()

				val response = transaction {
					val userRow = Users
						.select { Users.username eq data.username }
						.find {
							// todo: this is a potential vulnerability.
							// for now, I have to prevent users from having identical namws
							// later, I should allow them but require email or other kind of auth
							BCrypt.checkpw(data.passwordHash, it[Users.passwordHash])
						}
						?: run {
							call.response.status(HttpStatusCode(401,
								"Incorrect login or password"))
							return@transaction null
						}
					val user = Users.createEntity(userRow)

					Users.update({ Users.id eq user.id }) {
						it[Users.lastLoginTimestamp] = System.currentTimeMillis()
					}

					UserLoginRequest.Response(
						user = user,
						token = userRow[Users.token]
					)
				} ?: return@post

				call.respond(response)
			}

			post(Route.Auth.register) {
				val data = call.receive<UserRegisterRequest>()

				val complexity = Constants.hashComplexityPre
				if (data.passwordHash.startsWith("\$2a$complexity")) {
					call.response.status(HttpStatusCode(400, 
						"The password must be hashed with BCrypt and use a complexity of $complexity."))
					return@post
				}


				val response = transaction {
					// ensure the name is vacant
					if (Users.select { Users.username eq data.username }.empty()) {
						call.response.status(HttpStatusCode(401,
							"This username is already taken. Accounts with identical namss are not yet supported."))
						return@transaction null
					}

					val salt = BCrypt.gensalt(13)
					val hashedHash = BCrypt.hashpw(data.passwordHash, salt)

					val userToken = MessageDigest.getInstance("SHA-256")
						.digest((hashedHash + data.username).toByteArray())
						.let { BigInteger(1, it) }
						.toString(32)
						.padStart(256 / 5 + 1, '0')

					val userRow = Users.insert {
						it[username] = data.username
						it[Users.passwordHash] = hashedHash
						it[token] = userToken
						
						it[discriminator] = (System.nanoTime() xor 0xAAAA).toInt() % 10000

						it[creationTimestamp] = System.currentTimeMillis()
						it[lastLoginTimestamp] = System.currentTimeMillis()
					}.resultedValues!!.first()

					UserRegisterRequest.Response(userToken, Users.createEntity(userRow))
				} ?: return@post
				
				call.respond(response)
			}

			get("test") {
				call.respond(io.minchat.common.entity.User(1, "heis", 2829, false, 922992, 393939))
			}
		}
	}
}
