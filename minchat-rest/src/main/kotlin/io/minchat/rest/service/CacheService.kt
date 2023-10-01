package io.minchat.rest.service

import io.minchat.common.entity.*
import io.minchat.rest.MinchatRestClient
import io.minchat.rest.entity.*

/**
 * Allows fetching MinChat entities using a caching strategy.
 */
class CacheService(
	baseUrl: String,
	val minchatClient: MinchatRestClient
) : RestService(baseUrl, minchatClient.httpClient) {
	/** Up to how many entities of each type can be cached. */
	var maxCacheSize = 100

	val userCache = mutableMapOf<Long, User?>()
	val messageCache = mutableMapOf<Long, Message?>()
	val channelCache = mutableMapOf<Long, Channel?>()

	/**
	 * Adds the entity to the cache, possibly overriding the previous version.
	 * If it's not of a supported type, throws [EntityNotSupportedException].
	 */
	fun set(entity: Any) {
		when {
			entity is Message -> messageCache[entity.id] = entity
			entity is User -> userCache[entity.id] = entity
			entity is Channel -> channelCache[entity.id] = entity

			entity is MinchatMessage -> messageCache[entity.id] = entity.data
			entity is MinchatUser -> userCache[entity.id] = entity.data
			entity is MinchatChannel -> channelCache[entity.id] = entity.data

			else -> throw EntityNotSupportedException(entity::class.java)
		}
	}

	/**
	 * Gets an entity from the cache, potentially transforming it to the desired type.
	 *
	 * throws [EntityNotFoundException] if it's not cached and [restFallback] is false,
	 * or if rest returns null while searching for the entity.
	 */
	inline suspend fun <reified T> get(id: Long, restFallback: Boolean = true): T
		= getOrNull<T>(id, restFallback) ?: throw EntityNotFoundException(T::class.java.simpleName, id)

	/**
	 * Gets an entity from the cache, potentially transforming it to the desired type.
	 *
	 * returns null if it's not cached and [restFallback] is false,
	 * or if rest returns null while searching for the entity.
	 */
	inline suspend fun <reified T> getOrNull(id: Long, restFallback: Boolean = true): T? {
		var data: Any? = when (T::class) {
			Message::class, MinchatMessage::class -> messageCache[id]
			User::class, MinchatUser::class -> userCache[id]
			Channel::class, MinchatChannel::class -> channelCache[id]
			else -> throw EntityNotSupportedException(T::class.java)
		}

		if (data == null) {
			if (restFallback) {
				data = when (T::class) {
					Message::class, MinchatMessage::class -> minchatClient.getMessageOrNull(id)
					User::class, MinchatUser::class -> minchatClient.getUserOrNull(id)
					Channel::class, MinchatChannel::class -> minchatClient.getChannelOrNull(id)
					else -> error("guh")
				}
			} else {
				return null
			}
		}

		return if (data == null) {
			null
		} else if (!MinchatEntity::class.java.isAssignableFrom(T::class.java)) {
			// T is a common entity type such as io.minchat.common.entity.Message
			data as T
		} else {
			// T is a rest entity type such as io.minchat.rest.entity.MinchatMessage
			when (data) {
				is User -> data.withClient(minchatClient) as T
				is Message -> data.withClient(minchatClient) as T
				is Channel -> data.withClient(minchatClient) as T
				else -> error("guh")
			}
		}
	}

	class EntityNotFoundException(val name: String, val id: Long)
		: Exception("Entity \"$name\" #$id does not exist.")

	class EntityNotSupportedException(val cls: Class<*>)
		: Exception("Entity of $cls is not supported by this CacheService.")
}
