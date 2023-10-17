package io.minchat.rest.entity

import io.minchat.rest.MinchatRestClient

abstract class AbstractMinchatEntity<T : AbstractMinchatEntity<T>> {
	abstract val id: Long
	abstract val rest: MinchatRestClient

	/** Fetches this entity from the rest API. Doesn't affect this object. */
	abstract suspend fun fetch(): T
}
