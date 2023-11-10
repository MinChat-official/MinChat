package io.minchat.server.databases

import io.minchat.server.util.notFound
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*

abstract class AbstractMinchatEntityTable<T> : LongIdTable() {
	open val entityName by lazy { this::class.simpleName!!.removeSuffix("s") }

	val isDeleted = bool("deleted").default(false)

	/** Select statement that ignores deleted entities. Should be used instead of select in most cases. */
	inline fun safeSelect(builder: SqlExpressionBuilder.() -> Op<Boolean>) =
		select {
			builder() and (isDeleted eq false)
		}

	/** Returns a raw entity roq with the specified id, or throws an exception if it doesn't exist. */
	fun getRawById(id: Long): ResultRow =
		getRawByIdOrNull(id) ?: notFound("$entityName with id $id was not found.")

	/** Returns a raw entity row with the specified id, or null if it doesn't exist. */
	open fun getRawByIdOrNull(id: Long): ResultRow? = run {
		val idColumnn = this.id
		safeSelect { idColumnn eq id }.firstOrNull()
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
