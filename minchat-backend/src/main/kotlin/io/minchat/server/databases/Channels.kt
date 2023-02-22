package io.minchat.server

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.*

object Channels  : LongIdTable() {
	val name = varchar("name", 64)
	val description = varchar("description", 512)
}
