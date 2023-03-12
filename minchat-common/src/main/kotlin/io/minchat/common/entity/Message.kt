package io.minchat.common.entity

import kotlinx.serialization.Serializable

@Serializable
data class Message(
	val id: Long,

	val content: String,
	val author: User,
	val channel: Channel,

	val timestamp: Long
) {
	companion object {
		val contentLength = 1..1024
	}
}
