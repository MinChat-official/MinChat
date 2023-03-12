package io.minchat.common.entity

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
	val id: Long,
	val name: String,
	val description: String,
) {
	companion object {
		val nameLength = 3..64
		val descriptionLength = 0..512
	}
}
