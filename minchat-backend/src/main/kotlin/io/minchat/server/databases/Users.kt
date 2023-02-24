package io.minchat.server.databases

import io.minchat.common.entity.*
import io.minchat.server.util.notFound
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.*

object Users : MinchatEntityTable<User>() {
	val username = varchar("name", 64)
	val passwordHash = varchar("password", 80)
	val token = varchar("token", 64)
	val isAdmin = bool("admin").default(false)

	val discriminator = integer("discriminator")
	/**
	 * Unused: unreliable and hard to manage.
	 * Will (or will not) be used in the future to prevent users from creating too many accounts.
	 *
	 * If you're reading this source code as a user, no. We do not store your ip.
	 * And we will never store it in the raw form.
	 */
	val lastIpHash = varchar("last-ip", 256 / 8).nullable().default(null)

	val lastMessageTimestamp = long("last-sent").default(0L)
	val creationTimestamp = long("created-at")
	val lastLoginTimestamp = long("last-login")

	override fun createEntity(row: ResultRow) =
		User(
			id = row[id].value,
			username = row[username],
			discriminator = row[discriminator],
			isAdmin = row[isAdmin],

			lastMessageTimestamp = row[lastMessageTimestamp],
			creationTimestamp = row[creationTimestamp]
		)
	
	fun getByTokenOrNull(token: String) =
		select { Users.token eq token }.firstOrNull()?.let(::createEntity)
	
	fun getByToken(token: String) =
		getByTokenOrNull(token) ?: notFound("the providen token is not associated with any user.")
}
