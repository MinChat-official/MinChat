package io.minchat.common.entity

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
	val id: Long,
	val name: String,
	val discriminator: Int,

	val lastMessageTimestamp: Long,
	val creationTimestamp: Long
)
