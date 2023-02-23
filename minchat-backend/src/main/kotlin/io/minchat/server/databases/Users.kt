package io.minchat.server.databases

import io.minchat.common.entity.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.*

object Users : MinchatEntityTable<User>() {
	val username = varchar("name", 64)
	val passwordHash = varchar("password", 184 / 8)
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
			id = row[Users.id].value,
			username = row[Users.username],
			discriminator = row[Users.discriminator],
			isAdmin = row[Users.isAdmin],

			lastMessageTimestamp = row[Users.lastMessageTimestamp],
			creationTimestamp = row[Users.creationTimestamp]
		)
}
