package io.minchat.rest.event

import io.minchat.common.event.*
import io.minchat.rest.*

/** A base class for all REST-level events. */
class MinchatEvent<T : Event>(
	val data: Event,
	val client: MinchatRestClient
) {
	override fun toString() =
		"${this.class.simpleName}(data=$data)"
}
