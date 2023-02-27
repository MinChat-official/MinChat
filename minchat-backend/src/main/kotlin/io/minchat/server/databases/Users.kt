package io.minchat.server.databases

import io.minchat.common.entity.*
import io.minchat.server.util.notFound
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.math.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.*
import org.mindrot.jbcrypt.BCrypt

object Users : MinchatEntityTable<User>() {
	val username = varchar("name", 64)
	val passwordHash = varchar("password", 80)
	val token = varchar("token", 64)
	val isAdmin = bool("admin").default(false)

	val discriminator = integer("discriminator")

	val isBanned = bool("banned").default(false)
	val isDeleted = bool("deleted").default(false)
	/**
	 * Unused: unreliable and hard to manage.
	 * Will (or will not) be used in the future to prevent users from creating too many accounts.
	 *
	 * If you're reading this source code as a user, no. We do not store your ip.
	 * And we will never store it in the raw form.
	 */
	val lastIpHash = varchar("last-ip", 256 / 8).nullable().default(null)

	val messageCount = integer("message-count").default(0)
	val lastMessageTimestamp = long("last-sent").default(0L)

	val creationTimestamp = long("created-at")
	val lastLoginTimestamp = long("last-login")

	override fun getRawByIdOrNull(id: Long) =
		super.getRawByIdOrNull(id)?.takeIf { !it[isDeleted] }

	override fun createEntity(row: ResultRow) =
		User(
			id = row[id].value,
			username = row[username],
			discriminator = row[discriminator],
			isAdmin = row[isAdmin],

			isBanned = row[isBanned],

			messageCount = row[messageCount],
			lastMessageTimestamp = row[lastMessageTimestamp],

			creationTimestamp = row[creationTimestamp]
		)
	
	
	fun getRawByTokenOrNull(token: String) =
		select { Users.token eq token }.firstOrNull()

	fun getRawByToken(token: String) =
		getRawByTokenOrNull(token) ?: notFound("the providen token is not associated with any user.")
	
	fun getByTokenOrNull(token: String) =
		getRawByTokenOrNull(token)?.let(::createEntity)
	
	fun getByToken(token: String) =
		getByTokenOrNull(token) ?: notFound("the providen token is not associated with any user.")
	
	/** Returns true if the user with the providen token is an admin; false otherwise. */
	fun isAdminToken(token: String) =
		select { Users.token eq token }.firstOrNull()?.get(isAdmin) ?: false

	fun register(name: String, passwordHash: String, admin: Boolean): ResultRow {
		val salt = BCrypt.gensalt(13)
		val hashedHash = BCrypt.hashpw(passwordHash, salt)
		
		lateinit var userToken: String

		// generate a token; repeat if the token is already used (this should never happen normally)
		var attempt = 0
		do {
			val input = hashedHash + attempt + name + System.nanoTime() + System.currentTimeMillis()
			userToken = MessageDigest.getInstance("SHA-256")
				.digest(input.toByteArray())
				.let { BigInteger(1, it) }
				.toString(32)
				.padStart(256 / 5 + 1, '0')

			++attempt
		} while (select { Users.token eq userToken }.empty().not())

		// create a new user and get the created row
		val userRow = Users.insert {
			it[username] = name
			it[Users.passwordHash] = hashedHash
			it[token] = userToken
			
			it[discriminator] = abs((System.nanoTime() xor 0xAAAA).toInt() % 10000)
			it[isAdmin] = admin

			it[creationTimestamp] = System.currentTimeMillis()
			it[lastLoginTimestamp] = System.currentTimeMillis()
		}.resultedValues!!.first()

		return userRow
	}
}
