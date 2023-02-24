package io.minchat.server.modules

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.minchat.common.Constants
import io.minchat.common.Route
import io.minchat.common.request.*
import io.minchat.server.databases.Users
import io.minchat.server.util.*
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

				data.username.requireLength(3..40) { "Username length must be in the range of 3..40 characters!" }

				val complexity = Constants.hashComplexityPre
				if (data.passwordHash.startsWith("\$2a\$$complexity\$").not()) {
					illegalInput("The password must be hashed with BCrypt and use a complexity of $complexity.")
				}


				val response = transaction {
					// ensure the name is vacant
					if (Users.select { Users.username eq data.username }.empty().not()) {
						illegalInput("This username is already taken. Accounts with identical namss are not yet supported.")
					}

					val salt = BCrypt.gensalt(13)
					val hashedHash = BCrypt.hashpw(data.passwordHash, salt)

					
					lateinit var userToken: String

					// generate a token; repeat if the token is already used (this should never happen normally)
					do {
						val input = hashedHash + data.username + System.nanoTime() + System.currentTimeMillis()
						userToken = MessageDigest.getInstance("SHA-256")
							.digest(input.toByteArray())
							.let { BigInteger(1, it) }
							.toString(32)
							.padStart(256 / 5 + 1, '0')
					} while (Users.select { Users.token eq userToken }.empty().not())

					// create a new user and get the created row
					val userRow = Users.insert {
						it[username] = data.username
						it[Users.passwordHash] = hashedHash
						it[token] = userToken
						
						it[discriminator] = abs((System.nanoTime() xor 0xAAAA).toInt() % 10000)

						it[creationTimestamp] = System.currentTimeMillis()
						it[lastLoginTimestamp] = System.currentTimeMillis()
					}.resultedValues!!.first()

					UserRegisterRequest.Response(userToken, Users.createEntity(userRow))
				}
				
				Log.info { "A new user has been registered: ${response.user.tag}" }

				call.respond(response)
			}
		}
	}
}
