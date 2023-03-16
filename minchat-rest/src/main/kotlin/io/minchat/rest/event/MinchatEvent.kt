package io.minchat.rest.event

import io.minchat.common.event.Event
import io.minchat.rest.MinchatRestClient

/** A base class for all REST-level events. */
abstract class MinchatEvent<T : Event>(
	val data: Event,
	val client: MinchatRestClient
) {
	override fun toString() =
		"${this::class.simpleName}(data=$data)"
}
