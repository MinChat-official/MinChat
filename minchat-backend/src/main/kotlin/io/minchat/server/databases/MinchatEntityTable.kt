package io.minchat.server.databases

import io.minchat.server.util.notFound
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.dao.id.*

abstract class MinchatEntityTable<T> : LongIdTable() {
	/** Returns a raw entity roq with the specified id, or throws an exception if it doesn't exist. */
	fun getRawById(id: Long): ResultRow =
		getRawByIdOrNull(id) ?: notFound("Entity with id $id was not found.")

	/** Returns a raw entity roq with the specified id, or null if it doesn't exist. */
	open fun getRawByIdOrNull(id: Long): ResultRow? = run {
		val idColumnn = this.id
		select { idColumnn eq id }.firstOrNull()
	}

	/**
	 * Returns an entity object that represents the entity with the specified id.
	 * Throws an exception if it doesn't exist.
	 */
	fun getById(id: Long) =
		createEntity(getRawById(id))
	
	/**
	 * Returns an entity object that represents the entity with the specified id.
	 * Returns null if it doesn't exist.
	 */
	fun getByIdOrNull(id: Long) =
		getRawByIdOrNull(id)?.let { createEntity(it) }
	
	/**
	 * Creates an entity object from this result row.
	 * Throws an exception if this result row does not represent an entity of type [T].
	 */
	abstract fun createEntity(row: ResultRow): T
}
