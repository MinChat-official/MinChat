package io.minchat.server

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.*

object Messages : LongIdTable() {
	val content = varchar("content", 1024)
	val channel = reference("channel", Channels)
	val author = reference("author", Users)

	val timestamp = long("timestamp")
}
