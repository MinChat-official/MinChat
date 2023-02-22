package io.minchat.server

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.*

object Users : LongIdTable() {
	val username = varchar("name", 64)
	val passwordHash = varchar("password", 184 / 8)

	val discriminator = integer("discriminator")
	val lastIpHash = varchar("last-ip", 256 / 8).nullable().default(null)

	val lastMessageTimestamp = long("last-sent").default(0L)
	val creationTimestamp = long("created-at")
}
